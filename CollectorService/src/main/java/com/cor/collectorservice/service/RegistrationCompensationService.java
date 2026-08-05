package com.cor.collectorservice.service;

import com.cor.collectorservice.client.KeycloakAdminClient;
import com.cor.collectorservice.configs.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Компенсация незавершённой регистрации: удаление «осиротевшей» учётной записи Keycloak.
 * <p>
 * Регистрация состоит из двух шагов — создание учётной записи в Keycloak и сохранение профиля
 * в базе данных сервиса. Если второй шаг откатился, учётная запись в Keycloak остаётся лишней:
 * пользователь смог бы войти, не имея профиля. Этот сервис её удаляет.
 * <p>
 * Порядок работы:
 * <ol>
 *     <li>{@link #compensate(UUID, String)} пытается удалить учётную запись немедленно
 *         (с повторными попытками resilience4j внутри {@link KeycloakAdminClient});</li>
 *     <li>если Keycloak недоступен, задача попадает в очередь;</li>
 *     <li>{@link #retryPendingDeletions()} по расписанию повторяет удаление до
 *         {@code keycloak.registration.compensation.max-attempts} раз.</li>
 * </ol>
 * Очередь хранится в памяти: запись в базу здесь неуместна, поскольку типичная причина
 * компенсации — как раз недоступность базы данных. Задачи, не выполненные до перезапуска
 * сервиса, логируются с уровнем {@code ERROR} и требуют ручной очистки в Keycloak.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationCompensationService {

    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties properties;

    /**
     * Очередь незавершённых компенсаций. Потокобезопасна: задачи добавляются потоками обработки
     * запросов, а разбираются планировщиком.
     */
    private final Queue<PendingDeletion> pendingDeletions = new ConcurrentLinkedQueue<>();

    /**
     * Немедленно удаляет учётную запись Keycloak, а при неудаче ставит задачу в очередь ретрая.
     *
     * @param keycloakUserId идентификатор учётной записи, созданной на первом шаге регистрации
     * @param username       логин пользователя (для логов)
     */
    public void compensate(UUID keycloakUserId, String username) {
        log.warn("Запуск компенсации регистрации пользователя {}: удаляем учётную запись {} из Keycloak",
                username, keycloakUserId);
        try {
            keycloakAdminClient.deleteUser(keycloakUserId);
            log.info("Компенсация регистрации пользователя {} выполнена: учётная запись {} удалена",
                    username, keycloakUserId);
        } catch (RuntimeException ex) {
            log.error("Не удалось сразу удалить учётную запись {} пользователя {}. "
                            + "Задача поставлена в очередь фоновых повторов",
                    keycloakUserId, username, ex);
            pendingDeletions.add(new PendingDeletion(keycloakUserId, username, 1));
        }
    }

    /**
     * Фоновый обработчик очереди компенсаций: повторяет удаление учётных записей,
     * которые не удалось удалить сразу.
     * <p>
     * Интервал задаётся свойством {@code keycloak.registration.compensation.retry-interval}
     * в формате ISO-8601 (например, {@code PT30S}).
     */
    @Scheduled(fixedDelayString = "${keycloak.registration.compensation.retry-interval:PT30S}")
    public void retryPendingDeletions() {
        if (pendingDeletions.isEmpty()) {
            return;
        }

        int maxAttempts = properties.getRegistration().getCompensation().getMaxAttempts();
        List<PendingDeletion> batch = drainQueue();
        log.info("Повтор компенсаций регистрации: задач в очереди {}", batch.size());

        for (PendingDeletion task : batch) {
            try {
                keycloakAdminClient.deleteUser(task.userId());
                log.info("Компенсация регистрации пользователя {} выполнена с попытки {}: учётная запись {} удалена",
                        task.username(), task.attempts(), task.userId());
            } catch (RuntimeException ex) {
                handleFailedAttempt(task, maxAttempts, ex);
            }
        }
    }

    /**
     * Возвращает количество задач, ожидающих повторного удаления.
     * Используется в тестах и при диагностике.
     *
     * @return размер очереди компенсаций
     */
    public int pendingCount() {
        return pendingDeletions.size();
    }

    /**
     * Забирает из очереди все накопившиеся задачи, не мешая параллельному добавлению новых.
     *
     * @return список задач для текущего прохода
     */
    private List<PendingDeletion> drainQueue() {
        List<PendingDeletion> batch = new ArrayList<>();
        PendingDeletion task;
        while ((task = pendingDeletions.poll()) != null) {
            batch.add(task);
        }
        return batch;
    }

    /**
     * Обрабатывает неудачную попытку: возвращает задачу в очередь либо отказывается от неё,
     * если лимит попыток исчерпан.
     *
     * @param task        задача компенсации
     * @param maxAttempts максимальное число попыток
     * @param ex          ошибка последней попытки
     */
    private void handleFailedAttempt(PendingDeletion task, int maxAttempts, RuntimeException ex) {
        int nextAttempt = task.attempts() + 1;

        if (nextAttempt > maxAttempts) {
            log.error("Компенсация регистрации пользователя {} не выполнена за {} попыток. "
                            + "Учётная запись {} осталась в Keycloak и требует ручного удаления",
                    task.username(), task.attempts(), task.userId(), ex);
            return;
        }

        log.warn("Попытка {} удалить учётную запись {} пользователя {} не удалась, задача возвращена в очередь",
                task.attempts(), task.userId(), task.username(), ex);
        pendingDeletions.add(new PendingDeletion(task.userId(), task.username(), nextAttempt));
    }

    /**
     * Задача отложенного удаления учётной записи Keycloak.
     *
     * @param userId   идентификатор учётной записи Keycloak
     * @param username логин пользователя (для логов)
     * @param attempts количество уже выполненных попыток удаления
     */
    public record PendingDeletion(UUID userId, String username, int attempts) {
    }
}
