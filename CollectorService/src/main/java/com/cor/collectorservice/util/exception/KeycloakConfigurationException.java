package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Некорректная конфигурация интеграции с Keycloak
 * (например, недопустимый {@code keycloak.auth-server-url} в {@code application.yml}).
 * <p>
 * Ошибка не зависит от действий пользователя и требует правки настроек сервиса.
 */
public class KeycloakConfigurationException extends KeycloakException {

    /**
     * @param message описание некорректной настройки
     */
    public KeycloakConfigurationException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * @param message описание некорректной настройки
     * @param cause   исходная ошибка разбора настройки
     */
    public KeycloakConfigurationException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
