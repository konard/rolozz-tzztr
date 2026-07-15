package com.cor.collectorservice.client;

import com.cor.collectorservice.dto.wb.WbCardsResponse;
import com.cor.collectorservice.util.rate.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class WbApiClientQueueTest {

    @Test
    void concurrentFetchesAreSerializedByTheSharedSemaphore() throws Exception {
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter();
        Semaphore semaphore = new Semaphore(1, true);
        WbApiClient client = spy(new WbApiClient(mock(WebClient.class), rateLimiter, semaphore));

        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        // Каждый вызов страницы имитирует работу и фиксирует пиковую параллельность,
        // затем возвращает пустой ответ, чтобы пагинация остановилась после первой страницы.
        doAnswer(invocation -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            Thread.sleep(100);
            concurrent.decrementAndGet();
            return new WbCardsResponse();
        }).when(client).fetchCardsPage(anyString(), anyInt());

        int users = 5;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        Future<?>[] futures = new Future<?>[users];
        for (int i = 0; i < users; i++) {
            futures[i] = pool.submit(() -> client.fetchAllCards("token"));
        }
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Благодаря семафору одновременно выполняется не более одной загрузки
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }
}
