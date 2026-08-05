package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Логин уже занят.
 * <p>
 * Бросается как при регистрации (проверка до обращения к Keycloak и при ответе 409
 * от самого Keycloak), так и при смене логина в профиле.
 */
public class UserAlreadyExistsException extends BaseException {

    /**
     * @param username занятый логин пользователя
     */
    public UserAlreadyExistsException(String username) {
        super("Пользователь с именем уже существует: " + username, HttpStatus.CONFLICT);
    }
}

