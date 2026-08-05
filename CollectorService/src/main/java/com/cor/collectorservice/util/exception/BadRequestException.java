package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Запрос синтаксически корректен, но бессмысленен по смыслу — например, обновление
 * профиля, в котором не заполнено ни одно поле.
 */
public class BadRequestException extends BaseException {

    /**
     * @param message описание того, что именно не так с запросом
     */
    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
