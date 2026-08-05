package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Запрос выполнен без аутентификации: в {@link com.cor.collectorservice.util.context.UserContextHolder}
 * нет пользователя.
 * <p>
 * Заменяет {@code org.springframework.security.core.AuthenticationException} после отказа
 * от Spring Security. В отличие от {@link ForbiddenAccessException} означает, что пользователь
 * не известен вовсе, а не что ему не хватает роли.
 */
public class UnauthorizedAccessException extends BaseException {

    /**
     * Создаёт исключение со стандартным сообщением о необходимости аутентификации.
     */
    public UnauthorizedAccessException() {
        super("Требуется аутентификация", HttpStatus.UNAUTHORIZED);
    }

    /**
     * @param message уточнённое описание причины отказа
     */
    public UnauthorizedAccessException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
