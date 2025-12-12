package com.example.pubg.dto;

import java.util.List;

public class PlayerFeedbackResponse {
    private String accountId;
    private List<FeedbackCard> cards;
    private String narrative;

    public PlayerFeedbackResponse(String accountId, List<FeedbackCard> cards, String narrative) {
        this.accountId = accountId;
        this.cards = cards;
        this.narrative = narrative;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<FeedbackCard> getCards() {
        return cards;
    }

    public String getNarrative() {
        return narrative;
    }
}
