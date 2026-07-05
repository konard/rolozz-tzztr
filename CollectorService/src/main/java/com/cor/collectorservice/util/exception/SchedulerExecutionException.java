package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Исключение для ошибок выполнения планировщика задач
 */
public class SchedulerExecutionException extends BaseException {

    public SchedulerExecutionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SchedulerExecutionException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}
