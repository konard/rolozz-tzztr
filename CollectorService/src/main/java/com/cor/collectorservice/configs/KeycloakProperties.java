package com.cor.collectorservice.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Типизированные настройки интеграции с Keycloak.
 * <p>
 * Значения читаются из секции {@code keycloak} в {@code application.yml}.
 * Настройки используются:
 * <ul>
 *     <li>{@link com.cor.collectorservice.client.KeycloakAdminClient} — для управления пользователями через Admin API;</li>
 *     <li>{@link com.cor.collectorservice.client.KeycloakTokenClient} — для получения токенов по логину и паролю;</li>
 *     <li>{@link com.cor.collectorservice.util.jwt.KeycloakJwtTokenVerifier} — для проверки подписи и claim-ов JWT.</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    /**
     * Имя realm-а, в котором живут пользователи сервиса (например, {@code my-realm}).
     */
    private String realm;

    /**
     * Базовый URL сервера Keycloak без завершающего слэша (например, {@code http://localhost:8095}).
     */
    private String authServerUrl;

    /**
     * Идентификатор конфиденциального клиента, от имени которого выполняется вход пользователя.
     */
    private String clientId;

    /**
     * Секрет конфиденциального клиента, используемый в grant-запросе {@code password}.
     */
    private String clientSecret;

    /**
     * Realm-роль, которая назначается пользователю сразу после регистрации.
     */
    private String defaultRole = "USER";

    /**
     * Настройки сервисной (административной) учётной записи Keycloak.
     */
    private Admin admin = new Admin();

    /**
     * Настройки процесса регистрации, включая механизм ретрая компенсации.
     */
    private Registration registration = new Registration();

    /**
     * Настройки проверки JWT входящих запросов.
     */
    private Jwt jwt = new Jwt();

    /**
     * Возвращает адрес realm-а: {@code {authServerUrl}/realms/{realm}}.
     *
     * @return полный URL realm-а, он же ожидаемый издатель (issuer) токенов
     */
    public String getRealmUrl() {
        return authServerUrl + "/realms/" + realm;
    }

    /**
     * Возвращает издателя (claim {@code iss}), которого сервис ожидает в access-токенах.
     * <p>
     * По умолчанию это адрес realm-а, но если внутренний адрес Keycloak отличается от внешнего
     * (например, сервис ходит на {@code http://keycloak:8080}, а токены выпускаются
     * с адресом {@code http://localhost:8095}), ожидаемого издателя нужно задать явно
     * свойством {@code keycloak.jwt.issuer-uri}.
     *
     * @return ожидаемое значение claim {@code iss}
     */
    public String getExpectedIssuer() {
        return StringUtils.hasText(jwt.getIssuerUri()) ? jwt.getIssuerUri() : getRealmUrl();
    }

    /**
     * Возвращает адрес эндпоинта выдачи токенов realm-а.
     *
     * @return URL {@code token} эндпоинта протокола OpenID Connect
     */
    public String getTokenUri() {
        return getRealmUrl() + "/protocol/openid-connect/token";
    }

    /**
     * Возвращает адрес набора публичных ключей (JWKS) realm-а.
     *
     * @return URL {@code certs} эндпоинта протокола OpenID Connect
     */
    public String getJwksUri() {
        return getRealmUrl() + "/protocol/openid-connect/certs";
    }

    /**
     * Параметры административной учётной записи Keycloak,
     * от имени которой выполняются операции создания и удаления пользователей.
     */
    @Getter
    @Setter
    public static class Admin {

        /**
         * Логин администратора Keycloak.
         */
        private String username;

        /**
         * Пароль администратора Keycloak.
         */
        private String password;

        /**
         * Идентификатор клиента для административного доступа (обычно {@code admin-cli}).
         */
        private String clientId = "admin-cli";

        /**
         * Realm, в котором заведена административная учётная запись (обычно {@code master}).
         */
        private String realm = "master";

        /**
         * Размер пула HTTP-соединений административного клиента.
         */
        private int connectionPoolSize = 10;
    }

    /**
     * Настройки регистрации пользователя.
     */
    @Getter
    @Setter
    public static class Registration {

        /**
         * Настройки компенсации (отката) записи в Keycloak, если сохранение в БД не удалось.
         */
        private Compensation compensation = new Compensation();
    }

    /**
     * Настройки механизма ретрая компенсирующего удаления пользователя из Keycloak.
     * <p>
     * Механизм включается, когда пользователь уже создан в Keycloak,
     * но транзакция сохранения в БД сервиса откатилась, и немедленное удаление
     * записи из Keycloak тоже завершилось ошибкой.
     */
    @Getter
    @Setter
    public static class Compensation {

        /**
         * Максимальное количество фоновых попыток удаления «осиротевшего» пользователя.
         */
        private int maxAttempts = 10;

        /**
         * Интервал между запусками фонового обработчика очереди компенсаций (формат ISO-8601).
         */
        private Duration retryInterval = Duration.ofSeconds(30);
    }

    /**
     * Настройки проверки входящих JWT.
     */
    @Getter
    @Setter
    public static class Jwt {

        /**
         * Роль, без которой запрос к защищённым эндпоинтам отклоняется (используется в аннотации
         * {@link com.cor.collectorservice.util.annotation.RequiresRole} по умолчанию).
         */
        private String requiredRole = "USER";

        /**
         * Ожидаемый издатель токенов (claim {@code iss}).
         * <p>
         * Задаётся только тогда, когда внутренний адрес Keycloak не совпадает с адресом,
         * который Keycloak подставляет в выпускаемые токены. Если свойство не задано,
         * используется {@link KeycloakProperties#getRealmUrl()}.
         */
        private String issuerUri;

        /**
         * Допустимое расхождение часов между сервисом и Keycloak при проверке {@code exp}/{@code nbf}.
         */
        private Duration clockSkew = Duration.ofSeconds(30);

        /**
         * Пути, которые доступны без токена (Ant-шаблоны).
         */
        private List<String> publicPaths = List.of(
                "/api/auth/**",
                "/api-docs/**",
                "/api-docs",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/actuator/**",
                "/error"
        );
    }
}
