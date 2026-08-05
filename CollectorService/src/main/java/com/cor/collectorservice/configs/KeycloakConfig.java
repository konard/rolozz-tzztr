package com.cor.collectorservice.configs;

import com.cor.collectorservice.util.exception.KeycloakConfigurationException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Конфигурация интеграции с Keycloak.
 * <p>
 * Поднимает три бина:
 * <ul>
 *     <li>{@link Keycloak} — административный клиент (создание, обновление и удаление пользователей);</li>
 *     <li>{@link RestClient} — HTTP-клиент для эндпоинта выдачи токенов (вход по логину и паролю);</li>
 *     <li>{@link JWKSource} — источник публичных ключей realm-а для проверки подписи входящих JWT.</li>
 * </ul>
 * Ни один из бинов не устанавливает соединение с Keycloak во время старта приложения:
 * подключение выполняется лениво, при первом обращении, поэтому сервис поднимается
 * даже если Keycloak временно недоступен.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {

    /**
     * Создаёт административный клиент Keycloak, работающий от имени сервисной учётной записи.
     *
     * @param properties настройки интеграции с Keycloak
     * @return административный клиент Keycloak
     */
    @Bean(destroyMethod = "close")
    public Keycloak keycloakAdmin(KeycloakProperties properties) {
        KeycloakProperties.Admin admin = properties.getAdmin();
        log.info("Инициализация административного клиента Keycloak: сервер={}, realm администратора={}, клиент={}",
                properties.getAuthServerUrl(), admin.getRealm(), admin.getClientId());

        return KeycloakBuilder.builder()
                .serverUrl(properties.getAuthServerUrl())
                .realm(admin.getRealm())
                .clientId(admin.getClientId())
                .username(admin.getUsername())
                .password(admin.getPassword())
                .grantType(OAuth2Constants.PASSWORD)
                .build();
    }

    /**
     * Создаёт HTTP-клиент для запросов к OpenID Connect эндпоинтам realm-а
     * (получение токена по логину и паролю).
     *
     * @param properties настройки интеграции с Keycloak
     * @return HTTP-клиент с базовым адресом сервера Keycloak
     */
    @Bean
    public RestClient keycloakRestClient(KeycloakProperties properties) {
        log.info("Инициализация HTTP-клиента Keycloak с базовым URL: {}", properties.getAuthServerUrl());
        return RestClient.builder()
                .baseUrl(properties.getAuthServerUrl())
                .build();
    }

    /**
     * Создаёт источник публичных ключей realm-а (JWKS) с кэшированием и повторными попытками.
     * <p>
     * Ключи скачиваются при первой проверке токена и обновляются автоматически,
     * в том числе при появлении в токене неизвестного {@code kid} (ротация ключей Keycloak).
     *
     * @param properties настройки интеграции с Keycloak
     * @return источник публичных ключей для проверки подписи JWT
     * @throws KeycloakConfigurationException если {@code keycloak.auth-server-url} задан некорректно
     */
    @Bean
    public JWKSource<SecurityContext> keycloakJwkSource(KeycloakProperties properties) {
        String jwksUri = properties.getJwksUri();
        log.info("Инициализация источника публичных ключей Keycloak: {}", jwksUri);
        try {
            URL jwksUrl = URI.create(jwksUri).toURL();
            return JWKSourceBuilder.create(jwksUrl)
                    .retrying(true)
                    .build();
        } catch (MalformedURLException | IllegalArgumentException ex) {
            log.error("Некорректный адрес JWKS Keycloak: {}", jwksUri, ex);
            throw new KeycloakConfigurationException("Некорректный адрес JWKS Keycloak: " + jwksUri, ex);
        }
    }
}
