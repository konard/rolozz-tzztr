package com.cor.collectorservice.util.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Требует наличия у пользователя хотя бы одной из перечисленных realm-ролей Keycloak.
 * <p>
 * Замена аннотации {@code @PreAuthorize("hasRole('USER')")} из Spring Security.
 * Проверку выполняет {@link com.cor.collectorservice.util.interceptor.RoleAuthorizationInterceptor}
 * до вызова метода контроллера: если роли нет — возвращается 403,
 * если запрос не аутентифицирован — 401.
 * <p>
 * Аннотацию можно ставить как на класс контроллера (действует на все его методы),
 * так и на отдельный метод (перекрывает настройку класса).
 *
 * <pre>{@code
 * @RequiresRole("ADMIN")
 * @DeleteMapping("/{id}")
 * public void delete(@PathVariable UUID id) { ... }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    /**
     * Список допустимых ролей. Доступ разрешён, если у пользователя есть хотя бы одна из них.
     * Если список пуст, используется роль по умолчанию из {@code keycloak.jwt.required-role}.
     *
     * @return имена требуемых realm-ролей
     */
    String[] value() default {};
}
