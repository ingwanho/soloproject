package com.example.pubg.dto;

import java.util.List;

public class PlayerFeedbackResponse {
    private String accountId;
    private List<FeedbackCard> cards;

    public PlayerFeedbackResponse(String accountId, List<FeedbackCard> cards) {
        this.accountId = accountId;
        this.cards = cards;
    }

    public String getAccountId() {
        return accountId;
    }

    public List<FeedbackCard> getCards() {
        return cards;
    }
}
