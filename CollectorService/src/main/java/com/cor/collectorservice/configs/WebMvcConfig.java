package com.cor.collectorservice.configs;

import com.cor.collectorservice.util.interceptor.RoleAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Настройка Spring MVC для авторизации без Spring Security.
 * <p>
 * Регистрирует {@link RoleAuthorizationInterceptor}, который проверяет аннотацию
 * {@link com.cor.collectorservice.util.annotation.RequiresRole} на методах контроллеров.
 * Публичные пути (в том числе {@code /api/auth/**} и Swagger UI) из проверки исключаются:
 * их обработчики аннотацию не используют, но явное исключение экономит вызовы перехватчика.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleAuthorizationInterceptor roleAuthorizationInterceptor;
    private final KeycloakProperties keycloakProperties;

    /**
     * Регистрирует перехватчик проверки ролей для всех путей, кроме публичных.
     *
     * @param registry реестр перехватчиков Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var publicPaths = keycloakProperties.getJwt().getPublicPaths();
        log.info("Регистрация перехватчика проверки ролей. Исключения: {}", publicPaths);

        registry.addInterceptor(roleAuthorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(publicPaths);
    }
}
