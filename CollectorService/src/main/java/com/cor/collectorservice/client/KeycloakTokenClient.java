package com.cor.collectorservice.client;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.dto.auth.TokenResponse;
import com.cor.collectorservice.util.exception.InvalidCredentialsException;
import com.cor.collectorservice.util.exception.InvalidTokenException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Клиент OpenID Connect эндпоинтов Keycloak: выдача, обновление и отзыв токенов.
 * <p>
 * Вход выполняется по схеме Direct Access Grant (grant type {@code password}) от имени
 * конфиденциального клиента {@code keycloak.client-id}. Сервис не хранит пароли:
 * проверку учётных данных полностью выполняет Keycloak.
 */
@Slf4j
@Component
public class KeycloakTokenClient {

    /**
     * Имя resilience4j-инстанса ретрая, настроенного в {@code application.yml}.
     */
    public static final String RETRY_NAME = "keycloakRetry";

    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String REFRESH_TOKEN = "refresh_token";

    private final RestClient restClient;
    private final KeycloakProperties properties;

    /**
     * @param keycloakRestClient HTTP-клиент с базовым адресом сервера Keycloak
     * @param properties         настройки интеграции с Keycloak
     */
    public KeycloakTokenClient(@Qualifier("keycloakRestClient") RestClient keycloakRestClient,
                               KeycloakProperties properties) {
        this.restClient = keycloakRestClient;
        this.properties = properties;
    }

    /**
     * Выдаёт пару токенов по логину и паролю.
     *
     * @param username логин пользователя
     * @param password пароль пользователя
     * @return access- и refresh-токены
     * @throws InvalidCredentialsException  если Keycloak отверг учётные данные или учётная запись отключена
     * @throws KeycloakUnavailableException если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public TokenResponse obtainToken(String username, String password) {
        log.info("Запрос токенов у Keycloak для пользователя {}", username);

        MultiValueMap<String, String> form = baseForm();
        form.add(GRANT_TYPE, "password");
        form.add("username", username);
        form.add("password", password);

        TokenResponse response = post(properties.getTokenUri(), form, "выдаче токенов пользователю " + username);
        log.info("Keycloak выдал токены пользователю {} (срок жизни access-токена: {} с)",
                username, response.expiresIn());
        return response;
    }

    /**
     * Обновляет пару токенов по refresh-токену.
     *
     * @param refreshToken действующий refresh-токен
     * @return новая пара токенов
     * @throws InvalidTokenException        если refresh-токен просрочен или отозван
     * @throws KeycloakUnavailableException если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public TokenResponse refreshToken(String refreshToken) {
        log.info("Обновление пары токенов по refresh-токену");

        MultiValueMap<String, String> form = baseForm();
        form.add(GRANT_TYPE, REFRESH_TOKEN);
        form.add(REFRESH_TOKEN, refreshToken);

        try {
            TokenResponse response = post(properties.getTokenUri(), form, "обновлении токенов");
            log.info("Пара токенов успешно обновлена (срок жизни access-токена: {} с)", response.expiresIn());
            return response;
        } catch (InvalidCredentialsException ex) {
            log.warn("Keycloak отклонил refresh-токен: {}", ex.getMessage());
            throw new InvalidTokenException("Refresh-токен недействителен или просрочен", ex);
        }
    }

    /**
     * Завершает сессию пользователя в Keycloak, отзывая refresh-токен.
     * <p>
     * Уже отозванный или просроченный токен не считается ошибкой: цель операции — отсутствие сессии.
     *
     * @param refreshToken refresh-токен, полученный при входе
     * @throws KeycloakUnavailableException если Keycloak недоступен
     */
    @Retry(name = RETRY_NAME)
    public void logout(String refreshToken) {
        log.info("Завершение сессии пользователя в Keycloak");

        MultiValueMap<String, String> form = baseForm();
        form.add(REFRESH_TOKEN, refreshToken);

        try {
            restClient.post()
                    .uri(properties.getRealmUrl() + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Сессия пользователя в Keycloak завершена");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("Keycloak вернул ошибку сервера при выходе: статус={}", ex.getStatusCode(), ex);
                throw new KeycloakUnavailableException("Keycloak недоступен при завершении сессии", ex);
            }
            log.warn("Keycloak отклонил завершение сессии (статус={}): токен уже недействителен",
                    ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.error("Не удалось обратиться к Keycloak при завершении сессии", ex);
            throw new KeycloakUnavailableException("Keycloak недоступен при завершении сессии", ex);
        }
    }

    /**
     * Выполняет form-запрос к Keycloak и разбирает ответ с токенами.
     *
     * @param uri       адрес эндпоинта
     * @param form      тело запроса
     * @param operation описание операции для сообщений об ошибках
     * @return разобранный ответ с токенами
     * @throws InvalidCredentialsException  при ответе 4xx (неверные учётные данные или токен)
     * @throws KeycloakUnavailableException при ответе 5xx, сетевой ошибке или пустом теле ответа
     */
    private TokenResponse post(String uri, MultiValueMap<String, String> form, String operation) {
        try {
            TokenResponse response = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            if (response == null || response.accessToken() == null) {
                log.error("Keycloak вернул пустой ответ при {}", operation);
                throw new KeycloakUnavailableException("Keycloak вернул пустой ответ при " + operation);
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("Keycloak вернул ошибку сервера при {}: статус={}", operation, ex.getStatusCode(), ex);
                throw new KeycloakUnavailableException(
                        "Keycloak вернул статус " + ex.getStatusCode().value() + " при " + operation, ex);
            }
            log.warn("Keycloak отклонил запрос при {}: статус={}, тело={}",
                    operation, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new InvalidCredentialsException();
        } catch (ResourceAccessException ex) {
            log.error("Не удалось обратиться к Keycloak при {}", operation, ex);
            throw new KeycloakUnavailableException("Keycloak недоступен при " + operation, ex);
        }
    }

    /**
     * Создаёт тело запроса с параметрами конфиденциального клиента.
     *
     * @return форма с {@code client_id} и {@code client_secret}
     */
    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add(CLIENT_ID, properties.getClientId());
        form.add(CLIENT_SECRET, properties.getClientSecret());
        return form;
    }
}
