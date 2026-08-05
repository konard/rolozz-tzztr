package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Не удалось обновить данные пользователя (логин, e-mail, пароль) в Keycloak.
 */
public class KeycloakUserUpdateException extends KeycloakException {

    /**
     * @param message описание причины отказа, включая идентификатор пользователя
     */
    public KeycloakUserUpdateException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    /**
     * @param message описание причины отказа
     * @param cause   исходная ошибка Admin API
     */
    public KeycloakUserUpdateException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
