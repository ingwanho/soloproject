package com.example.pubg.config;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.web.client.RestTemplate;

import com.example.pubg.util.SimpleRateLimiter;

@Configuration
@EnableCaching
public class AppConfig {

    @Value("${pubg.api-key}")
    private String apiKey;

    @Value("${pubg.api-host}")
    private String apiHost;

    @Value("${pubg.rate-limit.requests-per-second}")
    private int requestsPerSecond;

    @Bean
    public RestTemplate pubgRestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + apiKey);
            request.getHeaders().add("Accept", "application/vnd.api+json");
            return execution.execute(request, body);
        };
        return builder
                .rootUri(apiHost)
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(Collections.singletonList(authInterceptor))
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService scheduler() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    @Bean
    public SimpleRateLimiter pubgRateLimiter(ScheduledExecutorService scheduler) {
        SimpleRateLimiter limiter = new SimpleRateLimiter(requestsPerSecond);
        limiter.scheduleRefill(scheduler);
        return limiter;
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .disableCachingNullValues();
    }
}
