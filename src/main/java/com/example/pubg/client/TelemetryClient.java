package com.example.pubg.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.pubg.util.SimpleRateLimiter;

@Component
public class TelemetryClient {
    private static final Logger log = LoggerFactory.getLogger(TelemetryClient.class);

    private final RestTemplate restTemplate;
    private final SimpleRateLimiter rateLimiter;
    private final Duration backoff;

    public TelemetryClient(RestTemplate restTemplate, SimpleRateLimiter rateLimiter,
            @org.springframework.beans.factory.annotation.Value("${pubg.retry.backoff-ms}") long backoffMs) {
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
        this.backoff = Duration.ofMillis(backoffMs);
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttemptsExpression = "${pubg.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${pubg.retry.backoff-ms}"))
    @Cacheable(value = "telemetry", key = "#telemetryUrl")
    public List<Map<String, Object>> fetchTelemetry(String telemetryUrl) {
        return executeWithRateLimit(() -> {
            ResponseEntity<List> response = restTemplate.exchange(telemetryUrl, HttpMethod.GET, null, List.class);
            return response.getBody();
        });
    }

    private <T> T executeWithRateLimit(Supplier<T> supplier) {
        try {
            rateLimiter.acquire(backoff);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PubgApiException("Interrupted while waiting for rate limit", e);
        }
    }

    @Recover
    public <T> T onFailure(RestClientException ex) {
        throw new PubgApiException("Telemetry fetch failed after retries", ex);
    }
}
