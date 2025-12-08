package com.example.pubg.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pro_distros")
public class ProDistro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String metricKey;

    private double p25;
    private double p50;
    private double p75;
    private double mean;
    private double std;

    @Column(nullable = false)
    private Instant updatedAt;

    public ProDistro() {
    }

    public ProDistro(String metricKey, double p25, double p50, double p75, double mean, double std, Instant updatedAt) {
        this.metricKey = metricKey;
        this.p25 = p25;
        this.p50 = p50;
        this.p75 = p75;
        this.mean = mean;
        this.std = std;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public double getP25() {
        return p25;
    }

    public void setP25(double p25) {
        this.p25 = p25;
    }

    public double getP50() {
        return p50;
    }

    public void setP50(double p50) {
        this.p50 = p50;
    }

    public double getP75() {
        return p75;
    }

    public void setP75(double p75) {
        this.p75 = p75;
    }

    public double getMean() {
        return mean;
    }

    public void setMean(double mean) {
        this.mean = mean;
    }

    public double getStd() {
        return std;
    }

    public void setStd(double std) {
        this.std = std;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
