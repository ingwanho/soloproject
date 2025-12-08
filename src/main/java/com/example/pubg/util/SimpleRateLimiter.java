package com.example.pubg.util;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight token bucket limiter for small workloads.
 */
public class SimpleRateLimiter {
    private final Semaphore tokens;
    private final int capacity;

    public SimpleRateLimiter(int permitsPerSecond) {
        this.capacity = Math.max(1, permitsPerSecond);
        this.tokens = new Semaphore(this.capacity, true);
    }

    public void scheduleRefill(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(() -> {
            int missing = capacity - tokens.availablePermits();
            if (missing > 0) {
                tokens.release(missing);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void acquire(Duration timeout) throws InterruptedException {
        boolean acquired = tokens.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new IllegalStateException("Rate limit exceeded");
        }
    }
}
