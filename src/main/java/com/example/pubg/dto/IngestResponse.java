package com.example.pubg.dto;

import java.util.List;

public class IngestResponse {
    private String accountId;
    private List<String> matchIds;

    public IngestResponse(String accountId, List<String> matchIds) {
        this.accountId = accountId;
        this.matchIds = matchIds;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<String> getMatchIds() {
        return matchIds;
    }
}
