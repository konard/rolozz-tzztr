package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Keycloak отклонил вход: неверная пара «логин — пароль» либо учётная запись
 * заблокирована или не подтверждена.
 * <p>
 * Текст намеренно не уточняет, что именно неверно, чтобы не давать возможность
 * перебирать существующие логины.
 */
public class InvalidCredentialsException extends BaseException {

    /**
     * Создаёт исключение с обезличенным сообщением о неверных учётных данных.
     */
    public InvalidCredentialsException() {
        super("Неверное имя пользователя или пароль", HttpStatus.UNAUTHORIZED);
    }
}
