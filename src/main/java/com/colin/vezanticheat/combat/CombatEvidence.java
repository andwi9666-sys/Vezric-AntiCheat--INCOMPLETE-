package com.colin.vezanticheat.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Rolling combat evidence for one attacker.
 * Suspicion accumulates in a buffer across many hits and decays over time or on
 * clean hits, so a single lag spike or borderline reach does not become an instant ban.
 */
public final class CombatEvidence {

    private static final int MAX_REASON_HISTORY = 20;
    private static final int DEFAULT_HIT_POINT_HISTORY = 100;

    private final UUID attackerUuid;
    private final CombatConfig config;
    private final Deque<CombatHitClassification> classifications = new ArrayDeque<CombatHitClassification>();
    private final Deque<HitPointData> hitPoints = new ArrayDeque<HitPointData>();

    private int cleanCount;
    private int lenientCount;
    private int veryLenientCount;
    private int badCount;
    private int impossibleCount;
    private double rollingSuspiciousScore;
    private final Deque<String> lastSuspiciousReasons = new ArrayDeque<String>();
    private long lastAttackTimeMs;
    private long lastDecayMs;
    private double buffer;

    public CombatEvidence(UUID attackerUuid, CombatConfig config) {
        this.attackerUuid = attackerUuid;
        this.config = config == null ? CombatConfig.defaults() : config;
        this.lastDecayMs = System.currentTimeMillis();
    }

    public UUID getAttackerUuid() {
        return attackerUuid;
    }

    public CombatConfig getConfig() {
        return config;
    }

    public double getBuffer() {
        return buffer;
    }

    public void setBuffer(double buffer) {
        this.buffer = Math.max(0.0D, buffer);
    }

    public long getLastAttackTimeMs() {
        return lastAttackTimeMs;
    }

    public double getRollingSuspiciousScore() {
        return rollingSuspiciousScore;
    }

    public int getCleanCount() {
        return cleanCount;
    }

    public int getLenientCount() {
        return lenientCount;
    }

    public int getVeryLenientCount() {
        return veryLenientCount;
    }

    public int getBadCount() {
        return badCount;
    }

    public int getImpossibleCount() {
        return impossibleCount;
    }

    public List<String> getLastSuspiciousReasons() {
        return new ArrayList<String>(lastSuspiciousReasons);
    }

    public List<String> getLastSuspiciousReasons(int limit) {
        if (limit <= 0) {
            return new ArrayList<String>();
        }
        List<String> all = getLastSuspiciousReasons();
        if (all.size() <= limit) {
            return all;
        }
        return new ArrayList<String>(all.subList(all.size() - limit, all.size()));
    }

    public int getTotalSamples() {
        return classifications.size();
    }

    public List<CombatHitClassification> getRecentClassifications() {
        return new ArrayList<CombatHitClassification>(classifications);
    }

    public int getHitPointHistorySize() {
        return hitPoints.size();
    }

    public int getHitPointSampleCount() {
        return Math.min(config.getHitDistributionRatioWindow(), hitPoints.size());
    }

    public double getExpansionShellHitRatio() {
        return ratioFor(new HitPointPredicate() {
            @Override
            public boolean test(HitPointData point) {
                return point.isExpansionShellHit();
            }
        });
    }

    public double getCenterLikeHitRatio() {
        return ratioFor(new HitPointPredicate() {
            @Override
            public boolean test(HitPointData point) {
                return point.isCenterLikeHit();
            }
        });
    }

    public double getEdgeHitRatio() {
        return ratioFor(new HitPointPredicate() {
            @Override
            public boolean test(HitPointData point) {
                return point.isEdgeHit();
            }
        });
    }

    public double getCleanPercentage() {
        return percentageFor(cleanCount);
    }

    public double getLenientPercentage() {
        return percentageFor(lenientCount);
    }

    public double getVeryLenientPercentage() {
        return percentageFor(veryLenientCount);
    }

    public double getBadPercentage() {
        return percentageFor(badCount);
    }

    public double getImpossiblePercentage() {
        return percentageFor(impossibleCount);
    }

    public boolean shouldCancelHit(CombatHitResult result, boolean cancelImpossibleHits) {
        return CombatCancelDecision.resolve(result, buffer, config, cancelImpossibleHits).shouldCancel();
    }

    /** @deprecated use {@link #shouldCancelHit(CombatHitResult, boolean)} */
    @Deprecated
    public boolean shouldCancelHit() {
        return buffer >= config.getCancelHitBuffer();
    }

    public boolean shouldFlag() {
        return buffer >= config.getFlagBuffer();
    }

    public void addResult(CombatHitResult result) {
        if (result == null) return;

        long nowMs = System.currentTimeMillis();
        decayBuffer(nowMs);

        int window = Math.max(1, config.getCombatEvidenceWindow());
        if (classifications.size() >= window) {
            CombatHitClassification evicted = classifications.pollFirst();
            decrementCounter(evicted);
        }

        CombatHitClassification classification = result.getClassification();
        if (classification == null) {
            classification = CombatHitClassification.CLEAN;
        }

        classifications.addLast(classification);
        incrementCounter(classification);

        double finalScore = Math.max(0.0D, result.getFinalScore());
        // Clean hits decay buffer; suspicious hits accumulate score over time instead of instant action.
        if (classification == CombatHitClassification.CLEAN) {
            buffer = Math.max(0.0D, buffer - config.getCleanHitBufferDecay());
            rollingSuspiciousScore = Math.max(0.0D, rollingSuspiciousScore - config.getCleanHitBufferDecay());
        } else {
            buffer += finalScore;
            rollingSuspiciousScore += finalScore;
        }

        if (finalScore > 0.0D || classification.isSuspicious()) {
            appendReasons(result.getReasons());
        }

        recordHitPoint(result);
        applyHitDistributionBuffer(result);
        capBuffer();

        lastAttackTimeMs = nowMs;
    }

    private void capBuffer() {
        double maxBuffer = Math.max(0.0D, config.getMaxBuffer());
        if (maxBuffer > 0.0D && buffer > maxBuffer) {
            buffer = maxBuffer;
        }
    }

    private void recordHitPoint(CombatHitResult result) {
        HitPointData data = result.getHitPointData();
        if (data == null) {
            return;
        }

        int historySize = Math.max(1, config.getHitDistributionHistorySize());
        if (historySize <= 0) {
            historySize = DEFAULT_HIT_POINT_HISTORY;
        }
        if (hitPoints.size() >= historySize) {
            hitPoints.pollFirst();
        }
        hitPoints.addLast(data);
    }

    private void applyHitDistributionBuffer(CombatHitResult result) {
        if (getHitPointSampleCount() < config.getHitDistributionMinSamples()) {
            return;
        }

        double shellRatio = getExpansionShellHitRatio();
        if (shellRatio > config.getHitDistributionShellRatioThreshold()) {
            buffer += config.getHitDistributionShellBuffer();
            appendDistributionReason("Hit distribution: high expansion-shell ratio");
        }

        int ping = result == null ? Integer.MAX_VALUE : Math.max(0, result.getPing());
        if (ping <= config.getHitDistributionLowPingThreshold()
                && shellRatio > config.getHitDistributionLowPingShellRatioThreshold()) {
            buffer += config.getHitDistributionLowPingShellBuffer();
            appendDistributionReason("Hit distribution: low ping with repeated expansion-shell hits");
        }
        capBuffer();
    }

    private void appendDistributionReason(String reason) {
        lastSuspiciousReasons.addLast(reason);
        while (lastSuspiciousReasons.size() > MAX_REASON_HISTORY) {
            lastSuspiciousReasons.pollFirst();
        }
    }

    private double ratioFor(HitPointPredicate predicate) {
        int window = Math.max(1, config.getHitDistributionRatioWindow());
        int available = hitPoints.size();
        if (available <= 0 || predicate == null) {
            return 0.0D;
        }

        int sampleCount = Math.min(window, available);
        int start = available - sampleCount;
        int matches = 0;
        int index = 0;
        for (HitPointData point : hitPoints) {
            if (index++ < start) {
                continue;
            }
            if (point != null && predicate.test(point)) {
                matches++;
            }
        }
        return matches / (double) sampleCount;
    }

    private interface HitPointPredicate {
        boolean test(HitPointData point);
    }

    public void decayBuffer(long nowMs) {
        if (nowMs <= lastDecayMs) {
            return;
        }

        double elapsedSeconds = (nowMs - lastDecayMs) / 1000.0D;
        if (elapsedSeconds <= 0.0D) {
            return;
        }

        // Passive decay so old suspicion fades without requiring clean hits.
        buffer = Math.max(0.0D, buffer - (config.getBufferDecayPerSecond() * elapsedSeconds));
        lastDecayMs = nowMs;
    }

    private double percentageFor(int count) {
        int total = classifications.size();
        if (total <= 0) {
            return 0.0D;
        }
        return (count * 100.0D) / total;
    }

    private void incrementCounter(CombatHitClassification classification) {
        switch (classification) {
            case CLEAN:
                cleanCount++;
                break;
            case LENIENT:
                lenientCount++;
                break;
            case VERY_LENIENT:
                veryLenientCount++;
                break;
            case BAD:
                badCount++;
                break;
            case IMPOSSIBLE:
                impossibleCount++;
                break;
            default:
                break;
        }
    }

    private void decrementCounter(CombatHitClassification classification) {
        if (classification == null) {
            return;
        }
        switch (classification) {
            case CLEAN:
                cleanCount = Math.max(0, cleanCount - 1);
                break;
            case LENIENT:
                lenientCount = Math.max(0, lenientCount - 1);
                break;
            case VERY_LENIENT:
                veryLenientCount = Math.max(0, veryLenientCount - 1);
                break;
            case BAD:
                badCount = Math.max(0, badCount - 1);
                break;
            case IMPOSSIBLE:
                impossibleCount = Math.max(0, impossibleCount - 1);
                break;
            default:
                break;
        }
    }

    private void appendReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return;
        }
        for (String reason : reasons) {
            if (reason == null || reason.isEmpty()) {
                continue;
            }
            lastSuspiciousReasons.addLast(reason);
            while (lastSuspiciousReasons.size() > MAX_REASON_HISTORY) {
                lastSuspiciousReasons.pollFirst();
            }
        }
    }
}
