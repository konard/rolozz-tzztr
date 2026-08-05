package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Keycloak недоступен: сетевая ошибка, таймаут или ответ 5xx от сервера идентификации.
 * <p>
 * Единственное исключение, на которое настроен повтор вызова
 * (resilience4j-инстанс {@code keycloakRetry}), поскольку ошибка считается временной.
 */
public class KeycloakUnavailableException extends KeycloakException {

    /**
     * @param message описание операции, которую не удалось выполнить
     */
    public KeycloakUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * @param message описание операции, которую не удалось выполнить
     * @param cause   исходная сетевая ошибка
     */
    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
