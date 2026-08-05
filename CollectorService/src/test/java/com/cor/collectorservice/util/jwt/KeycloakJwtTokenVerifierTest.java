package com.cor.collectorservice.util.jwt;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.exception.InvalidTokenException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты проверки access-токенов Keycloak без Spring Security.
 * <p>
 * Keycloak в тестах не поднимается: вместо JWKS realm-а используется пара RSA-ключей,
 * сгенерированная в памяти, — токены подписываются приватным ключом,
 * а проверяются публичным, как это происходит в бою.
 */
class KeycloakJwtTokenVerifierTest {

    private static final String ISSUER = "http://localhost:8095/realms/my-realm";

    private static RSAKey signingKey;
    private static RSAKey foreignKey;

    private KeycloakJwtTokenVerifier verifier;
    private UUID userId;

    @BeforeAll
    static void generateKeys() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        foreignKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    @BeforeEach
    void setUp() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setRealm("my-realm");
        properties.setAuthServerUrl("http://localhost:8095");

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(signingKey.toPublicJWK()));
        verifier = new KeycloakJwtTokenVerifier(properties, jwkSource);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Корректный токен: контекст пользователя собирается из claim-ов")
    void extractsUserContextFromValidToken() throws JOSEException {
        String token = sign(validClaims().build(), signingKey);

        UserContext context = verifier.verify(token);

        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.username()).isEqualTo("john_doe");
        assertThat(context.email()).isEqualTo("john_doe@example.com");
        assertThat(context.roles()).containsExactlyInAnyOrder("USER", "default-roles-my-realm");
        assertThat(context.hasRole("USER")).isTrue();
        assertThat(context.hasRole("ADMIN")).isFalse();
    }

    @Test
    @DisplayName("Роли собираются и из realm_access.roles, и из claim верхнего уровня roles")
    void mergesRolesFromBothClaims() throws JOSEException {
        String token = sign(validClaims()
                .claim("roles", List.of("ADMIN"))
                .build(), signingKey);

        UserContext context = verifier.verify(token);

        assertThat(context.roles()).contains("USER", "ADMIN");
    }

    @Test
    @DisplayName("Токен без ролей: множество ролей пустое, доступ по роли не выдаётся")
    void returnsEmptyRolesWhenTokenHasNone() throws JOSEException {
        String token = sign(baseClaims().build(), signingKey);

        UserContext context = verifier.verify(token);

        assertThat(context.roles()).isEmpty();
        assertThat(context.hasRole("USER")).isFalse();
    }

    @Test
    @DisplayName("Подпись чужим ключом отклоняется")
    void rejectsTokenSignedByForeignKey() throws JOSEException {
        String token = sign(validClaims().build(), foreignKey);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Просроченный токен отклоняется")
    void rejectsExpiredToken() throws JOSEException {
        String token = sign(validClaims()
                .expirationTime(Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .build(), signingKey);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Токен другого издателя отклоняется")
    void rejectsTokenFromAnotherIssuer() throws JOSEException {
        String token = sign(validClaims()
                .issuer("http://evil.example.com/realms/my-realm")
                .build(), signingKey);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Токен без обязательного claim preferred_username отклоняется")
    void rejectsTokenWithoutUsernameClaim() throws JOSEException {
        String token = sign(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .build(), signingKey);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Claim sub, не являющийся UUID, отклоняется")
    void rejectsTokenWithNonUuidSubject() throws JOSEException {
        String token = sign(validClaims()
                .subject("not-a-uuid")
                .build(), signingKey);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("идентификатор");
    }

    @Test
    @DisplayName("Строка, не являющаяся JWT, отклоняется")
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> verifier.verify("это-не-токен"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("формат");
    }

    /**
     * Собирает claim-ы валидного access-токена Keycloak с realm-ролью {@code USER}.
     *
     * @return построитель набора claim-ов
     */
    private JWTClaimsSet.Builder validClaims() {
        return baseClaims()
                .claim("realm_access", Map.of("roles", List.of("USER", "default-roles-my-realm")));
    }

    /**
     * Собирает минимальный набор обязательных claim-ов без ролей.
     *
     * @return построитель набора claim-ов
     */
    private JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("preferred_username", "john_doe")
                .claim("email", "john_doe@example.com")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)));
    }

    /**
     * Подписывает набор claim-ов указанным ключом алгоритмом RS256.
     *
     * @param claims набор claim-ов
     * @param key    ключ подписи
     * @return сериализованный JWT
     */
    private String sign(JWTClaimsSet claims, RSAKey key) throws JOSEException {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
