package com.cor.collectorservice.util.rate;

import com.cor.collectorservice.util.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TokenBucketRateLimiter();
        rateLimiter.reset();
    }

    @Test
    void capacityIsHundredRequests() {
        assertThat(rateLimiter.getCapacity()).isEqualTo(100L);
        assertThat(rateLimiter.getAvailableTokens()).isEqualTo(100L);
    }

    @Test
    void acquireConsumesTokens() {
        long before = rateLimiter.getAvailableTokens();
        rateLimiter.acquire();
        assertThat(rateLimiter.getAvailableTokens()).isEqualTo(before - 1);
    }

    @Test
    void tryAcquireThrowsWhenBucketIsEmpty() {
        rateLimiter.tryAcquire(100);
        assertThatThrownBy(() -> rateLimiter.tryAcquire())
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void acquireBlocksAndQueuesWhenBucketIsEmpty() {
        // Полностью опустошаем ведро
        rateLimiter.tryAcquire(100);
        assertThat(rateLimiter.getAvailableTokens()).isZero();

        long start = System.currentTimeMillis();
        // Блокирующий вызов должен подождать пополнения, а не бросить исключение
        rateLimiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        // При скорости ~1.67 токена/сек ожидание одного токена ≈ 600 мс
        assertThat(elapsed).isGreaterThanOrEqualTo(400L);
    }

    @Test
    void sharedLimiterServesConcurrentUsersUnderSingleLimit() throws Exception {
        // Все пользователи используют один и тот же лимитер (единый лимит)
        int users = 8;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        AtomicInteger acquired = new AtomicInteger();

        Future<?>[] futures = new Future<?>[users];
        for (int i = 0; i < users; i++) {
            futures[i] = pool.submit(() -> {
                rateLimiter.acquire();
                acquired.incrementAndGet();
            });
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(acquired.get()).isEqualTo(users);
        // Из 100 токенов было потрачено ровно по числу пользователей
        assertThat(rateLimiter.getAvailableTokens()).isEqualTo(100L - users);
    }
}
