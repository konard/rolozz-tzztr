package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BaseException {

    public UserNotFoundException(String username) {
        super("Пользователь не найден: " + username, HttpStatus.NOT_FOUND);
    }
}
