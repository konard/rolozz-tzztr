package com.cor.collectorservice.service;

import com.cor.collectorservice.client.KeycloakAdminClient;
import com.cor.collectorservice.configs.KeycloakProperties;
import com.cor.collectorservice.util.exception.KeycloakUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Тесты механизма повторов компенсации регистрации.
 * <p>
 * Проверяют требование задачи: если запись в базу данных откатилась, учётная запись удаляется
 * из Keycloak, а при недоступности Keycloak запускается механизм повторных попыток.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationCompensationServiceTest {

    private static final String USERNAME = "john_doe";

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    private KeycloakProperties properties;
    private RegistrationCompensationService compensationService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        properties = new KeycloakProperties();
        properties.getRegistration().getCompensation().setMaxAttempts(3);
        compensationService = new RegistrationCompensationService(keycloakAdminClient, properties);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Успешное удаление сразу: очередь повторов остаётся пустой")
    void deletesImmediatelyAndDoesNotQueueRetry() {
        compensationService.compensate(userId, USERNAME);

        verify(keycloakAdminClient).deleteUser(userId);
        assertThat(compensationService.pendingCount()).isZero();
    }

    @Test
    @DisplayName("Keycloak недоступен: задача попадает в очередь повторов")
    void queuesRetryWhenImmediateDeletionFails() {
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(userId);

        compensationService.compensate(userId, USERNAME);

        assertThat(compensationService.pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Фоновый повтор доводит удаление до конца и очищает очередь")
    void retrySucceedsOnSecondAttempt() {
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .doNothing()
                .when(keycloakAdminClient).deleteUser(userId);

        compensationService.compensate(userId, USERNAME);
        assertThat(compensationService.pendingCount()).isEqualTo(1);

        compensationService.retryPendingDeletions();

        verify(keycloakAdminClient, times(2)).deleteUser(userId);
        assertThat(compensationService.pendingCount()).isZero();
    }

    @Test
    @DisplayName("Задача возвращается в очередь, пока не исчерпан лимит попыток")
    void keepsTaskQueuedUntilAttemptLimitIsReached() {
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(userId);

        // Попытка 1 — немедленная, выполняется прямо в compensate
        compensationService.compensate(userId, USERNAME);
        assertThat(compensationService.pendingCount()).isEqualTo(1);

        // Попытка 2 из 3: лимит ещё не исчерпан, задача остаётся в очереди
        compensationService.retryPendingDeletions();
        assertThat(compensationService.pendingCount()).isEqualTo(1);

        // Попытка 3 из 3: лимит исчерпан, задача снимается с повторов
        compensationService.retryPendingDeletions();
        assertThat(compensationService.pendingCount()).isZero();

        // Следующий проход планировщика уже не трогает Keycloak
        compensationService.retryPendingDeletions();

        verify(keycloakAdminClient, times(3)).deleteUser(userId);
    }

    @Test
    @DisplayName("При max-attempts=1 повторов нет: задача в очередь не попадает")
    void doesNotQueueRetryWhenRetriesAreDisabled() {
        properties.getRegistration().getCompensation().setMaxAttempts(1);
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(userId);

        compensationService.compensate(userId, USERNAME);

        assertThat(compensationService.pendingCount()).isZero();
        verify(keycloakAdminClient, times(1)).deleteUser(userId);
    }

    @Test
    @DisplayName("Пустая очередь не вызывает Keycloak")
    void doesNothingWhenQueueIsEmpty() {
        compensationService.retryPendingDeletions();

        verify(keycloakAdminClient, times(0)).deleteUser(userId);
    }

    @Test
    @DisplayName("Компенсации нескольких пользователей обрабатываются независимо")
    void processesSeveralPendingTasks() {
        UUID secondUserId = UUID.randomUUID();
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(userId);
        doThrow(new KeycloakUnavailableException("Keycloak недоступен"))
                .when(keycloakAdminClient).deleteUser(secondUserId);

        compensationService.compensate(userId, USERNAME);
        compensationService.compensate(secondUserId, "jane_doe");
        assertThat(compensationService.pendingCount()).isEqualTo(2);

        doNothing().when(keycloakAdminClient).deleteUser(userId);
        compensationService.retryPendingDeletions();

        // Первый пользователь удалён, второй остался ждать следующего прохода
        assertThat(compensationService.pendingCount()).isEqualTo(1);
    }
}
