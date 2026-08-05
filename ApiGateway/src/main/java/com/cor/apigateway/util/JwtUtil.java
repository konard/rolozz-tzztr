package com.cor.apigateway.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Утилита для работы с JWT токенами.
 * Предоставляет методы для валидации токенов и извлечения данных из них.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final ReactiveCircuitBreakerFactory<?, ?> reactiveCircuitBreakerFactory;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    private RSAPublicKey publicKey;
    private long lastKeyRefresh = 0;
    private static final long KEY_REFRESH_INTERVAL = 3600000; // 1 час

    @PostConstruct
    public void init() {
        log.info("Инициализация JWT утилиты, загрузка публичного ключа из Keycloak");
        refreshPublicKeySync();
    }

    /**
     * Валидирует JWT токен.
     * Проверяет подпись, срок действия и издателя токена.
     * 
     * @param token JWT токен для валидации
     * @return true если токен валиден, иначе false
     */
    public boolean validateToken(String token) {
        try {
            log.debug("Начало валидации JWT токена");
            SignedJWT signedJWT = SignedJWT.parse(token);
            
            // Проверка подписи
            log.debug("Проверка подписи JWT токена");
            if (!verifySignature(signedJWT)) {
                log.error("Неверная подпись JWT токена");
                return false;
            }
            log.debug("Подпись JWT токена валидна");

            // Проверка срока действия
            log.debug("Проверка срока действия JWT токена");
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            Date expirationTime = claimsSet.getExpirationTime();
            if (expirationTime != null && expirationTime.before(new Date())) {
                log.error("Срок действия JWT токена истек");
                return false;
            }
            log.debug("Срок действия JWT токена валиден");

            // Проверка издателя
            log.debug("Проверка издателя JWT токена");
            String issuer = claimsSet.getIssuer();
            String expectedIssuer = authServerUrl + "/realms/" + realm;
            log.debug("Issuer: {}, Expected: {}", issuer, expectedIssuer);
            if (issuer == null || !issuer.equals(expectedIssuer)) {
                log.error("Неверный издатель JWT токена: {}", issuer);
                return false;
            }
            log.debug("Издатель JWT токена валиден");

            log.debug("JWT токен успешно валидирован");
            return true;
        } catch (ParseException e) {
            log.error("Ошибка парсинга JWT токена", e);
            return false;
        }
    }

    /**
     * Извлекает ID пользователя из JWT токена.
     * 
     * @param token JWT токен
     * @return ID пользователя или null в случае ошибки
     */
    public String getUserId(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            return claimsSet.getSubject();
        } catch (ParseException e) {
            log.error("Ошибка извлечения ID пользователя из JWT", e);
            return null;
        }
    }

    /**
     * Извлекает имя пользователя из JWT токена.
     * 
     * @param token JWT токен
     * @return имя пользователя или null в случае ошибки
     */
    public String getUsername(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            
            // Отладка: выведем все доступные claims
            log.debug("Все claims в токене: {}", claimsSet.toJSONObject());
            
            String username = claimsSet.getStringClaim("preferred_username");
            if (username == null) {
                // Попробуем альтернативные поля
                username = claimsSet.getStringClaim("username");
                log.debug("preferred_username не найден, пробуем username: {}", username);
            }
            if (username == null) {
                // Попробуем извлечь из subject если это username
                username = claimsSet.getSubject();
                log.debug("username не найден, пробуем subject: {}", username);
            }
            
            log.debug("Итоговый username: {}", username);
            return username;
        } catch (ParseException e) {
            log.error("Ошибка извлечения имени пользователя из JWT", e);
            return null;
        }
    }

    /**
     * Извлекает роли пользователя из JWT токена.
     * 
     * @param token JWT токен
     * @return список ролей или null в случае ошибки
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();
            return (List<String>) claimsSet.getClaim("roles");
        } catch (ParseException e) {
            log.error("Ошибка извлечения ролей из JWT", e);
            return Collections.emptyList();
        }
    }

    /**
     * Проверяет подпись JWT токена с использованием публичного ключа.
     * 
     * @param signedJWT подписанный JWT токен
     * @return true если подпись верна, иначе false
     */
    private boolean verifySignature(SignedJWT signedJWT) {
        try {
            if (publicKey == null || System.currentTimeMillis() - lastKeyRefresh > KEY_REFRESH_INTERVAL) {
                refreshPublicKeySync();
            }

            if (publicKey == null) {
                log.error("Публичный ключ недоступен");
                return false;
            }

            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            return signedJWT.verify(verifier);
        } catch (JOSEException e) {
            log.error("Ошибка проверки подписи JWT", e);
            return false;
        }
    }

    /**
     * Синхронно обновляет публичный ключ из Keycloak.
     * Используется при старте приложения и в блокирующих операциях.
     */
    private void refreshPublicKeySync() {
        try {
            log.debug("Начало синхронной загрузки публичного ключа из Keycloak");
            URL jwksUrl = new URL(authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs");
            JWKSet jwkSet = JWKSet.load(jwksUrl);
            
            JWK jwk = jwkSet.getKeys().get(0);
            if (!(jwk instanceof RSAKey rsaKey)) {
                log.error("Первый ключ в JWKS не является RSA ключом");
                return;
            }

            RSAPublicKey fetchedKey = rsaKey.toRSAPublicKey();
            if (fetchedKey != null) {
                this.publicKey = fetchedKey;
                this.lastKeyRefresh = System.currentTimeMillis();
                log.info("Публичный ключ успешно загружен синхронно");
            }
        } catch (Exception e) {
            log.error("Ошибка синхронной загрузки публичного ключа из Keycloak", e);
        }
    }

    /**
     * Обновляет публичный ключ из Keycloak JWKS эндпоинта.
     */
    private void refreshPublicKey() {
        try {
            var circuitBreaker = reactiveCircuitBreakerFactory.create("keycloakService");

            Mono<RSAPublicKey> fetchMono = Mono.fromCallable(() -> {
                String jwksUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/certs";
                JWKSet jwkSet = JWKSet.load(new URL(jwksUrl));

                List<JWK> keys = jwkSet.getKeys();
                if (keys.isEmpty()) {
                    return null;
                }

                JWK jwk = keys.get(0);
                if (!(jwk instanceof RSAKey rsaKey)) {
                    return null;
                }

                return rsaKey.toRSAPublicKey();
            })
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(Mono::justOrEmpty);

            RSAPublicKey fetchedKey = circuitBreaker
                    .run(fetchMono, throwable -> {
                        log.error("Keycloak недоступен, circuit breaker сработал при обновлении публичного ключа", throwable);
                        return Mono.empty();
                    })
                    .block();

            if (fetchedKey != null) {
                this.publicKey = fetchedKey;
                this.lastKeyRefresh = System.currentTimeMillis();
                log.info("Публичный ключ успешно обновлен");
            }
        } catch (Exception e) {
            log.error("Ошибка обновления публичного ключа из Keycloak", e);
        }
    }
}
