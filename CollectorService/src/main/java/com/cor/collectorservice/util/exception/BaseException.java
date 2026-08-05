package com.cor.collectorservice.util.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Базовое исключение сервиса: каждое прикладное исключение несёт HTTP-статус,
 * с которым {@link com.cor.collectorservice.util.globalHandler.GlobalExceptionHandler}
 * отвечает клиенту.
 * <p>
 * Наследники заменяют «голые» {@link RuntimeException}: благодаря общему предку
 * глобальный обработчик обрабатывает их одним методом, а статус ответа задаётся
 * там, где ошибка возникла, а не в обработчике.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    /**
     * HTTP-статус, который будет возвращён клиенту.
     */
    private final HttpStatus status;

    /**
     * @param message человекочитаемое описание ошибки на русском языке
     * @param status  HTTP-статус ответа
     */
    protected BaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * @param message человекочитаемое описание ошибки на русском языке
     * @param status  HTTP-статус ответа
     * @param cause   исходная ошибка; сохраняется, чтобы первопричина попадала в лог
     */
    protected BaseException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
