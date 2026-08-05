package com.cor.collectorservice.util.context;

import java.util.Set;
import java.util.UUID;

/**
 * Данные аутентифицированного пользователя, извлечённые из access-токена Keycloak.
 * <p>
 * Заменяет {@code org.springframework.security.core.Authentication}: объект складывается
 * в {@link UserContextHolder} фильтром
 * {@link com.cor.collectorservice.filter.JwtAuthenticationFilter} и живёт в пределах одного запроса.
 *
 * @param userId   идентификатор пользователя из claim {@code sub}; совпадает с первичным ключом в БД сервиса
 * @param username логин пользователя из claim {@code preferred_username}
 * @param email    e-mail пользователя из claim {@code email}, может быть {@code null}
 * @param roles    realm-роли пользователя из claim {@code realm_access.roles} и/или {@code roles}
 */
public record UserContext(UUID userId, String username, String email, Set<String> roles) {

    /**
     * Проверяет наличие роли у пользователя.
     *
     * @param role имя проверяемой роли, например {@code USER}
     * @return {@code true}, если роль присутствует в токене
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
