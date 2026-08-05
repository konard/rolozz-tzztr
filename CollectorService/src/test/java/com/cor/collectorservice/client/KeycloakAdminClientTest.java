package com.cor.collectorservice.client;

import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.exception.KeycloakConfigurationException;
import com.cor.collectorservice.util.exception.KeycloakOperationException;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Тесты перевода ошибок Admin API Keycloak в доменные исключения сервиса.
 * <p>
 * Проверяют требование задачи: наружу не должны просачиваться «чужие» runtime-исключения
 * (в частности, {@link WebApplicationException} и его наследники из JAX-RS) — каждое
 * приводится к собственному исключению, известному глобальному обработчику.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakAdminClientTest {

    private static final String USERNAME = "john_doe";
    private static final String EMAIL = "john@example.com";
    private static final String PASSWORD = "Secret123!";

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    private KeycloakAdminClient adminClient;
    private UUID userId;

    @BeforeEach
    void setUp() {
        KeycloakProperties properties = new KeycloakProperties();
        properties.setRealm("my-realm");
        adminClient = new KeycloakAdminClient(keycloak, properties);
        userId = UUID.randomUUID();

        lenient().when(keycloak.realm("my-realm")).thenReturn(realmResource);
        lenient().when(realmResource.users()).thenReturn(usersResource);
    }

    @Test
    @DisplayName("Отказ Admin API 401 при создании: ошибка настроек сервисной учётной записи")
    void translatesUnauthorizedOnCreateToConfigurationException() {
        when(usersResource.create(any())).thenThrow(webApplicationException(Response.Status.UNAUTHORIZED));

        assertThatThrownBy(() -> adminClient.createUser(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(KeycloakConfigurationException.class)
                .hasMessageContaining(USERNAME)
                .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    @DisplayName("Отказ Admin API 500 при создании: Keycloak считается недоступным")
    void translatesServerErrorOnCreateToUnavailableException() {
        when(usersResource.create(any()))
                .thenThrow(webApplicationException(Response.Status.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adminClient.createUser(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(KeycloakUnavailableException.class)
                .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    @DisplayName("Сетевая ошибка при создании: Keycloak считается недоступным")
    void translatesProcessingExceptionOnCreateToUnavailableException() {
        when(usersResource.create(any())).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> adminClient.createUser(USERNAME, EMAIL, PASSWORD))
                .isInstanceOf(KeycloakUnavailableException.class);
    }

    @Test
    @DisplayName("Прочий отказ Admin API при удалении: обобщённая ошибка операции")
    void translatesBadRequestOnDeleteToOperationException() {
        when(usersResource.delete(userId.toString())).thenThrow(webApplicationException(Response.Status.BAD_REQUEST));

        assertThatThrownBy(() -> adminClient.deleteUser(userId))
                .isInstanceOf(KeycloakOperationException.class)
                .hasMessageContaining(userId.toString())
                .hasCauseInstanceOf(WebApplicationException.class);
    }

    @Test
    @DisplayName("Учётной записи в Keycloak уже нет: удаление считается выполненным")
    void treatsMissingUserOnDeleteAsSuccess() {
        when(usersResource.delete(userId.toString())).thenThrow(new NotFoundException());

        assertThatCode(() -> adminClient.deleteUser(userId)).doesNotThrowAnyException();
    }

    /**
     * Создаёт ошибку JAX-RS с нужным статусом ответа.
     *
     * @param status статус, с которым Admin API отказал в операции
     * @return исключение, какое бросил бы клиент Keycloak
     */
    private WebApplicationException webApplicationException(Response.Status status) {
        return new WebApplicationException(Response.status(status).build());
    }
}
