package com.example.pubg.dto;

public class FeedbackCard {
    private String category;
    private String metricKey;
    private double value;
    private double percentile;
    private double zScore;
    private String message;

    public FeedbackCard(String category, String metricKey, double value, double percentile, double zScore, String message) {
        this.category = category;
        this.metricKey = metricKey;
        this.value = value;
        this.percentile = percentile;
        this.zScore = zScore;
        this.message = message;
    }

    public String getCategory() {
        return category;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public double getValue() {
        return value;
    }

    public double getPercentile() {
        return percentile;
    }

    public double getZScore() {
        return zScore;
    }

    public String getMessage() {
        return message;
    }
}
