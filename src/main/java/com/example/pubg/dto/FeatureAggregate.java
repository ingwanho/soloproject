package com.example.pubg.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FeatureAggregate {
    private final Map<String, Double> phaseMetrics;
    private final Map<String, Double> combatMetrics;
    private final Map<String, Double> grenadeMetrics;

    public FeatureAggregate(Map<String, Double> phaseMetrics, Map<String, Double> combatMetrics,
            Map<String, Double> grenadeMetrics) {
        this.phaseMetrics = Collections.unmodifiableMap(new HashMap<>(phaseMetrics));
        this.combatMetrics = Collections.unmodifiableMap(new HashMap<>(combatMetrics));
        this.grenadeMetrics = Collections.unmodifiableMap(new HashMap<>(grenadeMetrics));
    }

    public Map<String, Double> getPhaseMetrics() {
        return phaseMetrics;
    }

    public Map<String, Double> getCombatMetrics() {
        return combatMetrics;
    }

    public Map<String, Double> getGrenadeMetrics() {
        return grenadeMetrics;
    }

    public FeatureAggregate merge(FeatureAggregate other) {
        Map<String, Double> mergedPhase = averageMaps(this.phaseMetrics, other.phaseMetrics);
        Map<String, Double> mergedCombat = averageMaps(this.combatMetrics, other.combatMetrics);
        Map<String, Double> mergedGrenade = averageMaps(this.grenadeMetrics, other.grenadeMetrics);
        return new FeatureAggregate(mergedPhase, mergedCombat, mergedGrenade);
    }

    private Map<String, Double> averageMaps(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> result = new HashMap<>(a);
        b.forEach((k, v) -> result.merge(k, v, (v1, v2) -> (v1 + v2) / 2.0));
        return result;
    }
}
