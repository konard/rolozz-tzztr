package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Admin API Keycloak отказал в операции, и отказ не подходит ни под одну из более
 * конкретных причин (создание, обновление, удаление, недоступность, конфигурация).
 * <p>
 * Нужен, чтобы наружу не просачивались «голые» исключения JAX-RS
 * ({@code jakarta.ws.rs.WebApplicationException} и его наследники):
 * любая ошибка провайдера идентификации попадает в
 * {@link com.cor.collectorservice.util.globalHandler.GlobalExceptionHandler}
 * в виде {@link KeycloakException}.
 */
public class KeycloakOperationException extends KeycloakException {

    /**
     * @param message описание операции и статуса ответа Keycloak
     * @param cause   исходная ошибка Admin API
     */
    public KeycloakOperationException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY, cause);
    }
}
