package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class WbSyncException extends BaseException {

    public WbSyncException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public WbSyncException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
