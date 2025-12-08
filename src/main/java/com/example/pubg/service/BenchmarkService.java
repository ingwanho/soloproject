package com.example.pubg.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.pubg.client.PubgApiClient;
import com.example.pubg.client.TelemetryClient;
import com.example.pubg.dto.BenchmarkRequest;
import com.example.pubg.dto.FeatureAggregate;
import com.example.pubg.dto.MatchMeta;
import com.example.pubg.dto.ProDistroDto;
import com.example.pubg.entity.ProDistro;
import com.example.pubg.repository.ProDistroRepository;

@Service
public class BenchmarkService {
    private final PubgApiClient pubgApiClient;
    private final TelemetryClient telemetryClient;
    private final FeatureService featureService;
    private final ProDistroRepository proDistroRepository;

    public BenchmarkService(PubgApiClient pubgApiClient, TelemetryClient telemetryClient,
            FeatureService featureService, ProDistroRepository proDistroRepository) {
        this.pubgApiClient = pubgApiClient;
        this.telemetryClient = telemetryClient;
        this.featureService = featureService;
        this.proDistroRepository = proDistroRepository;
    }

    @Transactional
    public List<ProDistroDto> refresh(BenchmarkRequest request) {
        List<String> accountIds = pubgApiClient.fetchLeaderboardAccountIds(request.getMode(), request.getLeaderboardSize());

        Map<String, List<Double>> metricBuckets = new HashMap<>();
        for (String accountId : accountIds) {
            List<MatchMeta> matches = pubgApiClient.fetchRecentMatches(accountId, request.getSamplePerPlayer());
            for (MatchMeta meta : matches) {
                List<Map<String, Object>> telemetry = telemetryClient.fetchTelemetry(meta.telemetryUrl());
                FeatureAggregate agg = featureService.computeFeatures(accountId, meta, telemetry);
                merge(metricBuckets, agg.getPhaseMetrics());
                merge(metricBuckets, agg.getCombatMetrics());
                merge(metricBuckets, agg.getGrenadeMetrics());
            }
        }
        List<ProDistroDto> dtos = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : metricBuckets.entrySet()) {
            ProDistro distro = toDistro(entry.getKey(), entry.getValue());
            proDistroRepository.findByMetricKey(entry.getKey())
                    .ifPresentOrElse(existing -> update(existing, distro), () -> proDistroRepository.save(distro));
            dtos.add(toDto(distro));
        }
        return dtos;
    }

    private void merge(Map<String, List<Double>> bucket, Map<String, Double> metrics) {
        metrics.forEach((k, v) -> bucket.computeIfAbsent(k, key -> new ArrayList<>()).add(v));
    }

    private ProDistro toDistro(String key, List<Double> values) {
        DescriptiveStatistics stats = new DescriptiveStatistics(values.stream().mapToDouble(Double::doubleValue).toArray());
        double p25 = stats.getPercentile(25);
        double p50 = stats.getPercentile(50);
        double p75 = stats.getPercentile(75);
        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        return new ProDistro(key, p25, p50, p75, mean, std, Instant.now());
    }

    private void update(ProDistro target, ProDistro source) {
        target.setP25(source.getP25());
        target.setP50(source.getP50());
        target.setP75(source.getP75());
        target.setMean(source.getMean());
        target.setStd(source.getStd());
        target.setUpdatedAt(source.getUpdatedAt());
    }

    private ProDistroDto toDto(ProDistro distro) {
        return new ProDistroDto(distro.getMetricKey(), distro.getP25(), distro.getP50(), distro.getP75(),
                distro.getMean(), distro.getStd(), distro.getUpdatedAt());
    }
}
