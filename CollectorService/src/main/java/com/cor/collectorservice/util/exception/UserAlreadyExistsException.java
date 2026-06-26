package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends BaseException {

    public UserAlreadyExistsException(String username) {
        super("Username already exists: " + username, HttpStatus.CONFLICT);
    }
}

