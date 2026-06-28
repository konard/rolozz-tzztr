package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class DecryptionException extends BaseException {

    public DecryptionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public DecryptionException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
