package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends BaseException {

    public UserAlreadyExistsException(String username) {
        super("Пользователь с именем уже существует: " + username, HttpStatus.CONFLICT);
    }
}

