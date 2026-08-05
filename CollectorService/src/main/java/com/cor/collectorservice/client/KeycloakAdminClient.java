package com.cor.collectorservice.client;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.exception.KeycloakConfigurationException;
import com.cor.collectorservice.util.exception.KeycloakException;
import com.cor.collectorservice.util.exception.KeycloakOperationException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import com.cor.collectorservice.util.exception.KeycloakUserCreationException;
import com.cor.collectorservice.util.exception.KeycloakUserDeletionException;
import com.cor.collectorservice.util.exception.KeycloakUserUpdateException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Клиент Admin REST API Keycloak: создание, обновление и удаление учётных записей пользователей.
 * <p>
 * Вынесен в отдельный бин по двум причинам:
 * <ul>
 *     <li>здесь сосредоточен перевод ошибок Keycloak в доменные исключения сервиса;</li>
 *     <li>аннотация {@link Retry} работает через прокси, поэтому повторные попытки
 *         срабатывают только при вызове из другого бина (например, из
 *         {@link com.cor.collectorservice.service.AuthService}).</li>
 * </ul>
 * Повторяются только вызовы, завершившиеся {@link KeycloakUnavailableException},
 * то есть временные сетевые ошибки и ответы 5xx. Ошибки уровня бизнес-логики
 * (например, занятый логин) не повторяются.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    /**
     * Имя resilience4j-инстанса ретрая, настроенного в {@code application.yml}.
     */
    public static final String RETRY_NAME = "keycloakRetry";

    private final Keycloak keycloak;
    private final KeycloakProperties properties;

    /**
     * Создаёт пользователя в Keycloak и назначает ему роль по умолчанию.
     * <p>
     * Это первый шаг регистрации: идентификатор, назначенный Keycloak, становится
     * первичным ключом профиля в базе данных сервиса.
     * Если назначить роль не удалось, только что созданная учётная запись удаляется,
     * чтобы не оставлять пользователя без прав.
     *
     * @param username логин пользователя
     * @param email    e-mail пользователя
     * @param password пароль в открытом виде; хранится только в Keycloak
     * @return идентификатор созданной учётной записи Keycloak
     * @throws UserAlreadyExistsException     если логин или e-mail уже заняты (ответ 409)
     * @throws KeycloakUserCreationException  если Keycloak отказал в создании по другой причине
     * @throws KeycloakUnavailableException   если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public UUID createUser(String username, String email, String password) {
        log.info("Создание пользователя в Keycloak: логин={}, realm={}", username, properties.getRealm());

        UserRepresentation representation = buildUserRepresentation(username, email, password);

        try (Response response = users().create(representation)) {
            int status = response.getStatus();
            log.debug("Ответ Keycloak на создание пользователя {}: статус={}", username, status);

            if (status == HttpStatus.CREATED.value()) {
                UUID userId = extractCreatedId(response, username);
                log.info("Пользователь {} создан в Keycloak с идентификатором {}", username, userId);
                assignDefaultRoleOrRollback(userId, username);
                return userId;
            }
            if (status == HttpStatus.CONFLICT.value()) {
                log.warn("Keycloak отклонил создание пользователя {}: логин или e-mail уже заняты", username);
                throw new UserAlreadyExistsException(username);
            }
            if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                log.error("Keycloak вернул ошибку сервера при создании пользователя {}: статус={}", username, status);
                throw new KeycloakUnavailableException(
                        "Keycloak вернул статус " + status + " при создании пользователя " + username);
            }
            log.error("Keycloak отказал в создании пользователя {}: статус={}", username, status);
            throw new KeycloakUserCreationException(
                    "Keycloak отказал в создании пользователя " + username + ", статус ответа: " + status);
        } catch (WebApplicationException ex) {
            // Сюда попадают отказы самого Admin API, например неудачная авторизация сервисной учётной записи
            throw translateAdminFailure("создании пользователя " + username, ex);
        } catch (ProcessingException ex) {
            log.error("Не удалось обратиться к Keycloak при создании пользователя {}", username, ex);
            throw new KeycloakUnavailableException(
                    "Keycloak недоступен при создании пользователя " + username, ex);
        }
    }

    /**
     * Удаляет учётную запись пользователя из Keycloak.
     * <p>
     * Используется как компенсирующая операция, если профиль не удалось сохранить в базе сервиса.
     * Отсутствие пользователя (ответ 404) считается успехом: цель операции — отсутствие записи.
     *
     * @param userId идентификатор учётной записи Keycloak
     * @throws KeycloakUserDeletionException если Keycloak отказал в удалении
     * @throws KeycloakUnavailableException  если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public void deleteUser(UUID userId) {
        log.info("Удаление пользователя {} из Keycloak", userId);

        try (Response response = users().delete(userId.toString())) {
            int status = response.getStatus();

            if (status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.OK.value()) {
                log.info("Пользователь {} удалён из Keycloak", userId);
                return;
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                log.warn("Пользователь {} в Keycloak не найден — считаем удаление выполненным", userId);
                return;
            }
            if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
                log.error("Keycloak вернул ошибку сервера при удалении пользователя {}: статус={}", userId, status);
                throw new KeycloakUnavailableException(
                        "Keycloak вернул статус " + status + " при удалении пользователя " + userId);
            }
            log.error("Keycloak отказал в удалении пользователя {}: статус={}", userId, status);
            throw new KeycloakUserDeletionException(
                    "Keycloak отказал в удалении пользователя " + userId + ", статус ответа: " + status);
        } catch (jakarta.ws.rs.NotFoundException ex) {
            log.warn("Пользователь {} в Keycloak не найден — считаем удаление выполненным", userId);
        } catch (WebApplicationException ex) {
            throw translateAdminFailure("удалении пользователя " + userId, ex);
        } catch (ProcessingException ex) {
            log.error("Не удалось обратиться к Keycloak при удалении пользователя {}", userId, ex);
            throw new KeycloakUnavailableException("Keycloak недоступен при удалении пользователя " + userId, ex);
        }
    }

    /**
     * Обновляет логин и e-mail учётной записи в Keycloak,
     * поддерживая профиль сервиса и данные провайдера идентификации в согласованном состоянии.
     *
     * @param userId   идентификатор учётной записи Keycloak
     * @param username новый логин или {@code null}, если логин не меняется
     * @param email    новый e-mail или {@code null}, если e-mail не меняется
     * @throws UserAlreadyExistsException   если новый логин или e-mail уже заняты
     * @throws KeycloakUserUpdateException  если Keycloak отказал в обновлении
     * @throws KeycloakUnavailableException если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public void updateUser(UUID userId, String username, String email) {
        log.info("Обновление данных пользователя {} в Keycloak: логин={}, e-mail={}", userId, username, email);

        try {
            UserRepresentation representation = users().get(userId.toString()).toRepresentation();
            if (StringUtils.hasText(username)) {
                representation.setUsername(username);
            }
            if (StringUtils.hasText(email)) {
                representation.setEmail(email);
            }
            users().get(userId.toString()).update(representation);
            log.info("Данные пользователя {} в Keycloak обновлены", userId);
        } catch (jakarta.ws.rs.NotFoundException ex) {
            log.error("Пользователь {} не найден в Keycloak при обновлении данных", userId, ex);
            throw new KeycloakUserUpdateException("Пользователь не найден в Keycloak: " + userId, ex);
        } catch (WebApplicationException ex) {
            throw translateUpdateFailure(userId, username, ex);
        } catch (ProcessingException ex) {
            log.error("Не удалось обратиться к Keycloak при обновлении пользователя {}", userId, ex);
            throw new KeycloakUnavailableException("Keycloak недоступен при обновлении пользователя " + userId, ex);
        }
    }

    /**
     * Заменяет пароль учётной записи в Keycloak.
     *
     * @param userId   идентификатор учётной записи Keycloak
     * @param password новый пароль в открытом виде
     * @throws KeycloakUserUpdateException  если Keycloak отказал в смене пароля
     * @throws KeycloakUnavailableException если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public void updatePassword(UUID userId, String password) {
        log.info("Смена пароля пользователя {} в Keycloak", userId);

        try {
            users().get(userId.toString()).resetPassword(buildPassword(password));
            log.info("Пароль пользователя {} в Keycloak изменён", userId);
        } catch (jakarta.ws.rs.NotFoundException ex) {
            log.error("Пользователь {} не найден в Keycloak при смене пароля", userId, ex);
            throw new KeycloakUserUpdateException("Пользователь не найден в Keycloak: " + userId, ex);
        } catch (WebApplicationException ex) {
            throw translateUpdateFailure(userId, null, ex);
        } catch (ProcessingException ex) {
            log.error("Не удалось обратиться к Keycloak при смене пароля пользователя {}", userId, ex);
            throw new KeycloakUnavailableException("Keycloak недоступен при смене пароля пользователя " + userId, ex);
        }
    }

    /**
     * Назначает пользователю realm-роль по умолчанию, а при неудаче удаляет созданную учётную запись.
     *
     * @param userId   идентификатор созданной учётной записи
     * @param username логин пользователя (для логов и текста ошибки)
     * @throws KeycloakUserCreationException если роль назначить не удалось
     */
    private void assignDefaultRoleOrRollback(UUID userId, String username) {
        String role = properties.getDefaultRole();
        try {
            RoleRepresentation roleRepresentation = realm().roles().get(role).toRepresentation();
            users().get(userId.toString()).roles().realmLevel().add(List.of(roleRepresentation));
            log.info("Пользователю {} назначена realm-роль {}", username, role);
        } catch (WebApplicationException | ProcessingException ex) {
            log.error("Не удалось назначить роль {} пользователю {}. Удаляем созданную учётную запись {}",
                    role, username, userId, ex);
            deleteQuietly(userId);
            throw new KeycloakUserCreationException(
                    "Не удалось назначить роль " + role + " пользователю " + username, ex);
        }
    }

    /**
     * Удаляет пользователя, подавляя любые ошибки: используется во внутренних откатах,
     * где основная ошибка уже определена и не должна подменяться ошибкой удаления.
     *
     * @param userId идентификатор учётной записи Keycloak
     */
    private void deleteQuietly(UUID userId) {
        try {
            users().delete(userId.toString()).close();
        } catch (RuntimeException ex) {
            log.error("Не удалось удалить учётную запись {} при откате создания пользователя", userId, ex);
        }
    }

    /**
     * Собирает представление нового пользователя: включённая учётная запись с постоянным паролем.
     *
     * @param username логин
     * @param email    e-mail
     * @param password пароль в открытом виде
     * @return представление пользователя для Admin API
     */
    private UserRepresentation buildUserRepresentation(String username, String email, String password) {
        UserRepresentation representation = new UserRepresentation();
        representation.setUsername(username);
        representation.setEmail(email);
        representation.setEnabled(true);
        representation.setEmailVerified(false);
        representation.setCredentials(List.of(buildPassword(password)));
        return representation;
    }

    /**
     * Создаёт представление постоянного пароля (без требования смены при первом входе).
     *
     * @param password пароль в открытом виде
     * @return представление учётных данных для Admin API
     */
    private CredentialRepresentation buildPassword(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    /**
     * Извлекает идентификатор созданного пользователя из заголовка {@code Location}.
     *
     * @param response ответ Keycloak со статусом 201
     * @param username логин пользователя (для текста ошибки)
     * @return идентификатор созданной учётной записи
     * @throws KeycloakUserCreationException если ответ не содержит корректного идентификатора
     */
    private UUID extractCreatedId(Response response, String username) {
        String location = response.getLocation() == null ? null : response.getLocation().getPath();
        if (location == null || !location.contains("/")) {
            log.error("Keycloak не вернул адрес созданного пользователя {}: Location={}", username, location);
            throw new KeycloakUserCreationException(
                    "Keycloak не вернул идентификатор созданного пользователя " + username);
        }
        String rawId = location.substring(location.lastIndexOf('/') + 1);
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            log.error("Keycloak вернул некорректный идентификатор пользователя {}: {}", username, rawId, ex);
            throw new KeycloakUserCreationException(
                    "Keycloak вернул некорректный идентификатор пользователя " + username + ": " + rawId, ex);
        }
    }

    /**
     * Переводит ошибку Admin API, возникшую при обновлении, в доменное исключение.
     *
     * @param userId   идентификатор учётной записи
     * @param username логин пользователя, если он менялся
     * @param ex       исходная ошибка Admin API
     * @return исключение, которое следует выбросить вызывающему коду
     */
    private RuntimeException translateUpdateFailure(UUID userId, String username, WebApplicationException ex) {
        int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();

        if (status == HttpStatus.CONFLICT.value()) {
            log.warn("Keycloak отклонил обновление пользователя {}: логин или e-mail заняты", userId);
            return new UserAlreadyExistsException(username == null ? userId.toString() : username);
        }
        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Keycloak вернул ошибку сервера при обновлении пользователя {}: статус={}", userId, status, ex);
            return new KeycloakUnavailableException(
                    "Keycloak вернул статус " + status + " при обновлении пользователя " + userId, ex);
        }
        log.error("Keycloak отказал в обновлении пользователя {}: статус={}", userId, status, ex);
        return new KeycloakUserUpdateException(
                "Keycloak отказал в обновлении пользователя " + userId + ", статус ответа: " + status, ex);
    }

    /**
     * Переводит отказ Admin API в доменное исключение, чтобы наружу не просачивались
     * исключения JAX-RS.
     *
     * @param operation описание операции в предложном падеже, например «удалении пользователя 42»
     * @param ex        исходная ошибка Admin API
     * @return исключение, которое следует выбросить вызывающему коду
     */
    private KeycloakException translateAdminFailure(String operation, WebApplicationException ex) {
        int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();

        if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Keycloak вернул ошибку сервера при {}: статус={}", operation, status, ex);
            return new KeycloakUnavailableException("Keycloak вернул статус " + status + " при " + operation, ex);
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            log.error("Сервисной учётной записи Keycloak не хватает прав при {}: статус={}", operation, status, ex);
            return new KeycloakConfigurationException(
                    "Сервисная учётная запись Keycloak не имеет прав на выполнение операции: " + operation, ex);
        }

        log.error("Keycloak отказал при {}: статус={}", operation, status, ex);
        return new KeycloakOperationException(
                "Keycloak отказал при " + operation + ", статус ответа: " + status, ex);
    }

    /**
     * @return ресурс realm-а, в котором живут пользователи сервиса
     */
    private RealmResource realm() {
        return keycloak.realm(properties.getRealm());
    }

    /**
     * @return ресурс управления пользователями realm-а
     */
    private UsersResource users() {
        return realm().users();
    }
}
