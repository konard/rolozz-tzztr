package com.cor.collectorservice.service;

import com.cor.collectorservice.client.KeycloakAdminClient;
import com.cor.collectorservice.dto.auth.UpdateUserRequest;
import com.cor.collectorservice.dto.auth.UserResponse;
import com.cor.collectorservice.entity.User;
import com.cor.collectorservice.mapper.UserMapper;
import com.cor.collectorservice.repository.UserRepository;
import com.cor.collectorservice.util.EncryptionUtil;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.BadRequestException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import com.cor.collectorservice.util.exception.UserAlreadyExistsException;
import com.cor.collectorservice.util.exception.UserNotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * Работа с профилем текущего пользователя.
 * <p>
 * После перехода на Keycloak сервис не хранит и не проверяет пароли: учётные данные
 * (логин, e-mail, пароль) живут в Keycloak, а в базе данных сервиса остаётся профиль,
 * первичный ключ которого равен идентификатору учётной записи Keycloak.
 * <p>
 * Текущий пользователь определяется не {@code SecurityContextHolder}, а
 * {@link UserContextHolder}: контекст туда кладёт
 * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter} после проверки подписи токена.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    EncryptionUtil encryptionUtil;
    KeycloakAdminClient keycloakAdminClient;

    /**
     * Возвращает профиль текущего пользователя.
     *
     * @return профиль пользователя
     * @throws UnauthorizedAccessException если запрос не аутентифицирован
     * @throws UserNotFoundException       если учётная запись есть в Keycloak, но профиля в базе нет
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        log.debug("Получение данных текущего пользователя");
        User user = getAuthenticatedUser();
        log.info("Данные пользователя получены: {}", user.getUsername());
        return userMapper.toResponse(user);
    }

    /**
     * Обновляет профиль текущего пользователя.
     * <p>
     * Порядок изменений выбран так, чтобы Keycloak и база данных не разошлись:
     * <ol>
     *     <li>профиль сохраняется в базе и сразу сбрасывается в неё ({@code saveAndFlush}),
     *         поэтому нарушение ограничений всплывает здесь, а не при фиксации транзакции;</li>
     *     <li>логин и e-mail переносятся в Keycloak;</li>
     *     <li>пароль меняется только в Keycloak — сервис его не хранит;</li>
     *     <li>если Keycloak отказал, исключение пробрасывается и транзакция базы откатывается,
     *         а уже применённое обновление логина и e-mail отменяется обратным вызовом Keycloak.</li>
     * </ol>
     *
     * @param request поля профиля, которые нужно изменить; достаточно любого одного
     * @return обновлённый профиль пользователя
     * @throws BadRequestException        если не указано ни одного поля
     * @throws UserAlreadyExistsException если новый логин уже занят
     */
    @Transactional
    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        log.info("Начало обновления данных пользователя");
        if (!StringUtils.hasText(request.getUsername()) && !StringUtils.hasText(request.getPassword())
                && !StringUtils.hasText(request.getEmail()) && !StringUtils.hasText(request.getWbToken())) {
            log.warn("Попытка обновления без указания полей");
            throw new BadRequestException("Хотя бы одно поле должно быть указано для обновления");
        }

        User user = getAuthenticatedUser();
        log.debug("Обновление пользователя: {}", user.getUsername());

        String previousUsername = user.getUsername();
        String previousEmail = user.getEmail();

        if (StringUtils.hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                log.warn("Попытка смены имени на уже существующее: {}", request.getUsername());
                throw new UserAlreadyExistsException(request.getUsername());
            }
            log.info("Смена имени пользователя с {} на {}", user.getUsername(), request.getUsername());
            user.setUsername(request.getUsername());
        }

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
            log.info("Смена e-mail пользователя {}", user.getUsername());
            user.setEmail(request.getEmail());
        }

        if (StringUtils.hasText(request.getWbToken())) {
            log.debug("Обновление WB-токена пользователя: {}", user.getUsername());
            user.setWbToken(encryptionUtil.encrypt(request.getWbToken()));
        }

        User updatedUser = userRepository.saveAndFlush(user);
        applyCredentialChanges(updatedUser, request, previousUsername, previousEmail);

        log.info("Данные пользователя успешно обновлены: {}", updatedUser.getUsername());
        return userMapper.toResponse(updatedUser);
    }

    /**
     * Удаляет профиль текущего пользователя и его учётную запись в Keycloak.
     * <p>
     * Профиль удаляется первым и сразу сбрасывается в базу; учётная запись Keycloak удаляется
     * следом, в той же транзакции. Если Keycloak недоступен, исключение пробрасывается,
     * транзакция откатывается и профиль остаётся на месте — удаление можно безопасно повторить.
     * Так исключается «осиротевшая» учётная запись, владелец которой смог бы войти без профиля.
     *
     * @throws UnauthorizedAccessException если запрос не аутентифицирован
     */
    @Transactional
    public void deleteCurrentUser() {
        log.info("Начало удаления текущего пользователя");
        User user = getAuthenticatedUser();
        UUID userId = user.getId();
        String username = user.getUsername();

        log.warn("Удаление пользователя: {} ({})", username, userId);
        userRepository.delete(user);
        userRepository.flush();

        keycloakAdminClient.deleteUser(userId);
        log.info("Пользователь успешно удалён: {} ({})", username, userId);
    }

    /**
     * Возвращает расшифрованный WB-токен текущего пользователя.
     *
     * @return WB-токен в открытом виде или {@code null}, если он не задан
     */
    @Transactional(readOnly = true)
    public String getDecryptedWbToken() {
        log.debug("Получение расшифрованного WB-токена");
        User user = getAuthenticatedUser();
        if (user.getWbToken() == null) {
            log.debug("WB-токен не установлен для пользователя: {}", user.getUsername());
            return null;
        }
        String decryptedToken = encryptionUtil.decrypt(user.getWbToken());
        if (decryptedToken != null && decryptedToken.startsWith("\"") && decryptedToken.endsWith("\"")) {
            decryptedToken = decryptedToken.substring(1, decryptedToken.length() - 1);
        }
        log.info("WB-токен успешно расшифрован для пользователя: {}", user.getUsername());
        return decryptedToken;
    }

    /**
     * Возвращает профиль пользователя, от имени которого выполняется запрос.
     * <p>
     * Поиск идёт по идентификатору из claim {@code sub}, а не по логину: идентификатор
     * неизменен, тогда как логин пользователь может сменить.
     *
     * @return профиль текущего пользователя
     * @throws UnauthorizedAccessException если запрос не аутентифицирован
     * @throws UserNotFoundException       если профиль в базе данных отсутствует
     */
    User getAuthenticatedUser() {
        UserContext context = UserContextHolder.getRequired();

        return userRepository.findById(context.userId())
                .orElseThrow(() -> {
                    log.error("Аутентифицированный пользователь не найден в базе: {} ({})",
                            context.username(), context.userId());
                    return new UserNotFoundException(context.username());
                });
    }

    /**
     * Переносит изменения учётных данных в Keycloak и откатывает их при неудаче.
     *
     * @param user             сохранённый профиль пользователя
     * @param request          запрос на обновление
     * @param previousUsername логин до обновления
     * @param previousEmail    e-mail до обновления
     */
    private void applyCredentialChanges(User user,
                                        UpdateUserRequest request,
                                        String previousUsername,
                                        String previousEmail) {
        boolean credentialsChanged = !user.getUsername().equals(previousUsername)
                || !Objects.equals(user.getEmail(), previousEmail);

        if (credentialsChanged) {
            log.info("Синхронизация логина и e-mail пользователя {} с Keycloak", user.getUsername());
            keycloakAdminClient.updateUser(user.getId(), user.getUsername(), user.getEmail());
        }

        if (!StringUtils.hasText(request.getPassword())) {
            return;
        }

        try {
            log.info("Смена пароля пользователя {} в Keycloak", user.getUsername());
            keycloakAdminClient.updatePassword(user.getId(), request.getPassword());
        } catch (RuntimeException ex) {
            if (credentialsChanged) {
                log.error("Смена пароля пользователя {} не удалась. Возвращаем прежние логин и e-mail в Keycloak",
                        user.getUsername(), ex);
                restoreCredentialsQuietly(user.getId(), previousUsername, previousEmail);
            }
            throw ex;
        }
    }

    /**
     * Возвращает прежние логин и e-mail в Keycloak, подавляя ошибки отката:
     * наружу должна уйти исходная причина сбоя, а не ошибка компенсации.
     *
     * @param userId           идентификатор учётной записи Keycloak
     * @param previousUsername логин до обновления
     * @param previousEmail    e-mail до обновления
     */
    private void restoreCredentialsQuietly(UUID userId, String previousUsername, String previousEmail) {
        try {
            keycloakAdminClient.updateUser(userId, previousUsername, previousEmail);
            log.info("Прежние логин и e-mail учётной записи {} восстановлены", userId);
        } catch (RuntimeException ex) {
            log.error("Не удалось восстановить прежние логин и e-mail учётной записи {}. "
                    + "Данные в Keycloak и в базе данных могли разойтись", userId, ex);
        }
    }
}
