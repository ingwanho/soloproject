package com.example.pubg.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.pubg.dto.FeatureAggregate;
import com.example.pubg.dto.MatchMeta;
import com.example.pubg.util.GeoUtils;

@Service
public class FeatureService {
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    public FeatureAggregate computeFeatures(String accountId, MatchMeta meta, List<Map<String, Object>> telemetry) {
        List<PlayerSnapshot> timeline = buildTimeline(accountId, telemetry);
        List<PhaseInfo> phases = extractPhases(meta, telemetry);

        Map<String, Double> phaseMetrics = computePhaseMetrics(timeline, phases, meta.durationSeconds());
        Map<String, Double> combatMetrics = computeCombatMetrics(accountId, telemetry);
        Map<String, Double> grenadeMetrics = computeGrenadeMetrics(accountId, telemetry, timeline, meta.durationSeconds());

        return new FeatureAggregate(phaseMetrics, combatMetrics, grenadeMetrics);
    }

    private List<PlayerSnapshot> buildTimeline(String accountId, List<Map<String, Object>> telemetry) {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (Map<String, Object> event : telemetry) {
            Map<String, Object> character = extractCharacter(event);
            if (character == null) {
                continue;
            }
            String id = (String) character.getOrDefault("accountId", character.get("name"));
            if (!accountId.equals(id)) {
                continue;
            }
            Instant ts = parseTime(event.get("_D"));
            Map<String, Object> location = (Map<String, Object>) character.get("location");
            if (location == null) {
                continue;
            }
            double x = ((Number) location.getOrDefault("x", 0)).doubleValue();
            double y = ((Number) location.getOrDefault("y", 0)).doubleValue();
            boolean inVehicle = Boolean.TRUE.equals(character.get("isInVehicle"));
            snapshots.add(new PlayerSnapshot(ts, x, y, inVehicle));
        }
        snapshots.sort(Comparator.comparing(PlayerSnapshot::timestamp));
        return snapshots;
    }

    private List<PhaseInfo> extractPhases(MatchMeta meta, List<Map<String, Object>> telemetry) {
        List<PhaseInfo> phases = new ArrayList<>();
        for (Map<String, Object> event : telemetry) {
            String type = (String) event.get("_T");
            if ("LogPhaseChange".equals(type) || "LogBlueZoneCustom".equals(type)) {
                Instant ts = parseTime(event.get("_D"));
                Number phaseNum = (Number) event.getOrDefault("phase", 0);
                Map<String, Object> safeZonePos = (Map<String, Object>) event.getOrDefault("safeZonePosition",
                        event.getOrDefault("blueZonePosition", Map.of()));
                double x = ((Number) safeZonePos.getOrDefault("x", 0)).doubleValue();
                double y = ((Number) safeZonePos.getOrDefault("y", 0)).doubleValue();
                double radius = ((Number) event.getOrDefault("safeZoneRadius", event.getOrDefault("radius", 0))).doubleValue();
                phases.add(new PhaseInfo(phaseNum.intValue(), ts, x, y, radius));
            }
        }
        if (phases.isEmpty()) {
            // fallback to evenly split phases if telemetry lacks explicit phase data
            int defaultPhases = 8;
            long segment = meta.durationSeconds() / defaultPhases;
            for (int i = 0; i < defaultPhases; i++) {
                phases.add(new PhaseInfo(i + 1, Instant.EPOCH.plusSeconds(segment * i), 0, 0, 1000));
            }
        }
        phases.sort(Comparator.comparing(PhaseInfo::timestamp));
        return phases;
    }

    private Map<String, Double> computePhaseMetrics(List<PlayerSnapshot> timeline, List<PhaseInfo> phases,
            long matchDuration) {
        Map<String, Double> metrics = new HashMap<>();
        for (int i = 0; i < phases.size(); i++) {
            PhaseInfo phase = phases.get(i);
            Instant phaseEnd = (i + 1 < phases.size()) ? phases.get(i + 1).timestamp()
                    : phase.timestamp().plusSeconds(matchDuration / Math.max(1, phases.size()));
            List<PlayerSnapshot> inWindow = timeline.stream()
                    .filter(p -> !p.timestamp().isBefore(phase.timestamp()) && p.timestamp().isBefore(phaseEnd))
                    .toList();
            if (inWindow.isEmpty()) {
                continue;
            }
            double radius = phase.radius() > 0 ? phase.radius() : 1;
            double enterDelay = computeEnterDelay(inWindow, phase, phaseEnd);
            double centerBias = inWindow.stream()
                    .mapToDouble(p -> GeoUtils.distance(p.x(), p.y(), phase.x(), phase.y()) / radius)
                    .average()
                    .orElse(1.0);
            double rotationDistance = 0;
            double blueExposure = 0;
            boolean enteredWithVehicle = false;
            for (int j = 1; j < inWindow.size(); j++) {
                PlayerSnapshot prev = inWindow.get(j - 1);
                PlayerSnapshot cur = inWindow.get(j);
                double dist = GeoUtils.distance(prev.x(), prev.y(), cur.x(), cur.y());
                rotationDistance += dist;
                boolean prevInBlue = GeoUtils.distance(prev.x(), prev.y(), phase.x(), phase.y()) > radius;
                if (prevInBlue) {
                    long dt = Duration.between(prev.timestamp(), cur.timestamp()).toMillis();
                    blueExposure += dt / 1000.0;
                }
                if (!enteredWithVehicle && !prevInBlue && cur.inVehicle()) {
                    enteredWithVehicle = true;
                }
            }
            double phaseDuration = Duration.between(phase.timestamp(), phaseEnd).toMillis() / 1000.0;
            double avgSpeed = phaseDuration > 0 ? rotationDistance / phaseDuration : 0;
            String prefix = "phase" + phase.phase() + ".";
            metrics.put(prefix + "enter_delay_s", enterDelay);
            metrics.put(prefix + "center_bias", GeoUtils.clamp(centerBias, 0, 2));
            metrics.put(prefix + "rotation_distance_m", rotationDistance);
            metrics.put(prefix + "avg_speed_mps", avgSpeed);
            metrics.put(prefix + "entered_with_vehicle", enteredWithVehicle ? 1.0 : 0.0);
            metrics.put(prefix + "blue_exposure_s_phase", blueExposure);
        }
        return metrics;
    }

    private double computeEnterDelay(List<PlayerSnapshot> inWindow, PhaseInfo phase, Instant phaseEnd) {
        Instant enter = null;
        for (PlayerSnapshot snap : inWindow) {
            double dist = GeoUtils.distance(snap.x(), snap.y(), phase.x(), phase.y());
            if (dist <= Math.max(1, phase.radius())) {
                enter = snap.timestamp();
                break;
            }
        }
        if (enter == null) {
            return Duration.between(phase.timestamp(), phaseEnd).toMillis() / 1000.0;
        }
        return Duration.between(phase.timestamp(), enter).toMillis() / 1000.0;
    }

    private Map<String, Double> computeCombatMetrics(String accountId, List<Map<String, Object>> telemetry) {
        List<CombatEvent> combats = new ArrayList<>();
        for (Map<String, Object> event : telemetry) {
            String type = (String) event.get("_T");
            if (type == null) {
                continue;
            }
            if (type.contains("Attack") || type.contains("TakeDamage") || type.contains("Kill") || type.contains("Down")) {
                Map<String, Object> attacker = (Map<String, Object>) event.get("attacker");
                Map<String, Object> victim = (Map<String, Object>) event.get("victim");
                if (attacker == null && victim == null) {
                    attacker = extractCharacter(event);
                }
                Instant ts = parseTime(event.get("_D"));
                combats.add(new CombatEvent(ts, attacker, victim, type));
            }
        }
        combats.sort(Comparator.comparing(CombatEvent::timestamp));
        List<List<CombatEvent>> clusters = clusterCombats(combats);

        List<Double> angleVars = new ArrayList<>();
        List<Double> spreads = new ArrayList<>();
        List<Double> simulRates = new ArrayList<>();
        List<Double> firstShotDistances = new ArrayList<>();
        int downToKill = 0;
        int downs = 0;

        for (List<CombatEvent> cluster : clusters) {
            if (cluster.isEmpty()) {
                continue;
            }
            CombatEvent anchor = cluster.get(0);
            double baseX = anchor.attackerX;
            double baseY = anchor.attackerY;
            List<Double> angles = new ArrayList<>();
            List<double[]> positions = new ArrayList<>();
            Map<String, Integer> victimHitCounts = new HashMap<>();

            for (CombatEvent ev : cluster) {
                if (Double.isFinite(ev.attackerX) && Double.isFinite(ev.attackerY) && Double.isFinite(ev.victimX)
                        && Double.isFinite(ev.victimY)) {
                    double dx = ev.victimX - ev.attackerX;
                    double dy = ev.victimY - ev.attackerY;
                    double angle = Math.toDegrees(Math.atan2(dy, dx));
                    angles.add(angle);
                    positions.add(new double[] { ev.attackerX, ev.attackerY });
                    double dist = GeoUtils.distance(ev.attackerX, ev.attackerY, ev.victimX, ev.victimY);
                    if (victimHitCounts.isEmpty()) {
                        firstShotDistances.add(dist);
                    }
                    String key = Optional.ofNullable(ev.victimName).orElse("unknown");
                    victimHitCounts.merge(key, 1, Integer::sum);
                }
                if (ev.type.contains("Down")) {
                    downs++;
                    boolean converted = cluster.stream().anyMatch(next -> next.type.contains("Kill")
                            && Duration.between(ev.timestamp, next.timestamp).getSeconds() <= 10);
                    if (converted) {
                        downToKill++;
                    }
                }
            }
            if (!angles.isEmpty()) {
                double mean = angles.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double variance = angles.stream().mapToDouble(a -> Math.pow(a - mean, 2)).average().orElse(0);
                angleVars.add(Math.sqrt(variance));
            }
            if (!positions.isEmpty()) {
                double cx = positions.stream().mapToDouble(p -> p[0]).average().orElse(0);
                double cy = positions.stream().mapToDouble(p -> p[1]).average().orElse(0);
                double spread = positions.stream()
                        .mapToDouble(p -> GeoUtils.distance(p[0], p[1], cx, cy))
                        .average()
                        .orElse(0);
                spreads.add(spread);
            }
            if (!victimHitCounts.isEmpty()) {
                long multi = victimHitCounts.values().stream().filter(v -> v >= 2).count();
                simulRates.add(multi / (double) victimHitCounts.size());
            }
        }

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("team_angle_var_deg", safeAvg(angleVars));
        metrics.put("team_spread_m", safeAvg(spreads));
        metrics.put("simul_fire_rate", safeAvg(simulRates));
        metrics.put("first_shot_distance_m", safeAvg(firstShotDistances));
        metrics.put("dtk_conv_rate", downs == 0 ? 0 : downToKill / (double) downs);
        return metrics;
    }

    private Map<String, Double> computeGrenadeMetrics(String accountId, List<Map<String, Object>> telemetry,
            List<PlayerSnapshot> timeline, long matchDuration) {
        int frag = 0, smoke = 0, flash = 0, molotov = 0;
        List<Instant> grenadeTimes = new ArrayList<>();
        List<Instant> combatTimes = new ArrayList<>();
        int grenadeToDown = 0;
        int grenadeHits = 0;

        for (Map<String, Object> event : telemetry) {
            String type = (String) event.get("_T");
            if (type == null) {
                continue;
            }
            if ("LogItemThrow".equals(type)) {
                Map<String, Object> character = extractCharacter(event);
                if (character == null) {
                    continue;
                }
                String id = (String) character.getOrDefault("accountId", character.get("name"));
                if (!accountId.equals(id)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) event.get("item");
                String sub = item != null ? (String) item.getOrDefault("subCategory", "") : "";
                Instant ts = parseTime(event.get("_D"));
                grenadeTimes.add(ts);
                if (sub.contains("Grenade")) {
                    frag++;
                } else if (sub.contains("Smoke")) {
                    smoke++;
                } else if (sub.contains("Flash")) {
                    flash++;
                } else if (sub.contains("Molotov")) {
                    molotov++;
                }
            }
            if (type.contains("Attack") || type.contains("TakeDamage")) {
                Map<String, Object> attacker = (Map<String, Object>) event.get("attacker");
                if (attacker != null) {
                    String id = (String) attacker.getOrDefault("accountId", attacker.get("name"));
                    if (accountId.equals(id)) {
                        combatTimes.add(parseTime(event.get("_D")));
                    }
                }
                String damageType = (String) event.getOrDefault("damageTypeCategory", "");
                if (damageType != null && damageType.toLowerCase().contains("grenade")) {
                    grenadeHits++;
                    Instant hitTime = parseTime(event.get("_D"));
                    boolean downed = telemetry.stream().anyMatch(next -> {
                        String t = (String) next.get("_T");
                        if (t != null && t.contains("Down")) {
                            Instant downTs = parseTime(next.get("_D"));
                            return !downTs.isBefore(hitTime) && Duration.between(hitTime, downTs).getSeconds() <= 10;
                        }
                        return false;
                    });
                    if (downed) {
                        grenadeToDown++;
                    }
                }
            }
        }

        double surviveSeconds = matchDuration > 0 ? matchDuration : timeline.isEmpty()
                ? 1
                : Duration.between(timeline.get(0).timestamp(),
                        timeline.get(timeline.size() - 1).timestamp()).toSeconds();
        double norm = surviveSeconds / 600.0;
        norm = norm == 0 ? 1 : norm;

        double firstGrenadeDelay = 0;
        if (!grenadeTimes.isEmpty() && !combatTimes.isEmpty()) {
            Instant firstCombat = combatTimes.stream().min(Instant::compareTo).orElse(grenadeTimes.get(0));
            Instant firstGrenade = grenadeTimes.stream().min(Instant::compareTo).get();
            firstGrenadeDelay = Duration.between(firstCombat, firstGrenade).toMillis() / 1000.0;
        }

        long pushes = combatTimes.size();
        long prePush = combatTimes.stream()
                .filter(ct -> grenadeTimes.stream().anyMatch(gt -> Duration.between(gt, ct).abs().getSeconds() <= 3))
                .count();

        Map<String, Double> metrics = new HashMap<>();
        metrics.put("frag_per_10m", frag / norm);
        metrics.put("smoke_per_10m", smoke / norm);
        metrics.put("flash_per_10m", flash / norm);
        metrics.put("molotov_per_10m", molotov / norm);
        metrics.put("first_grenade_delay_s", firstGrenadeDelay);
        metrics.put("pre_push_grenade_rate", pushes == 0 ? 0 : prePush / (double) pushes);
        metrics.put("nade_to_down_chain_rate", grenadeHits == 0 ? 0 : grenadeToDown / (double) grenadeHits);
        return metrics;
    }

    private List<List<CombatEvent>> clusterCombats(List<CombatEvent> events) {
        List<List<CombatEvent>> clusters = new ArrayList<>();
        List<CombatEvent> current = new ArrayList<>();
        for (CombatEvent ev : events) {
            if (current.isEmpty()) {
                current.add(ev);
                continue;
            }
            CombatEvent last = current.get(current.size() - 1);
            long dt = Duration.between(last.timestamp, ev.timestamp).getSeconds();
            double dist = GeoUtils.distance(last.attackerX, last.attackerY, ev.attackerX, ev.attackerY);
            if (dt <= 10 && dist <= 40) {
                current.add(ev);
            } else {
                clusters.add(new ArrayList<>(current));
                current.clear();
                current.add(ev);
            }
        }
        if (!current.isEmpty()) {
            clusters.add(current);
        }
        return clusters;
    }

    private Map<String, Object> extractCharacter(Map<String, Object> event) {
        Object obj = event.get("character");
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        return null;
    }

    private Instant parseTime(Object raw) {
        try {
            if (raw instanceof String s) {
                return Instant.parse(s);
            }
        } catch (Exception e) {
            log.debug("Failed to parse time {}", raw, e);
        }
        return Instant.EPOCH;
    }

    private double safeAvg(List<Double> list) {
        return list.isEmpty() ? 0 : list.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private record PlayerSnapshot(Instant timestamp, double x, double y, boolean inVehicle) {
    }

    private record PhaseInfo(int phase, Instant timestamp, double x, double y, double radius) {
    }

    private static class CombatEvent {
        private final Instant timestamp;
        private final String type;
        private final String attackerName;
        private final String victimName;
        private final double attackerX;
        private final double attackerY;
        private final double victimX;
        private final double victimY;

        private CombatEvent(Instant timestamp, Map<String, Object> attacker, Map<String, Object> victim, String type) {
            this.timestamp = timestamp;
            this.type = type;
            this.attackerName = attacker != null ? (String) attacker.getOrDefault("name", attacker.get("accountId")) : null;
            this.victimName = victim != null ? (String) victim.getOrDefault("name", victim.get("accountId")) : null;
            double[] attackerPos = extractPos(attacker);
            double[] victimPos = extractPos(victim);
            this.attackerX = attackerPos[0];
            this.attackerY = attackerPos[1];
            this.victimX = victimPos[0];
            this.victimY = victimPos[1];
        }

        private static double[] extractPos(Map<String, Object> character) {
            if (character == null) {
                return new double[] { Double.NaN, Double.NaN };
            }
            Map<String, Object> loc = (Map<String, Object>) character.get("location");
            if (loc == null) {
                return new double[] { Double.NaN, Double.NaN };
            }
            return new double[] {
                    ((Number) loc.getOrDefault("x", Double.NaN)).doubleValue(),
                    ((Number) loc.getOrDefault("y", Double.NaN)).doubleValue() };
        }

        public Instant timestamp() {
            return timestamp;
        }
    }
}
