package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends BaseException {

    public UserNotFoundException(String username) {
        super("User not found: " + username, HttpStatus.NOT_FOUND);
    }
}
