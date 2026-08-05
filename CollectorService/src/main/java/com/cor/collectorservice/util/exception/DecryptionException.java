package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Не удалось расшифровать хранимое значение (например, WB-токен пользователя).
 */
public class DecryptionException extends BaseException {

    /**
     * @param message описание операции, которую не удалось выполнить
     */
    public DecryptionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * @param message описание операции, которую не удалось выполнить
     * @param cause   исходная криптографическая ошибка
     */
    public DecryptionException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
