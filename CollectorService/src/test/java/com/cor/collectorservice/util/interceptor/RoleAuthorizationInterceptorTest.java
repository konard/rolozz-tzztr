package com.cor.collectorservice.util.interceptor;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.annotation.RequiresRole;
import com.cor.collectorservice.util.context.UserContext;
import com.cor.collectorservice.util.context.UserContextHolder;
import com.cor.collectorservice.util.exception.ForbiddenAccessException;
import com.cor.collectorservice.util.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты проверки ролей, заменившей {@code @PreAuthorize} из Spring Security.
 */
class RoleAuthorizationInterceptorTest {

    private RoleAuthorizationInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.getJwt().setRequiredRole("USER");
        interceptor = new RoleAuthorizationInterceptor(properties);
        request = new MockHttpServletRequest("GET", "/api/users/me");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    @DisplayName("Пользователь с требуемой ролью пропускается")
    void allowsUserWithRequiredRole() throws Exception {
        authenticateWithRoles("USER");

        assertThat(interceptor.preHandle(request, response, handler("annotatedOnClass"))).isTrue();
    }

    @Test
    @DisplayName("Роль берётся с метода и перекрывает роль класса")
    void methodAnnotationOverridesClassAnnotation() throws Exception {
        authenticateWithRoles("USER");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresAdmin")))
                .isInstanceOf(ForbiddenAccessException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    @DisplayName("Пустая аннотация использует роль по умолчанию из настроек")
    void emptyAnnotationFallsBackToConfiguredRole() throws Exception {
        authenticateWithRoles("MANAGER");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("requiresDefaultRole")))
                .isInstanceOf(ForbiddenAccessException.class)
                .hasMessageContaining("USER");
    }

    @Test
    @DisplayName("Достаточно любой из перечисленных ролей")
    void allowsAnyOfListedRoles() throws Exception {
        authenticateWithRoles("ADMIN");

        assertThat(interceptor.preHandle(request, response, handler("requiresUserOrAdmin"))).isTrue();
    }

    @Test
    @DisplayName("Запрос без аутентификации отклоняется с 401")
    void rejectsUnauthenticatedRequest() {
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("annotatedOnClass")))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    @DisplayName("Обработчики без аннотации и не-методы пропускаются")
    void passesThroughWhenNoRoleIsRequired() throws Exception {
        assertThat(interceptor.preHandle(request, response, "не HandlerMethod")).isTrue();
        assertThat(interceptor.preHandle(request, response, unannotatedHandler())).isTrue();
    }

    /**
     * Кладёт в контекст запроса пользователя с указанными ролями.
     *
     * @param roles realm-роли пользователя
     */
    private void authenticateWithRoles(String... roles) {
        UserContextHolder.set(new UserContext(UUID.randomUUID(), "john_doe", "john_doe@example.com", Set.of(roles)));
    }

    /**
     * Возвращает обработчик тестового контроллера, помеченного {@link RequiresRole} на уровне класса.
     *
     * @param methodName имя метода тестового контроллера
     * @return обработчик запроса
     */
    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = TestController.class.getMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    /**
     * Возвращает обработчик контроллера без аннотации ролей.
     *
     * @return обработчик запроса
     */
    private HandlerMethod unannotatedHandler() throws NoSuchMethodException {
        return new HandlerMethod(new PublicController(), PublicController.class.getMethod("open"));
    }

    /**
     * Тестовый контроллер с аннотацией роли на уровне класса.
     */
    @RequiresRole("USER")
    public static class TestController {

        /**
         * Метод без собственной аннотации: наследует роль класса.
         */
        public void annotatedOnClass() {
        }

        /**
         * Метод, требующий роль администратора.
         */
        @RequiresRole("ADMIN")
        public void requiresAdmin() {
        }

        /**
         * Метод с пустой аннотацией: требуется роль по умолчанию из настроек.
         */
        @RequiresRole
        public void requiresDefaultRole() {
        }

        /**
         * Метод, доступный обладателям любой из двух ролей.
         */
        @RequiresRole({"USER", "ADMIN"})
        public void requiresUserOrAdmin() {
        }
    }

    /**
     * Тестовый контроллер без требований к ролям.
     */
    public static class PublicController {

        /**
         * Публичный метод, доступный без ролей.
         */
        public void open() {
        }
    }
}
