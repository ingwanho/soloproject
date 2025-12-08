package com.example.pubg.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pubg.dto.PlayerFeedbackResponse;
import com.example.pubg.service.FeedbackService;

@RestController
@RequestMapping("/v1/players")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/{accountId}/feedback")
    public ResponseEntity<PlayerFeedbackResponse> feedback(@PathVariable String accountId) {
        return ResponseEntity.ok(feedbackService.buildFeedback(accountId));
    }
}
