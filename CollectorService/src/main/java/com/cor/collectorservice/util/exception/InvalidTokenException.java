package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Access-токен отсутствует, повреждён, просрочен, подписан неизвестным ключом
 * или выдан другим издателем.
 * <p>
 * Бросается {@link com.cor.collectorservice.util.jwt.TokenVerifier} и приводит к ответу 401.
 */
public class InvalidTokenException extends BaseException {

    /**
     * @param message описание причины, по которой токен отклонён
     */
    public InvalidTokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    /**
     * @param message описание причины, по которой токен отклонён
     * @param cause   исходная ошибка разбора или проверки подписи
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, HttpStatus.UNAUTHORIZED, cause);
    }
}
