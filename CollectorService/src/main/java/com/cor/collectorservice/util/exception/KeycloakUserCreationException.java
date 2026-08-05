package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Не удалось создать пользователя в Keycloak (Admin API вернул неуспешный статус).
 * <p>
 * Бросается на первом шаге регистрации: если оно возникло, запись в БД сервиса ещё не создавалась,
 * компенсация не требуется.
 */
public class KeycloakUserCreationException extends KeycloakException {

    /**
     * @param message описание причины отказа, включая статус ответа Keycloak
     */
    public KeycloakUserCreationException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    /**
     * @param message описание причины отказа
     * @param cause   исходная ошибка Admin API
     */
    public KeycloakUserCreationException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
