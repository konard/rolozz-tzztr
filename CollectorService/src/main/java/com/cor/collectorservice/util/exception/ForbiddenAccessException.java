package com.cor.collectorservice.util.exception;

import org.springframework.http.HttpStatus;

/**
 * Пользователь аутентифицирован, но у него нет роли, требуемой эндпоинтом.
 * <p>
 * Заменяет {@code org.springframework.security.access.AccessDeniedException}
 * после отказа от Spring Security. Бросается
 * {@link com.cor.collectorservice.util.interceptor.RoleAuthorizationInterceptor}.
 */
public class ForbiddenAccessException extends BaseException {

    /**
     * @param requiredRole роль, которой не хватает пользователю
     */
    public ForbiddenAccessException(String requiredRole) {
        super("Доступ запрещён: требуется роль " + requiredRole, HttpStatus.FORBIDDEN);
    }
}
