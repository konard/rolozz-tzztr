package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Исключение для превышения лимита запросов
 */
public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
        initCause(cause);
    }
}
