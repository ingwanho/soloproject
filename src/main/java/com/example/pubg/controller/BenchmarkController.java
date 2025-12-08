package com.example.pubg.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.pubg.dto.BenchmarkRequest;
import com.example.pubg.dto.ProDistroDto;
import com.example.pubg.service.BenchmarkService;

@RestController
@RequestMapping("/v1/benchmarks")
public class BenchmarkController {
    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<List<ProDistroDto>> refresh(@Validated @RequestBody BenchmarkRequest request) {
        return ResponseEntity.ok(benchmarkService.refresh(request));
    }
}
