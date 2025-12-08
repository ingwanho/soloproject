package com.example.pubg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class BenchmarkRequest {
    @NotBlank
    private String mode;

    @Min(1)
    @Max(100)
    private int leaderboardSize = 50;

    @Min(1)
    @Max(50)
    private int samplePerPlayer = 20;

    public BenchmarkRequest() {
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getLeaderboardSize() {
        return leaderboardSize;
    }

    public void setLeaderboardSize(int leaderboardSize) {
        this.leaderboardSize = leaderboardSize;
    }

    public int getSamplePerPlayer() {
        return samplePerPlayer;
    }

    public void setSamplePerPlayer(int samplePerPlayer) {
        this.samplePerPlayer = samplePerPlayer;
    }
}
