package com.cor.collectorservice.client;

import com.cor.collectorservice.dto.card.CardRequest;
import com.cor.collectorservice.dto.wb.WbCardsResponse;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class WbApiClient {

    private final WebClient wbWebClient;

    @Value("${wb.api.content.cards-path}")
    private String cardsPath;

    @Value("${wb.api.content.max-page-size:100}")
    private int maxPageSize;

    @CircuitBreaker(name = "wbApiCircuitBreaker")
    @Retry(name = "wbApiRetry")
    @TimeLimiter(name = "wbApiTimeLimiter")
    @Bulkhead(name = "wbApiBulkhead")
    public WbCardsResponse fetchCardsPage(String token, int skip) {
        log.debug("Fetching WB cards page with skip: {}", skip);

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

            log.debug("Request body: {}", requestBody);

            return wbWebClient.method(HttpMethod.POST)
                    .uri(uriBuilder -> uriBuilder
                            .path(cardsPath)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> {
                                log.error("WB API error: {}", response.statusCode());
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("WB API error body: {}", body);
                                            return reactor.core.publisher.Mono.error(
                                                    new RuntimeException("WB API error: " + body)
                                            );
                                        });
                            })
                    .bodyToMono(WbCardsResponse.class)
                    .block(Duration.ofSeconds(30));

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("Rate limit exceeded. Retry after: {}",
                    e.getHeaders().getFirst("Retry-After"));
            throw new RuntimeException("Wildberries API rate limit exceeded", e);
        } catch (Exception e) {
            log.error("Error fetching WB cards with skip: {}", skip, e);
            throw new RuntimeException("Failed to fetch WB cards", e);
        }
    }

    public List<CardRequest> fetchAllCards(String token) {
        log.info("Starting to fetch all cards from Wildberries API");
        long startTime = System.currentTimeMillis();

        List<CardRequest> allCards = new ArrayList<>();
        int skip = 0;
        int pageCount = 0;

        try {
            while (true) {
                pageCount++;
                log.debug("Fetching page {} with skip: {}", pageCount, skip);

                WbCardsResponse response = fetchCardsPage(token, skip);

                if (response == null || response.getCards() == null || response.getCards().isEmpty()) {
                    log.info("No more cards found, stopping pagination at page: {}", pageCount);
                    break;
                }

                allCards.addAll(response.getCards());

                Integer total = response.getCursor() != null ? response.getCursor().getTotal() : 0;
                log.info("Page {} loaded: {} cards ({} total available, {} collected)",
                        pageCount, response.getCards().size(), total, allCards.size());

                if (response.getCards().size() < maxPageSize) {
                    log.info("Received less than page size, assuming end of data");
                    break;
                }

                if (total > 0 && allCards.size() >= total) {
                    log.info("All cards collected: {} from {}", allCards.size(), total);
                    break;
                }

                skip += maxPageSize;

                try {
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Sleep interrupted", e);
                }
            }

            log.info("Successfully fetched {} cards from WB API in {} pages ({} ms)",
                    allCards.size(), pageCount, System.currentTimeMillis() - startTime);
            return allCards;

        } catch (Exception e) {
            log.error("Error fetching cards from WB API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch cards from Wildberries API", e);
        }
    }
}
