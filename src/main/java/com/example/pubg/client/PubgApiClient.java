package com.example.pubg.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.pubg.dto.MatchMeta;
import com.example.pubg.util.SimpleRateLimiter;

@Component
public class PubgApiClient {
    private static final Logger log = LoggerFactory.getLogger(PubgApiClient.class);

    private final RestTemplate restTemplate;
    private final SimpleRateLimiter rateLimiter;
    private final Duration backoff;
    private final int defaultMatchCount;
    private final String shard;

    public PubgApiClient(
            RestTemplate restTemplate,
            SimpleRateLimiter rateLimiter,
            @Value("${pubg.retry.backoff-ms}") long backoffMs,
            @Value("${pubg.ingest.default-match-count}") int defaultMatchCount,
            @Value("${pubg.shard:kakao}") String shard) {
        this.restTemplate = restTemplate;
        this.rateLimiter = rateLimiter;
        this.backoff = Duration.ofMillis(backoffMs);
        this.defaultMatchCount = defaultMatchCount;
        this.shard = shard;
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttemptsExpression = "${pubg.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${pubg.retry.backoff-ms}"))
    public String findAccountId(String nickname) {
        return executeWithRateLimit(() -> {
            String path = "/shards/" + shard + "/players?filter[playerNames]=" + nickname;
            ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data == null || data.isEmpty()) {
                throw new PubgApiException("No player found for nickname=" + nickname);
            }
            Map<String, Object> first = data.get(0);
            return (String) first.get("id");
        });
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttemptsExpression = "${pubg.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${pubg.retry.backoff-ms}"))
    public List<MatchMeta> fetchRecentMatches(String accountId, Integer requestedCount) {
        int count = Optional.ofNullable(requestedCount).orElse(defaultMatchCount);
        return executeWithRateLimit(() -> {
            String path = "/shards/" + shard + "/players/" + accountId;
            ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> relationships = (Map<String, Object>) data.get("relationships");
            Map<String, Object> matches = (Map<String, Object>) relationships.get("matches");
            List<Map<String, Object>> matchData = (List<Map<String, Object>>) matches.get("data");
            List<MatchMeta> metas = new ArrayList<>();
            for (int i = 0; i < Math.min(count, matchData.size()); i++) {
                String matchId = (String) matchData.get(i).get("id");
                metas.add(fetchMatchMeta(matchId));
            }
            return metas;
        });
    }

    @Retryable(
            retryFor = {RestClientException.class},
            maxAttemptsExpression = "${pubg.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${pubg.retry.backoff-ms}"))
    public MatchMeta fetchMatchMeta(String matchId) {
        return executeWithRateLimit(() -> {
            String path = "/shards/" + shard + "/matches/" + matchId;
            ResponseEntity<Map> response = restTemplate.exchange(path, HttpMethod.GET, HttpEntity.EMPTY, Map.class);
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
            String mode = (String) attributes.getOrDefault("gameMode", "squad");
            Number duration = (Number) attributes.getOrDefault("duration", 0);

            List<Map<String, Object>> included = (List<Map<String, Object>>) response.getBody().get("included");
            String telemetryUrl = included.stream()
                    .filter(it -> "asset".equals(it.get("type")))
                    .map(it -> (Map<String, Object>) it.get("attributes"))
                    .map(attr -> (String) attr.get("URL"))
                    .findFirst()
                    .orElseThrow(() -> new PubgApiException("Telemetry URL not found for match=" + matchId));
            return new MatchMeta(matchId, telemetryUrl, mode, duration.longValue());
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
        throw new PubgApiException("PUBG API failed after retries", ex);
    }

    /**
     * Placeholder for leaderboard collection. PUBG API exposes leaderboards per mode/platform.
     * Wire actual API path when credentials are available.
     */
    public List<String> fetchLeaderboardAccountIds(String mode, int size) {
        log.warn("fetchLeaderboardAccountIds is returning empty list (stub). Wire actual PUBG leaderboard endpoint.");
        return List.of();
    }

    public String getShard() {
        return shard;
    }
}
