package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class WbRateLimitException extends BaseException {

    public WbRateLimitException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }

    public WbRateLimitException(String message, Throwable cause) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        initCause(cause);
    }
}
