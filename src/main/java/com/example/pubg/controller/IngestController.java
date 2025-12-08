package com.example.pubg.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pubg.dto.IngestRequest;
import com.example.pubg.dto.IngestResponse;
import com.example.pubg.service.IngestService;

@RestController
@RequestMapping("/v1/ingest")
public class IngestController {
    private final IngestService ingestService;

    public IngestController(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Validated @RequestBody IngestRequest request) {
        return ResponseEntity.ok(ingestService.ingestRecentMatches(request));
    }
}
