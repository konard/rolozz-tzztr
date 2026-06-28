package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends BaseException {

    public InvalidCredentialsException() {
        super("Неверное имя пользователя или пароль", HttpStatus.UNAUTHORIZED);
    }
}
