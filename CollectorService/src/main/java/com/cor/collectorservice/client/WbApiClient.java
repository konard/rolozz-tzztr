package com.cor.collectorservice.client;

import com.cor.collectorservice.dto.card.CardRequest;
import com.cor.collectorservice.dto.wb.WbCardsResponse;
import com.cor.collectorservice.util.exception.WbApiException;
import com.cor.collectorservice.util.exception.WbRateLimitException;
import com.cor.collectorservice.util.rate.TokenBucketRateLimiter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Slf4j
@Component
@RequiredArgsConstructor
public class WbApiClient {

    private final WebClient wbWebClient;

    // Общий для всех пользователей лимит: 100 запросов/минуту к WB API
    private final TokenBucketRateLimiter rateLimiter;

    // Единая очередь: пока идёт загрузка для одного пользователя, остальные ждут
    private final Semaphore wbApiSemaphore;

    @Value("${wb.api.content.cards-path}")
    private String cardsPath;

    @Value("${wb.api.content.max-page-size}")
    private int maxPageSize;

    @CircuitBreaker(name = "wbApiCircuitBreaker")
    @Retry(name = "wbApiRetry")
    @TimeLimiter(name = "wbApiTimeLimiter")
    @Bulkhead(name = "wbApiBulkhead")
    public WbCardsResponse fetchCardsPage(String token, int skip) {
        log.debug("Получение страницы карточек WB с пропуском: {}", skip);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> settings = new HashMap<>();

            Map<String, Object> cursor = new HashMap<>();
            cursor.put("limit", maxPageSize);

            Map<String, Object> filter = new HashMap<>();
            filter.put("withPhoto", -1);

            settings.put("cursor", cursor);
            settings.put("filter", filter);

            requestBody.put("settings", settings);

            log.debug("Тело запроса: {}", requestBody);

            return wbWebClient.method(HttpMethod.POST)
                    .uri(uriBuilder -> uriBuilder
                            .path(cardsPath)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> {
                                log.error("Ошибка WB API: {}", response.statusCode());
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("Тело ошибки WB API: {}", body);
                                            return reactor.core.publisher.Mono.error(
                                                    new WbApiException("Ошибка WB API: " + body)
                                            );
                                        });
                            })
                    .bodyToMono(WbCardsResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("Превышен лимит запросов. Повторить через: {}",
                    e.getHeaders().getFirst("Retry-After"));
            throw new WbRateLimitException("Превышен лимит запросов к Wildberries API", e);
        } catch (Exception e) {
            log.error("Ошибка при получении карточек WB с пропуском: {}", skip, e);
            throw new WbApiException("Не удалось получить карточки WB", e);
        }
    }

    public List<CardRequest> fetchAllCards(String token) {
        log.info("Начало получения всех карточек из Wildberries API");
        long startTime = System.currentTimeMillis();

        acquireQueueSlot();
        try {
            return fetchAllCardsInternal(token, startTime);
        } finally {
            wbApiSemaphore.release();
            log.debug("Слот очереди освобождён. Свободных слотов: {}", wbApiSemaphore.availablePermits());
        }
    }

    private List<CardRequest> fetchAllCardsInternal(String token, long startTime) {
        List<CardRequest> allCards = new ArrayList<>();
        int skip = 0;
        int pageCount = 0;

        try {
            while (true) {
                pageCount++;
                log.debug("Получение страницы {} с пропуском: {}", pageCount, skip);

                // Единый лимит 100 запросов/минуту для всех пользователей.
                // Если токенов нет — поток ждёт (не бросаем исключение).
                rateLimiter.acquire();

                WbCardsResponse response = fetchCardsPage(token, skip);

                if (shouldStopPagination(response)) {
                    log.info("Карточек больше не найдено, остановка пагинации на странице: {}", pageCount);
                    break;
                }

                allCards.addAll(response.getCards());
                logPageInfo(pageCount, response, allCards);

                if (isEndOfData(response, allCards)) {
                    break;
                }

                skip += maxPageSize;
            }

            log.info("Успешно получено {} карточек из WB API за {} страниц ({} мс)",
                    allCards.size(), pageCount, System.currentTimeMillis() - startTime);
            return allCards;

        } catch (Exception e) {
            log.error("Ошибка при получении карточек из WB API: {}", e.getMessage(), e);
            throw new WbApiException("Не удалось получить карточки из Wildberries API", e);
        }
    }

    /**
     * Встаёт в общую очередь на выполнение запросов к WB API.
     * Пока для одного пользователя выполняется загрузка карточек,
     * остальные ожидают освобождения слота.
     */
    private void acquireQueueSlot() {
        try {
            log.debug("Ожидание слота в очереди WB API. Свободных слотов: {}, ожидающих: {}",
                    wbApiSemaphore.availablePermits(), wbApiSemaphore.getQueueLength());
            wbApiSemaphore.acquire();
            log.debug("Слот очереди получен, начинаем загрузку карточек");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WbApiException("Ожидание в очереди на получение карточек было прервано", e);
        }
    }

    private boolean shouldStopPagination(WbCardsResponse response) {
        return response == null || response.getCards() == null || response.getCards().isEmpty();
    }

    private void logPageInfo(int pageCount, WbCardsResponse response, List<CardRequest> allCards) {
        Integer total = response.getCursor() != null ? response.getCursor().getTotal() : 0;
        log.info("Страница {} загружена: {} карточек ({} всего доступно, {} собрано)",
                pageCount, response.getCards().size(), total, allCards.size());
    }

    private boolean isEndOfData(WbCardsResponse response, List<CardRequest> allCards) {
        if (response.getCards().size() < maxPageSize) {
            log.info("Получено меньше размера страницы, предполагаем конец данных");
            return true;
        }

        Integer total = response.getCursor() != null ? response.getCursor().getTotal() : 0;
        if (total > 0 && allCards.size() >= total) {
            log.info("Все карточки собраны: {} из {}", allCards.size(), total);
            return true;
        }

        return false;
    }
}
