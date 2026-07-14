package com.cor.collectorservice.util.rate;

import com.cor.collectorservice.util.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Rate Limiter с алгоритмом Token Bucket (ведро с токенами)
 * Позволяет плавно распределять запросы во времени с возможностью кратковременных всплесков
 * Идеально подходит для асинхронной обработки с контролем 100 запросов/минуту
 */
@Slf4j
@Component
public class TokenBucketRateLimiter {

    // Вместимость ведра (максимальное количество токенов)
    private final long capacity = 100;

    // Скорость пополнения токенов (токенов в секунду)
    // 100 токенов в минуту = 100/60 ≈ 1.67 токена в секунду
    private final double refillRate = 100.0 / 60.0;

    // Минимальное время сна между попытками получить токен в блокирующем режиме (мс)
    private static final long MIN_WAIT_MILLIS = 50;

    // Текущее количество токенов в ведре
    private final AtomicLong tokens = new AtomicLong(capacity);

    // Время последнего пополнения токенов (в миллисекундах)
    private final AtomicLong lastRefillTimestamp = new AtomicLong(System.currentTimeMillis());

    // Блокировка для атомарных операций пополнения
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Блокирующее получение одного токена.
     * Если токенов нет, поток ожидает (встаёт в очередь) до тех пор,
     * пока токен не станет доступен. Используется для единого лимита
     * 100 запросов/минуту, общего для всех пользователей приложения.
     */
    public void acquire() {
        acquire(1);
    }

    /**
     * Блокирующее получение указанного количества токенов.
     * В отличие от {@link #tryAcquire(int)}, метод не бросает исключение,
     * а ожидает пополнения ведра, тем самым выстраивая запросы в очередь.
     * @param tokenCount количество токенов
     */
    public void acquire(int tokenCount) {
        while (true) {
            long waitTimeMs;
            lock.lock();
            try {
                refill();

                if (tokens.get() >= tokenCount) {
                    tokens.addAndGet(-tokenCount);
                    log.debug("Токен получен (блокирующий режим). Осталось токенов: {}/{}", tokens.get(), capacity);
                    return;
                }

                waitTimeMs = calculateWaitTime(tokenCount);
                log.debug("Токены недоступны, ожидание {} мс. Нужно: {}, Доступно: {}",
                        waitTimeMs, tokenCount, tokens.get());
            } finally {
                lock.unlock();
            }

            try {
                Thread.sleep(Math.max(waitTimeMs, MIN_WAIT_MILLIS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateLimitExceededException("Ожидание освобождения токена было прервано", e);
            }
        }
    }

    /**
     * Пытается получить токен для выполнения запроса
     * @throws RateLimitExceededException если токены недоступны
     */
    public void tryAcquire() throws RateLimitExceededException {
        tryAcquire(1);
    }

    /**
     * Пытается получить указанное количество токенов
     * @param tokenCount количество токенов
     * @throws RateLimitExceededException если токены недоступны
     */
    public void tryAcquire(int tokenCount) throws RateLimitExceededException {
        lock.lock();
        try {
            refill();

            if (tokens.get() >= tokenCount) {
                tokens.addAndGet(-tokenCount);
                log.debug("Токен получен. Осталось токенов: {}/{}", tokens.get(), capacity);
            } else {
                long waitTime = calculateWaitTime(tokenCount);
                log.warn("Токены недоступны. Нужно: {}, Доступно: {}. Ожидание: {} мс",
                        tokenCount, tokens.get(), waitTime);
                throw new RateLimitExceededException(
                        String.format("Превышен лимит запросов. Необходимо подождать %d мс", waitTime)
                );
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Пополняет ведро токенами на основе прошедшего времени
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long lastRefill = lastRefillTimestamp.get();
        long elapsedTime = now - lastRefill;

        if (elapsedTime > 0) {
            // Вычисляем количество токенов для добавления
            double tokensToAdd = (elapsedTime / 1000.0) * refillRate;
            long newTokens = (long) Math.min(tokensToAdd, capacity - tokens.get());

            if (newTokens > 0) {
                tokens.addAndGet(newTokens);
                lastRefillTimestamp.set(now);
                log.trace("Пополнено {} токенов. Текущее количество: {}/{}", newTokens, tokens.get(), capacity);
            }
        }
    }

    /**
     * Вычисляет время ожидания до накопления нужного количества токенов
     * @param tokenCount нужное количество токенов
     * @return время ожидания в миллисекундах
     */
    private long calculateWaitTime(int tokenCount) {
        long currentTokens = tokens.get();
        long tokensNeeded = tokenCount - currentTokens;

        if (tokensNeeded <= 0) {
            return 0;
        }

        // Время в миллисекундах = (нужные токены / скорость пополнения) * 1000
        double waitTimeSeconds = tokensNeeded / refillRate;
        return (long) (waitTimeSeconds * 1000);
    }

    /**
     * Получает текущее количество токенов
     * @return количество токенов
     */
    public long getAvailableTokens() {
        lock.lock();
        try {
            refill();
            return tokens.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Получает вместимость ведра
     * @return вместимость
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Сбрасывает состояние rate limiter (для тестирования)
     */
    public void reset() {
        lock.lock();
        try {
            tokens.set(capacity);
            lastRefillTimestamp.set(System.currentTimeMillis());
            log.warn("Token Bucket Rate Limiter сброшен");
        } finally {
            lock.unlock();
        }
    }
}
