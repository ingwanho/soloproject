package com.example.pubg.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.pubg.dto.FeatureAggregate;

@Component
public class UserFeatureStore {
    private final Map<String, FeatureAggregate> features = new ConcurrentHashMap<>();

    public void put(String accountId, FeatureAggregate aggregate) {
        features.put(accountId, aggregate);
    }

    public FeatureAggregate get(String accountId) {
        return features.get(accountId);
    }
}
