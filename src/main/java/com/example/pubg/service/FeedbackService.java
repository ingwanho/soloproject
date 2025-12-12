package com.example.pubg.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.pubg.dto.FeatureAggregate;
import com.example.pubg.dto.FeedbackCard;
import com.example.pubg.dto.PlayerFeedbackResponse;
import com.example.pubg.entity.ProDistro;
import com.example.pubg.repository.ProDistroRepository;

@Service
public class FeedbackService {
    private final UserFeatureStore store;
    private final ProDistroRepository proDistroRepository;

    public FeedbackService(UserFeatureStore store, ProDistroRepository proDistroRepository) {
        this.store = store;
        this.proDistroRepository = proDistroRepository;
    }

    public PlayerFeedbackResponse buildFeedback(String accountId) {
        FeatureAggregate aggregate = store.get(accountId);
        if (aggregate == null) {
            throw new IllegalStateException("No cached features for accountId=" + accountId
                    + ". Run ingest first.");
        }

        List<FeedbackCard> cards = new ArrayList<>();
        cards.add(pickBest(aggregate.getPhaseMetrics(), "phase"));
        cards.add(pickBest(aggregate.getCombatMetrics(), "combat"));
        cards.add(pickBest(aggregate.getGrenadeMetrics(), "grenade"));

        cards.sort(Comparator.comparingDouble(FeedbackCard::getPercentile).reversed());
        String narrative = buildNarrative(cards);
        return new PlayerFeedbackResponse(accountId, cards, narrative);
    }

    private FeedbackCard pickBest(Map<String, Double> metrics, String category) {
        return metrics.entrySet().stream()
                .findFirst()
                .map(e -> toCard(category, e.getKey(), e.getValue()))
                .orElseThrow(() -> new IllegalStateException("No metrics found for " + category));
    }

    private FeedbackCard toCard(String category, String key, double value) {
        Optional<ProDistro> distro = proDistroRepository.findByMetricKey(key);
        double mean = distro.map(ProDistro::getMean).orElse(value);
        double std = distro.map(ProDistro::getStd).orElse(1.0);
        double p50 = distro.map(ProDistro::getP50).orElse(value);
        double p75 = distro.map(ProDistro::getP75).orElse(value);
        double p25 = distro.map(ProDistro::getP25).orElse(value);

        double z = std == 0 ? 0 : (value - mean) / std;
        double percentile = interpolatePercentile(value, p25, p50, p75);
        String message = template(category, key, value, percentile);
        return new FeedbackCard(category, key, value, percentile, z, message);
    }

    private double interpolatePercentile(double value, double p25, double p50, double p75) {
        if (value <= p25) {
            return 25 * value / Math.max(p25, 1e-6);
        }
        if (value <= p50) {
            return 25 + 25 * (value - p25) / Math.max(p50 - p25, 1e-6);
        }
        if (value <= p75) {
            return 50 + 25 * (value - p50) / Math.max(p75 - p50, 1e-6);
        }
        return Math.min(99, 75 + 25 * (value - p75) / Math.max(p75, 1e-6));
    }

    private String template(String category, String key, double value, double pct) {
        String scoreText = String.format("백분위 %.1f%%", pct);
        switch (category) {
            case "phase":
                return "페이즈 포지셔닝: " + readableKey(key) + " (" + scoreText + "). 다음 페이즈를 한 템포 앞서서 들어가 보세요.";
            case "combat":
                return "교전 품질: " + readableKey(key) + " (" + scoreText + "). 팀 각 벌리기와 동시 사격을 노려보세요.";
            case "grenade":
                return "투척 활용: " + readableKey(key) + " (" + scoreText + "). 첫 수류탄 타이밍을 앞당겨 푸시를 준비하세요.";
            default:
                return scoreText;
        }
    }

    private String buildNarrative(List<FeedbackCard> cards) {
        if (cards.isEmpty()) {
            return "데이터가 부족해 상위권 대비 피드백을 생성하지 못했습니다.";
        }
        FeedbackCard best = cards.get(0);
        FeedbackCard worst = cards.get(cards.size() - 1);

        String tendency = best.getPercentile() >= 60
                ? "강점: " + best.getCategory() + "에서 " + best.getMetricKey() + "가 상위권 분포 대비 우수합니다."
                : "강점: 특정 영역은 평균 수준입니다.";

        String focus = worst.getPercentile() <= 50
                ? "개선: " + worst.getCategory() + "의 " + worst.getMetricKey()
                        + "를 끌어올리면 상위권과 격차가 빠르게 줄어듭니다."
                : "개선: 균형 잡힌 지표입니다. 세부 최적화에 집중하세요.";

        String direction = switch (worst.getCategory()) {
            case "phase" -> "다음 세이프존 수축 전에 이동을 시작하고, 센터-엣지 밸런스를 시험해 보세요.";
            case "combat" -> "교전 시 팀 각 벌리기와 동시 사격 타이밍을 맞추어 다운 전환률을 높이세요.";
            case "grenade" -> "푸시 3초 전 투척을 습관화해 시야 확보와 진입 각을 만들면 상위권 패턴에 근접합니다.";
            default -> "플레이 리듬을 일정하게 유지하며 리스크를 줄이세요.";
        };

        return tendency + " " + focus + " " + direction;
    }

    private String readableKey(String key) {
        return key.replace("_", " ");
    }
}
