package com.cor.collectorservice.util.jwt;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.exception.InvalidTokenException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Проверка access-токенов Keycloak по публичным ключам realm-а (JWKS).
 * <p>
 * Проверяются:
 * <ul>
 *     <li>подпись токена алгоритмом RS256 ключом из JWKS realm-а;</li>
 *     <li>срок действия {@code exp} и время выпуска {@code iat} с допуском на расхождение часов;</li>
 *     <li>издатель {@code iss} — должен совпадать с {@code {auth-server-url}/realms/{realm}};</li>
 *     <li>наличие обязательных claim-ов {@code sub} и {@code preferred_username}.</li>
 * </ul>
 * Обращение к сети выполняется только при первой проверке и при ротации ключей Keycloak:
 * {@link JWKSource} кэширует полученный набор ключей.
 */
@Slf4j
@Component
public class KeycloakJwtTokenVerifier implements TokenVerifier {

    /**
     * Claim Keycloak, содержащий объект с realm-ролями пользователя.
     */
    private static final String REALM_ACCESS_CLAIM = "realm_access";

    /**
     * Claim со списком ролей: используется как внутри {@code realm_access},
     * так и на верхнем уровне (протокол-маппер {@code realm-roles-mapper} из конфигурации realm-а).
     */
    private static final String ROLES_CLAIM = "roles";

    /**
     * Claim с логином пользователя.
     */
    private static final String USERNAME_CLAIM = "preferred_username";

    /**
     * Claim с e-mail пользователя.
     */
    private static final String EMAIL_CLAIM = "email";

    private final DefaultJWTProcessor<SecurityContext> jwtProcessor;

    /**
     * @param properties настройки Keycloak (ожидаемый издатель и допуск расхождения часов)
     * @param jwkSource  источник публичных ключей realm-а
     */
    public KeycloakJwtTokenVerifier(KeycloakProperties properties, JWKSource<SecurityContext> jwkSource) {
        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(properties.getExpectedIssuer()).build(),
                Set.of("sub", "exp", USERNAME_CLAIM)
        );
        claimsVerifier.setMaxClockSkew((int) properties.getJwt().getClockSkew().toSeconds());

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier(claimsVerifier);

        this.jwtProcessor = processor;
        log.info("Проверка JWT настроена: ожидаемый издатель={}, допустимое расхождение часов={}",
                properties.getExpectedIssuer(), properties.getJwt().getClockSkew());
    }

    /**
     * {@inheritDoc}
     *
     * @throws KeycloakUnavailableException если публичные ключи realm-а недоступны
     */
    @Override
    public UserContext verify(String token) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);

            UUID userId = parseUserId(claims.getSubject());
            String username = claims.getStringClaim(USERNAME_CLAIM);
            String email = claims.getStringClaim(EMAIL_CLAIM);
            Set<String> roles = extractRoles(claims);

            log.debug("Токен успешно проверен: пользователь={}, id={}, роли={}", username, userId, roles);
            return new UserContext(userId, username, email, roles);
        } catch (ParseException ex) {
            log.warn("Не удалось разобрать JWT: {}", ex.getMessage());
            throw new InvalidTokenException("Токен имеет некорректный формат", ex);
        } catch (BadJOSEException ex) {
            log.warn("JWT отклонён при проверке: {}", ex.getMessage());
            throw new InvalidTokenException("Токен недействителен или просрочен", ex);
        } catch (JOSEException ex) {
            log.error("Не удалось проверить подпись JWT: публичные ключи Keycloak недоступны", ex);
            throw new KeycloakUnavailableException("Не удалось получить публичные ключи Keycloak", ex);
        }
    }

    /**
     * Преобразует claim {@code sub} в идентификатор пользователя.
     *
     * @param subject значение claim {@code sub}
     * @return идентификатор пользователя
     * @throws InvalidTokenException если claim отсутствует или не является UUID
     */
    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("Claim sub не является идентификатором пользователя Keycloak: {}", subject);
            throw new InvalidTokenException("Токен не содержит корректный идентификатор пользователя", ex);
        }
    }

    /**
     * Собирает роли пользователя из claim-ов {@code realm_access.roles} и {@code roles}.
     *
     * @param claims набор claim-ов проверенного токена
     * @return множество ролей; пустое, если роли в токене отсутствуют
     */
    private Set<String> extractRoles(JWTClaimsSet claims) {
        Set<String> roles = new LinkedHashSet<>();

        Object realmAccess = claims.getClaim(REALM_ACCESS_CLAIM);
        if (realmAccess instanceof Map<?, ?> realmAccessMap
                && realmAccessMap.get(ROLES_CLAIM) instanceof Collection<?> realmRoles) {
            realmRoles.forEach(role -> roles.add(String.valueOf(role)));
        }

        Object topLevelRoles = claims.getClaim(ROLES_CLAIM);
        if (topLevelRoles instanceof Collection<?> mappedRoles) {
            mappedRoles.forEach(role -> roles.add(String.valueOf(role)));
        }

        if (roles.isEmpty()) {
            log.debug("Токен не содержит ролей пользователя");
            return Set.of();
        }
        return Set.copyOf(List.copyOf(roles));
    }
}
