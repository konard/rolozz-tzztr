package com.cor.collectorservice.filter;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.InvalidTokenException;
import com.cor.collectorservice.util.jwt.TokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Тесты фильтра аутентификации, заменившего цепочку фильтров Spring Security.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenVerifier tokenVerifier;

    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new JwtAuthenticationFilter(tokenVerifier, objectMapper, new KeycloakProperties());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Корректный токен: контекст пользователя доступен в цепочке и очищается после запроса")
    void populatesUserContextForValidToken() throws Exception {
        UserContext context = new UserContext(UUID.randomUUID(), "john_doe", "john_doe@example.com", Set.of("USER"));
        when(tokenVerifier.verify("valid-token")).thenReturn(context);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<UserContext> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInsideChain.set(UserContextHolder.get());

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain.get()).isEqualTo(context);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        // Контекст обязан быть очищен, иначе он «протечёт» в следующий запрос того же потока
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Без заголовка Authorization возвращается 401 в формате ErrorResponse")
    void returnsUnauthorizedWhenTokenIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":401", "/api/users/me");
        verifyNoInteractions(chain);
        verify(tokenVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("Недействительный токен возвращает 401 и не пропускает запрос дальше")
    void returnsUnauthorizedWhenTokenIsInvalid() throws Exception {
        when(tokenVerifier.verify("broken")).thenThrow(new InvalidTokenException("Токен недействителен или просрочен"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cards/current");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer broken");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).contains("Токен недействителен или просрочен");
        verifyNoInteractions(chain);
        assertThat(UserContextHolder.get()).isNull();
    }

    @Test
    @DisplayName("Публичные пути пропускаются без токена")
    void skipsPublicPaths() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/login"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/auth/register"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui.html"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api-docs"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
    }

    @Test
    @DisplayName("Защищённые пути и preflight-запросы разделяются корректно")
    void filtersProtectedPathsButSkipsPreflight() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/users/me"))).isFalse();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("OPTIONS", "/api/users/me"))).isTrue();
    }
}
