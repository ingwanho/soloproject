package com.example.pubg.dto;

import java.time.Instant;

public class ProDistroDto {
    private String metricKey;
    private double p25;
    private double p50;
    private double p75;
    private double mean;
    private double std;
    private Instant updatedAt;

    public ProDistroDto(String metricKey, double p25, double p50, double p75, double mean, double std, Instant updatedAt) {
        this.metricKey = metricKey;
        this.p25 = p25;
        this.p50 = p50;
        this.p75 = p75;
        this.mean = mean;
        this.std = std;
        this.updatedAt = updatedAt;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public double getP25() {
        return p25;
    }

    public double getP50() {
        return p50;
    }

    public double getP75() {
        return p75;
    }

    public double getMean() {
        return mean;
    }

    public double getStd() {
        return std;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
