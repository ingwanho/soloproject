package com.example.pubg.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pubg.client.PubgApiClient;
import com.example.pubg.client.TelemetryClient;
import com.example.pubg.dto.FeatureAggregate;
import com.example.pubg.dto.IngestRequest;
import com.example.pubg.dto.IngestResponse;
import com.example.pubg.dto.MatchMeta;

@Service
public class IngestService {
    private static final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final PubgApiClient pubgApiClient;
    private final TelemetryClient telemetryClient;
    private final FeatureService featureService;
    private final UserFeatureStore store;

    public IngestService(PubgApiClient pubgApiClient, TelemetryClient telemetryClient, FeatureService featureService,
            UserFeatureStore store) {
        this.pubgApiClient = pubgApiClient;
        this.telemetryClient = telemetryClient;
        this.featureService = featureService;
        this.store = store;
    }

    @Transactional
    public IngestResponse ingestRecentMatches(IngestRequest request) {
        String accountId = pubgApiClient.findAccountId(request.getNickname());
        List<MatchMeta> metas = pubgApiClient.fetchRecentMatches(accountId, request.getMatchCount());
        log.info("Found {} recent matches for accountId={}", metas.size(), accountId);
        FeatureAggregate aggregate = null;
        List<String> processedMatches = new ArrayList<>();
        log.info("1");
        for (MatchMeta meta : metas) {
            try {
                log.info("Fetching telemetry for matchId={} url={}", meta.matchId(), meta.telemetryUrl());
                List<Map<String, Object>> telemetry = telemetryClient.fetchTelemetry(meta.telemetryUrl());
                FeatureAggregate features = featureService.computeFeatures(accountId, meta, telemetry);
                aggregate = aggregate == null ? features : aggregate.merge(features);
                processedMatches.add(meta.matchId());
                log.info("Processed matchId={}", meta.matchId());
            } catch (Exception e) {
                log.error("Telemetry fetch failed for matchId={} url={}", meta.matchId(), meta.telemetryUrl(), e);
                throw e;
            }
        }
        if (aggregate != null) {
            store.put(accountId, aggregate);
        }
        return new IngestResponse(accountId, processedMatches);
    }
}
