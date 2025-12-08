package com.example.pubg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.pubg.client.PubgApiClient;
import com.example.pubg.client.TelemetryClient;
import com.example.pubg.dto.BenchmarkRequest;
import com.example.pubg.dto.IngestRequest;
import com.example.pubg.dto.MatchMeta;
import com.example.pubg.dto.PlayerFeedbackResponse;
import com.example.pubg.repository.ProDistroRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public class PipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("pubg")
            .withUsername("pubg")
            .withPassword("pubg");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @MockBean
    private PubgApiClient pubgApiClient;

    @MockBean
    private TelemetryClient telemetryClient;

    @Autowired
    private IngestService ingestService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private BenchmarkService benchmarkService;

    @Autowired
    private ProDistroRepository proDistroRepository;

    private List<Map<String, Object>> telemetry;

    @DynamicPropertySource
    static void configureProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setup() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Path path = Path.of("src/test/resources/fixtures/telemetry_sample.json");
        telemetry = mapper.readValue(Files.readString(path), new TypeReference<>() {
        });

        when(pubgApiClient.findAccountId("Test")).thenReturn("test-account");
        when(pubgApiClient.fetchRecentMatches(anyString(), anyInt()))
                .thenReturn(List.of(new MatchMeta("match1", "url1", "squad", 600)));
        when(pubgApiClient.fetchMatchMeta(anyString()))
                .thenReturn(new MatchMeta("match1", "url1", "squad", 600));
        when(pubgApiClient.fetchLeaderboardAccountIds(anyString(), anyInt()))
                .thenReturn(List.of("test-account"));
        when(telemetryClient.fetchTelemetry("url1")).thenReturn(telemetry);
    }

    @Test
    void ingestAndFeedbackFlow() {
        IngestRequest req = new IngestRequest();
        req.setNickname("Test");
        req.setMatchCount(1);
        ingestService.ingestRecentMatches(req);

        PlayerFeedbackResponse feedback = feedbackService.buildFeedback("test-account");
        assertThat(feedback.getCards()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void benchmarkRefreshCreatesDistros() {
        BenchmarkRequest request = new BenchmarkRequest();
        request.setMode("squad");
        request.setLeaderboardSize(1);
        request.setSamplePerPlayer(1);

        var distros = benchmarkService.refresh(request);
        assertThat(distros).isNotEmpty();
        assertThat(proDistroRepository.findAll()).isNotEmpty();
    }
}
