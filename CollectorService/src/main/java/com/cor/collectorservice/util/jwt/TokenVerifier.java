package com.cor.collectorservice.util.jwt;

import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.exception.InvalidTokenException;

/**
 * Проверяет access-токен и извлекает из него данные пользователя.
 * <p>
 * Абстракция введена, чтобы фильтр аутентификации не зависел от конкретной библиотеки
 * разбора JWT и легко покрывался unit-тестами.
 */
public interface TokenVerifier {

    /**
     * Проверяет токен и возвращает данные пользователя.
     *
     * @param token access-токен в компактной сериализации JWS (без префикса {@code Bearer})
     * @return данные аутентифицированного пользователя
     * @throws InvalidTokenException если токен повреждён, просрочен, подписан неизвестным ключом
     *                               или выдан другим издателем
     */
    UserContext verify(String token);
}
