package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Профиль пользователя не найден в базе сервиса.
 * <p>
 * После перехода на Keycloak это означает рассогласование хранилищ: токен прошёл проверку,
 * то есть учётная запись в Keycloak есть, а профиля с таким идентификатором в базе нет.
 * Такое возможно, если компенсация регистрации удалила профиль, а удаление учётной записи
 * ещё не доехало.
 */
public class UserNotFoundException extends BaseException {

    /**
     * @param username логин либо идентификатор пользователя, профиль которого не найден
     */
    public UserNotFoundException(String username) {
        super("Пользователь не найден: " + username, HttpStatus.NOT_FOUND);
    }
}
