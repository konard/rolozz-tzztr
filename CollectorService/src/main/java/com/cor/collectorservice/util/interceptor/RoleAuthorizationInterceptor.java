package com.cor.collectorservice.util.interceptor;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.annotation.RequiresRole;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.ForbiddenAccessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * Проверяет права доступа к методам контроллеров, помеченным {@link RequiresRole}.
 * <p>
 * Работает после {@link com.cor.collectorservice.filter.JwtAuthenticationFilter}:
 * к моменту вызова контекст пользователя уже наполнен данными из токена.
 * Заменяет механизм {@code @PreAuthorize} из Spring Security.
 * <p>
 * Исключения, выброшенные из перехватчика, попадают в
 * {@link com.cor.collectorservice.util.globalHandler.GlobalExceptionHandler},
 * поэтому формат ответа об ошибке совпадает с остальными ошибками API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    private final KeycloakProperties properties;

    /**
     * Проверяет наличие требуемой роли перед вызовом метода контроллера.
     *
     * @param request  входящий запрос
     * @param response ответ клиенту
     * @param handler  обработчик запроса
     * @return {@code true}, если доступ разрешён
     * @throws com.cor.collectorservice.util.exception.UnauthorizedAccessException если запрос не аутентифицирован
     * @throws ForbiddenAccessException                                            если у пользователя нет требуемой роли
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresRole annotation = findAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }

        UserContext context = UserContextHolder.getRequired();
        List<String> requiredRoles = resolveRequiredRoles(annotation);

        boolean allowed = requiredRoles.stream().anyMatch(context::hasRole);
        if (!allowed) {
            log.warn("Пользователю {} отказано в доступе к {} {}: требуются роли {}, фактические роли {}",
                    context.username(), request.getMethod(), request.getRequestURI(),
                    requiredRoles, context.roles());
            throw new ForbiddenAccessException(String.join(" или ", requiredRoles));
        }

        log.debug("Пользователю {} разрешён доступ к {} {} по ролям {}",
                context.username(), request.getMethod(), request.getRequestURI(), requiredRoles);
        return true;
    }

    /**
     * Ищет аннотацию сначала на методе, затем на классе контроллера.
     *
     * @param handlerMethod вызываемый метод контроллера
     * @return найденная аннотация или {@code null}, если эндпоинт не требует ролей
     */
    private RequiresRole findAnnotation(HandlerMethod handlerMethod) {
        RequiresRole methodAnnotation =
                AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresRole.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresRole.class);
    }

    /**
     * Возвращает список ролей, любая из которых открывает доступ к эндпоинту.
     *
     * @param annotation аннотация эндпоинта
     * @return роли из аннотации либо роль по умолчанию из настроек, если аннотация пуста
     */
    private List<String> resolveRequiredRoles(RequiresRole annotation) {
        if (annotation.value().length == 0) {
            return List.of(properties.getJwt().getRequiredRole());
        }
        return Arrays.asList(annotation.value());
    }
}
