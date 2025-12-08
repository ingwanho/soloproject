package com.example.pubg.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.pubg.entity.ProDistro;

public interface ProDistroRepository extends JpaRepository<ProDistro, Long> {
    Optional<ProDistro> findByMetricKey(String metricKey);
}
