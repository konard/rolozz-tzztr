package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Базовое исключение для всех ошибок взаимодействия с Keycloak.
 * <p>
 * Позволяет обработчику {@link com.cor.collectorservice.util.globalHandler.GlobalExceptionHandler}
 * ловить любые проблемы провайдера идентификации одним обработчиком,
 * не перечисляя каждый конкретный класс.
 */
public abstract class KeycloakException extends BaseException {

    /**
     * @param message человекочитаемое описание ошибки на русском языке
     * @param status  HTTP-статус, который будет возвращён клиенту
     */
    protected KeycloakException(String message, HttpStatus status) {
        super(message, status);
    }

    /**
     * @param message человекочитаемое описание ошибки на русском языке
     * @param status  HTTP-статус, который будет возвращён клиенту
     * @param cause   исходная причина ошибки
     */
    protected KeycloakException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
