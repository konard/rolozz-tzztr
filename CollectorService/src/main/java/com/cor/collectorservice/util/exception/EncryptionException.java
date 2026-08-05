package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Не удалось зашифровать значение перед сохранением (например, WB-токен пользователя).
 */
public class EncryptionException extends BaseException {

    /**
     * @param message описание операции, которую не удалось выполнить
     */
    public EncryptionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * @param message описание операции, которую не удалось выполнить
     * @param cause   исходная криптографическая ошибка
     */
    public EncryptionException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
