package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.check.AccuracySpikeResult;
import com.colin.vezanticheat.combat.check.AimCorrelationResult;
import com.colin.vezanticheat.combat.check.TargetSwitchResult;

/**
 * Combines geometry (reach/expansion) and behavior (aim) signals into base/final scores.
 * Classification floors ensure BAD/IMPOSSIBLE hits retain minimum suspicion even when
 * ping or knockback leniency reduces the computed score.
 */
public final class CombatScoreComposer {

    private static final double LOS_PENALTY = 0.5D;

    private CombatScoreComposer() {}

    public static double computeGeometryScore(HitboxExpansionTier tier,
                                              CombatHitClassification classification,
                                              int ping,
                                              boolean pingCompensated,
                                              boolean lineOfSightValid) {
        return computeGeometryScore(tier, classification, ping, pingCompensated, lineOfSightValid,
                CombatConfig.defaults());
    }

    public static double computeGeometryScore(HitboxExpansionTier tier,
                                              CombatHitClassification classification,
                                              int ping,
                                              boolean pingCompensated,
                                              boolean lineOfSightValid,
                                              CombatConfig config) {
        CombatConfig effectiveConfig = config == null ? CombatConfig.defaults() : config;
        double expansionScore = scoreExpansionTier(tier, ping, effectiveConfig);
        double losPenalty = lineOfSightPenalty(classification, lineOfSightValid);
        double raw = expansionScore + losPenalty;
        return Math.max(raw, classificationFloor(classification, tier, effectiveConfig));
    }

    public static double sumBehaviorScore(PreAimResult preAim,
                                          AccuracySpikeResult spike,
                                          AimCorrelationResult correlation,
                                          TargetSwitchResult targetSwitch) {
        double total = 0.0D;
        if (preAim != null) {
            total += Math.max(0.0D, preAim.getSuspiciousScore());
        }
        if (spike != null) {
            total += Math.max(0.0D, spike.getSuspiciousScore());
        }
        if (correlation != null) {
            total += Math.max(0.0D, correlation.getSuspiciousScore());
        }
        if (targetSwitch != null) {
            total += Math.max(0.0D, targetSwitch.getSuspiciousScore());
        }
        return total;
    }

    public static double computeBaseScore(double geometryScore, double behaviorScore) {
        return Math.max(0.0D, geometryScore) + Math.max(0.0D, behaviorScore);
    }

    public static double computeFinalScore(double geometryScore,
                                           double behaviorScore,
                                           CombatHitClassification classification,
                                           HitboxExpansionTier tier,
                                           boolean knockbackLeniencyApplied,
                                           CombatConfig config) {
        CombatConfig effectiveConfig = config == null ? CombatConfig.defaults() : config;
        // Knockback leniency applies to behavior only; geometry was already computed separately.
        double reducedBehavior = behaviorScore;
        if (knockbackLeniencyApplied) {
            reducedBehavior *= effectiveConfig.getKnockbackBehaviorMultiplier();
        }

        double computed = Math.max(0.0D, geometryScore) + Math.max(0.0D, reducedBehavior);
        return Math.max(computed, classificationFloor(classification, tier, effectiveConfig));
    }

    /** @deprecated use {@link #computeFinalScore(double, double, CombatHitClassification, HitboxExpansionTier, boolean, CombatConfig)} */
    @Deprecated
    public static double computeFinalScore(double geometryScore,
                                           double behaviorScore,
                                           CombatHitClassification classification,
                                           HitboxExpansionTier tier,
                                           boolean knockbackLeniencyApplied,
                                           double knockbackAimScoreReduction) {
        double reducedBehavior = behaviorScore;
        if (knockbackLeniencyApplied && knockbackAimScoreReduction > 0.0D) {
            reducedBehavior *= (1.0D - knockbackAimScoreReduction);
        }
        double computed = Math.max(0.0D, geometryScore) + Math.max(0.0D, reducedBehavior);
        return Math.max(computed, classificationFloor(classification, tier, CombatConfig.defaults()));
    }

    public static ScorePair finalizeScores(CombatHitResult merged,
                                           boolean knockbackLeniencyApplied,
                                           CombatConfig config) {
        if (merged == null) {
            return new ScorePair(0.0D, 0.0D);
        }

        CombatConfig effectiveConfig = config == null ? CombatConfig.defaults() : config;
        HitboxExpansionTier tier = merged.getExpansionTier();
        CombatHitClassification classification = merged.getClassification();
        if (classification == null) {
            classification = CombatHitClassification.CLEAN;
        }

        double geometryScore = computeGeometryScore(
                tier,
                classification,
                merged.getPing(),
                merged.isPingCompensated(),
                merged.isLineOfSightValid(),
                effectiveConfig);

        double behaviorScore = sumBehaviorScore(
                merged.getPreAimResult(),
                merged.getAccuracySpikeResult(),
                merged.getAimCorrelationResult(),
                merged.getTargetSwitchResult());

        double baseScore = computeBaseScore(geometryScore, behaviorScore);
        double finalScore = computeFinalScore(
                geometryScore,
                behaviorScore,
                classification,
                tier,
                knockbackLeniencyApplied,
                effectiveConfig);

        return new ScorePair(baseScore, finalScore);
    }

    private static double scoreExpansionTier(HitboxExpansionTier tier, int ping, CombatConfig config) {
        if (tier == null) {
            return 0.0D;
        }

        double score = tier.getBaseSuspiciousScore();
        int lowPingThreshold = config.getPingThresholdLow();

        // Low ping: server trusts eye position more, so expansion-shell hits score higher.
        if (ping <= lowPingThreshold && tier == HitboxExpansionTier.FULL) {
            score += config.getLowPingFullExpansionExtra();
        }
        if (ping <= lowPingThreshold && tier == HitboxExpansionTier.MEDIUM) {
            score += config.getLowPingMediumExpansionExtra();
        }

        // High ping: reduce suspicion score only; tier and BAD/IMPOSSIBLE classifications unchanged.
        if (ping > config.getPingThresholdHigh() && tier.isExpansionTier()) {
            score *= config.getHighPingReductionMultiplier();
        }

        return Math.max(0.0D, score);
    }

    private static double lineOfSightPenalty(CombatHitClassification classification, boolean lineOfSightValid) {
        if (lineOfSightValid || classification == CombatHitClassification.IMPOSSIBLE) {
            return 0.0D;
        }
        return LOS_PENALTY;
    }

    static double classificationFloor(CombatHitClassification classification,
                                    HitboxExpansionTier tier,
                                    CombatConfig config) {
        if (classification == null || config == null) {
            return 0.0D;
        }

        switch (classification) {
            case CLEAN:
                return config.getCleanScore();
            case LENIENT:
                return config.getLenientScore();
            case VERY_LENIENT:
                return config.getVeryLenientScore();
            case BAD:
                return config.getBadScore();
            case IMPOSSIBLE:
                return config.getImpossibleScore();
            default:
                return 0.0D;
        }
    }

    public static final class ScorePair {
        private final double baseScore;
        private final double finalScore;

        public ScorePair(double baseScore, double finalScore) {
            this.baseScore = baseScore;
            this.finalScore = finalScore;
        }

        public double getBaseScore() {
            return baseScore;
        }

        public double getFinalScore() {
            return finalScore;
        }
    }
}
