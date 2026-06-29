package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class WbApiException extends BaseException {

    public WbApiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public WbApiException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
