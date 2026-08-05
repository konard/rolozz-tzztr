package com.cor.collectorservice.util.context;

import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import lombok.extern.slf4j.Slf4j;

/**
 * Хранилище данных текущего пользователя в рамках обрабатываемого запроса.
 * <p>
 * Аналог {@code SecurityContextHolder} из Spring Security, но без зависимости от него:
 * значение кладётся фильтром {@link com.cor.collectorservice.filter.JwtAuthenticationFilter}
 * после успешной проверки JWT и обязательно очищается в блоке {@code finally},
 * чтобы контекст не «протёк» в следующий запрос при переиспользовании потоков.
 */
@Slf4j
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * Сохраняет данные пользователя для текущего потока обработки запроса.
     *
     * @param context данные пользователя, извлечённые из токена
     */
    public static void set(UserContext context) {
        log.debug("Установка контекста пользователя: {} ({})", context.username(), context.userId());
        CONTEXT.set(context);
    }

    /**
     * Возвращает данные пользователя текущего запроса.
     *
     * @return данные пользователя или {@code null}, если запрос выполняется без аутентификации
     */
    public static UserContext get() {
        return CONTEXT.get();
    }

    /**
     * Возвращает данные пользователя текущего запроса, требуя обязательного наличия аутентификации.
     *
     * @return данные аутентифицированного пользователя
     * @throws UnauthorizedAccessException если контекст пуст, то есть запрос не аутентифицирован
     */
    public static UserContext getRequired() {
        UserContext context = CONTEXT.get();
        if (context == null) {
            log.warn("Попытка доступа к контексту пользователя без аутентификации");
            throw new UnauthorizedAccessException();
        }
        return context;
    }

    /**
     * Очищает контекст текущего потока. Вызывается фильтром после обработки запроса.
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
