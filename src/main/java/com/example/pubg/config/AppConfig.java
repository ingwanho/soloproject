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
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.web.client.RestTemplate;

import com.example.pubg.util.SimpleRateLimiter;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;

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
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(5))
                .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .disableCookieManagement()
                .setDefaultRequestConfig(requestConfig)
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            request.getHeaders().add("Authorization", "Bearer " + apiKey);
            // PUBG API는 GET 요청에서 Content-Type을 강제 설정하면 415를 줄 수 있다.
            request.getHeaders().setAccept(Collections.singletonList(
                    org.springframework.http.MediaType.parseMediaType("application/vnd.api+json")));
            return execution.execute(request, body);
        };
        return builder
                .rootUri(apiHost)
                .requestFactory(() -> requestFactory)
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
