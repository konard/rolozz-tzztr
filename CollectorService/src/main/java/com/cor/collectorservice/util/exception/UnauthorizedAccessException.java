package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedAccessException extends BaseException {

    public UnauthorizedAccessException() {
        super("Требуется аутентификация", HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
