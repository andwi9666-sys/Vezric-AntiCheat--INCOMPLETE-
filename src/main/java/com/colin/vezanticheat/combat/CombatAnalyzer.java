package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.check.AccuracySpikeAnalyzer;
import com.colin.vezanticheat.combat.check.AccuracySpikeResult;
import com.colin.vezanticheat.combat.check.AimCorrelationAnalyzer;
import com.colin.vezanticheat.combat.check.AimCorrelationResult;
import com.colin.vezanticheat.combat.check.TargetSwitchAnalyzer;
import com.colin.vezanticheat.combat.check.TargetSwitchResult;
import com.colin.vezanticheat.combat.math.BoundingBox;
import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerCombatData;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the PacketEvents combat pipeline: geometry classification, behavior
 * analyzers (pre-aim, spike, correlation, target switch), score finalization, and
 * evidence buffering. Each stage adds context so no single packet triggers a ban.
 */
public final class CombatAnalyzer {

    private static final double CLOSEST_PRE_AIM_FAR_YAW = 20.0D;
    private static final double RULE3_PRE_AIM_SCORE = 1.0D;

    private static final double STRONG_TARGET_ANGULAR = 18.0D;
    private static final double STRONG_ATTACKER_YAW = 3.0D;
    private static final double STRONG_CORRELATION_SCORE = 1.5D;

    private static final double MODERATE_TARGET_ANGULAR = 10.0D;
    private static final double MODERATE_ATTACKER_YAW = 2.0D;
    private static final double MODERATE_CORRELATION_SCORE = 1.0D;

    private final CombatConfig config;
    private boolean cancelImpossibleHits = true;
    private final Map<UUID, CombatEvidence> evidenceByAttacker = new HashMap<UUID, CombatEvidence>();
    private final Map<UUID, PlayerCombatData> combatDataByPlayer = new HashMap<UUID, PlayerCombatData>();

    public CombatAnalyzer() {
        this(CombatConfig.defaults());
    }

    public CombatAnalyzer(CombatConfig config) {
        this.config = config == null ? CombatConfig.defaults() : config;
    }

    public CombatConfig getConfig() {
        return config;
    }

    public void setCancelImpossibleHits(boolean cancelImpossibleHits) {
        this.cancelImpossibleHits = cancelImpossibleHits;
    }

    public boolean isCancelImpossibleHits() {
        return cancelImpossibleHits;
    }

    public CombatEvidence getEvidence(UUID attackerId) {
        if (attackerId == null) {
            return null;
        }
        return evidenceByAttacker.get(attackerId);
    }

    public CombatEvidence getOrCreateEvidence(UUID attackerId) {
        if (attackerId == null) {
            return null;
        }
        CombatEvidence evidence = evidenceByAttacker.get(attackerId);
        if (evidence == null) {
            evidence = new CombatEvidence(attackerId, config);
            evidenceByAttacker.put(attackerId, evidence);
        }
        return evidence;
    }

    public void removeEvidence(UUID attackerId) {
        if (attackerId == null) {
            return;
        }
        evidenceByAttacker.remove(attackerId);
    }

    public PlayerCombatData getCombatData(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        return combatDataByPlayer.get(playerId);
    }

    public PlayerCombatData getOrCreateCombatData(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        PlayerCombatData data = combatDataByPlayer.get(playerId);
        if (data == null) {
            data = new PlayerCombatData(playerId);
            combatDataByPlayer.put(playerId, data);
        }
        return data;
    }

    public void removeCombatData(UUID playerId) {
        if (playerId == null) {
            return;
        }
        combatDataByPlayer.remove(playerId);
    }

    public void removePlayer(UUID playerId) {
        removeEvidence(playerId);
        removeCombatData(playerId);
    }

    public PreAimResult analyzePreAim(CombatSample sample) {
        return PreAimAnalyzer.analyze(sample, config);
    }

    public AccuracySpikeResult analyzeAccuracySpike(CombatSample sample, CombatHitResult currentResult) {
        return AccuracySpikeAnalyzer.analyze(sample, currentResult, config);
    }

    public AimCorrelationResult analyzeAimCorrelation(CombatSample sample) {
        return AimCorrelationAnalyzer.analyze(sample, config);
    }

    public TargetSwitchResult analyzeTargetSwitch(CombatSample sample) {
        // Switch detection reads prior attacks from PlayerCombatData populated by recordAttack().
        PlayerCombatData data = sample == null ? null : getCombatData(sample.getAttackerUuid());
        return TargetSwitchAnalyzer.analyze(sample, data, config);
    }

    public void markCombatDamage(UUID playerId, long nowMs) {
        PlayerCombatData data = getOrCreateCombatData(playerId);
        if (data != null) {
            data.markDamage(nowMs);
        }
    }

    public void markCombatVelocity(UUID playerId, long nowMs) {
        PlayerCombatData data = getOrCreateCombatData(playerId);
        if (data != null) {
            data.markVelocity(nowMs);
        }
    }

    public CombatHitResult analyzeHit(CombatSample sample) {
        return analyzeHit(sample, null);
    }

    public CombatHitResult analyzeHit(CombatSample sample, VezAntiCheat plugin) {
        if (sample == null) {
            return null;
        }
        PlayerCombatData attackerData = getCombatData(sample.getAttackerUuid());
        PlayerCombatData targetData = getCombatData(sample.getTargetUuid());
        CombatHitResult geometry = CombatHitClassifier.classify(sample, config, attackerData, targetData);
        if (geometry == null) {
            return null;
        }
        CombatHitResult withPreAim = mergePreAim(geometry, analyzePreAim(sample));
        CombatHitResult withSpike = mergeAccuracySpike(withPreAim, analyzeAccuracySpike(sample, withPreAim));
        AimCorrelationResult correlation = analyzeAimCorrelation(sample);
        CombatHitResult withCorrelation = mergeAimCorrelation(withSpike, correlation);
        CombatHitResult withTargetSwitch = mergeTargetSwitch(withCorrelation, analyzeTargetSwitch(sample));
        return finalizeScores(withTargetSwitch, sample, plugin);
    }

    public CombatHitResult analyzeHitGeometryOnly(CombatSample sample, VezAntiCheat plugin) {
        if (sample == null) {
            return null;
        }
        PlayerCombatData attackerData = getCombatData(sample.getAttackerUuid());
        PlayerCombatData targetData = getCombatData(sample.getTargetUuid());
        CombatHitResult geometry = CombatHitClassifier.classify(sample, config, attackerData, targetData);
        if (geometry == null) {
            return null;
        }
        return finalizeScores(geometry, sample, plugin);
    }

    /** @deprecated use {@link #analyzeHit(CombatSample, VezAntiCheat)} */
    public CombatHitResult analyze(CombatSample sample) {
        return analyzeHit(sample);
    }

    private CombatHitResult mergePreAim(CombatHitResult geometry, PreAimResult preAim) {
        if (geometry == null) {
            return null;
        }
        if (preAim == null) {
            preAim = PreAimResult.EMPTY;
        }

        double preAimScore = preAim.getSuspiciousScore();
        List<String> mergedReasons = new ArrayList<String>(geometry.getReasons());
        List<String> preAimReasons = new ArrayList<String>(preAim.getReasons());

        CombatHitClassification classification = geometry.getClassification();
        if (preAim.getSamplesChecked() > 0
                && preAim.getClosestYawError() > CLOSEST_PRE_AIM_FAR_YAW
                && (classification == CombatHitClassification.CLEAN
                || classification == CombatHitClassification.LENIENT)) {
            preAimScore += RULE3_PRE_AIM_SCORE;
            preAimReasons.add("Pre-aim never near target but hit classified lenient");
        }

        mergedReasons.addAll(preAimReasons);

        PreAimResult mergedPreAim = PreAimResult.builder()
                .hadPreAim(preAim.hadPreAim())
                .samplesChecked(preAim.getSamplesChecked())
                .samplesNearTarget(preAim.getSamplesNearTarget())
                .averageYawError(preAim.getAverageYawError())
                .averagePitchError(preAim.getAveragePitchError())
                .closestYawError(preAim.getClosestYawError())
                .closestPitchError(preAim.getClosestPitchError())
                .suspiciousScore(preAimScore)
                .reasons(preAimReasons)
                .build();

        return copyFrom(geometry)
                .preAimResult(mergedPreAim)
                .reasons(mergedReasons)
                .build();
    }

    private CombatHitResult mergeAccuracySpike(CombatHitResult current, AccuracySpikeResult spike) {
        if (current == null) {
            return null;
        }
        if (spike == null) {
            spike = AccuracySpikeResult.EMPTY;
        }

        List<String> mergedReasons = new ArrayList<String>(current.getReasons());
        mergedReasons.addAll(spike.getReasons());

        return copyFrom(current)
                .accuracySpikeResult(spike)
                .reasons(mergedReasons)
                .build();
    }

    private CombatHitResult mergeAimCorrelation(CombatHitResult current, AimCorrelationResult correlation) {
        if (current == null) {
            return null;
        }
        if (correlation == null) {
            correlation = AimCorrelationResult.EMPTY;
        }

        List<String> mergedReasons = new ArrayList<String>(current.getReasons());

        double correlationScore = 0.0D;
        AimCorrelationResult mergedCorrelation = correlation;

        if (correlation.enoughData()) {
            CombatHitClassification classification = current.getClassification();
            double targetAngular = correlation.getTargetAngularChange();
            double attackerYaw = correlation.getAttackerYawChange();
            List<String> correlationReasons = new ArrayList<String>(correlation.getReasons());

            // Merged as behavior evidence only; geometry/reach were evaluated separately.
            // Flag when tracking is disproportionately precise vs target movement over several ticks.
            if (targetAngular > STRONG_TARGET_ANGULAR
                    && attackerYaw < STRONG_ATTACKER_YAW
                    && (classification == CombatHitClassification.CLEAN
                    || classification == CombatHitClassification.LENIENT)) {
                correlationScore = STRONG_CORRELATION_SCORE;
                correlationReasons.add(
                        "Strong aim correlation miss: large target angular change with minimal yaw follow");
            } else if (targetAngular > MODERATE_TARGET_ANGULAR
                    && attackerYaw < MODERATE_ATTACKER_YAW
                    && classification == CombatHitClassification.CLEAN) {
                correlationScore = MODERATE_CORRELATION_SCORE;
                correlationReasons.add(
                        "Poor aim correlation: target moved across view but attacker yaw barely tracked");
            }

            mergedCorrelation = AimCorrelationResult.builder()
                    .enoughData(true)
                    .targetAngularChange(correlation.getTargetAngularChange())
                    .attackerYawChange(correlation.getAttackerYawChange())
                    .correlationScore(correlation.getCorrelationScore())
                    .suspicious(correlationScore > 0.0D)
                    .suspiciousScore(correlationScore)
                    .reasons(correlationReasons)
                    .build();
        }

        mergedReasons.addAll(mergedCorrelation.getReasons());

        return copyFrom(current)
                .aimCorrelationResult(mergedCorrelation)
                .reasons(mergedReasons)
                .build();
    }

    private CombatHitResult mergeTargetSwitch(CombatHitResult current, TargetSwitchResult targetSwitch) {
        if (current == null) {
            return null;
        }
        if (targetSwitch == null) {
            targetSwitch = TargetSwitchResult.EMPTY;
        }

        List<String> mergedReasons = new ArrayList<String>(current.getReasons());
        mergedReasons.addAll(targetSwitch.getReasons());

        return copyFrom(current)
                .targetSwitchResult(targetSwitch)
                .reasons(mergedReasons)
                .build();
    }

    private CombatHitResult finalizeScores(CombatHitResult merged, CombatSample sample, VezAntiCheat plugin) {
        if (merged == null || sample == null) {
            return merged;
        }

        boolean knockbackLeniencyApplied = shouldApplyAttackerKnockbackLeniency(merged, sample);
        List<String> mergedReasons = new ArrayList<String>(merged.getReasons());
        if (knockbackLeniencyApplied) {
            mergedReasons.add("recent knockback leniency applied");
        }

        HitboxExpansionTier tier = merged.getExpansionTier();
        CombatHitClassification classification = merged.getClassification();
        if (classification == null) {
            classification = CombatHitClassification.CLEAN;
        }

        double geometryScore = CombatScoreComposer.computeGeometryScore(
                tier,
                classification,
                merged.getPing(),
                merged.isPingCompensated(),
                merged.isLineOfSightValid(),
                config);
        double behaviorScore = CombatScoreComposer.sumBehaviorScore(
                merged.getPreAimResult(),
                merged.getAccuracySpikeResult(),
                merged.getAimCorrelationResult(),
                merged.getTargetSwitchResult());
        if (classification != CombatHitClassification.IMPOSSIBLE) {
            behaviorScore += evaluateRequiredRotationBehavior(sample);
        }

        // Recent KB/damage explains erratic aim: reduce behavior scores only, not geometry; IMPOSSIBLE unchanged.
        double reducedBehavior = behaviorScore;
        if (knockbackLeniencyApplied) {
            reducedBehavior *= config.getKnockbackBehaviorMultiplier();
        }

        double geometryMultiplier = plugin == null ? 1.0D : CombatFalsePositiveGuard.getGeometryMultiplier(plugin, sample);
        double behaviorMultiplier = plugin == null ? 1.0D : CombatFalsePositiveGuard.getBehaviorMultiplier(plugin, sample);
        double globalMultiplier = plugin == null ? 1.0D : CombatFalsePositiveGuard.getFalsePositiveMultiplier(plugin, sample);

        double adjustedGeometry = geometryScore * geometryMultiplier * globalMultiplier;
        double adjustedBehavior = reducedBehavior * behaviorMultiplier * globalMultiplier;
        double baseScore = CombatScoreComposer.computeBaseScore(geometryScore, behaviorScore);
        double computedFinal = Math.max(0.0D, adjustedGeometry) + Math.max(0.0D, adjustedBehavior);
        double finalScore = Math.max(
                computedFinal,
                CombatScoreComposer.classificationFloor(classification, tier, config));

        if (globalMultiplier < 1.0D || geometryMultiplier < 1.0D || behaviorMultiplier < 1.0D) {
            mergedReasons.add("falsePositiveMultiplier="
                    + roundMultiplier(globalMultiplier * Math.min(geometryMultiplier, behaviorMultiplier)));
        }

        return copyFrom(merged)
                .baseScore(baseScore)
                .finalScore(finalScore)
                .reasons(mergedReasons)
                .build();
    }

    private static double roundMultiplier(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private double evaluateRequiredRotationBehavior(CombatSample sample) {
        if (sample == null) {
            return 0.0D;
        }
        Location eye = sample.getAttackerEye();
        Location targetBase = sample.getClassificationTargetLocation();
        if (eye == null || targetBase == null) {
            return 0.0D;
        }

        double extraTolerance = sample.isRewoundValid() ? 0.0D : 2.0D;
        BoundingBox box = BoundingBox.fromFeet(targetBase, sample.getTargetWidth(), sample.getTargetHeight());
        RequiredRotationUtil.Result rotation = RequiredRotationUtil.evaluate(
                eye,
                sample.getAttackerYaw(),
                sample.getAttackerPitch(),
                box,
                sample.getPingEstimate(),
                extraTolerance);

        PlayerCombatData attackerData = getCombatData(sample.getAttackerUuid());
        if (attackerData != null) {
            attackerData.recordRequiredRotationError(rotation.getCombinedError(), sample.getTimestampMs());
            attackerData.decaySuspiciousTargetSwitch(sample.getTimestampMs());
        }

        if (attackerData == null
                || attackerData.getRequiredRotationSampleCount() < config.getRequiredRotationMinSamples()) {
            return rotation.exceedsTolerance() ? 0.75D : 0.0D;
        }

        double median = attackerData.medianRequiredRotationError();
        double allowance = rotation.getHitboxAngularRadius() + rotation.getPingTolerance();
        if (median <= allowance + config.getRequiredRotationMedianThreshold()) {
            return 0.0D;
        }
        return Math.min(2.5D, (median - allowance) / 5.0D);
    }

    private boolean shouldApplyAttackerKnockbackLeniency(CombatHitResult result, CombatSample sample) {
        if (result == null || sample == null) {
            return false;
        }
        // IMPOSSIBLE hits keep full suspicion regardless of recent knockback.
        if (result.getClassification() == CombatHitClassification.IMPOSSIBLE) {
            return false;
        }

        PlayerCombatData attackerData = getCombatData(sample.getAttackerUuid());
        long windowMs = config.getKnockbackLeniencyWindowMs();
        long attackTime = sample.getTimestampMs();
        return attackerData != null
                && (attackerData.recentlyVelocity(attackTime, windowMs)
                || attackerData.recentlyDamaged(attackTime, windowMs));
    }

    private static CombatHitResult.Builder copyFrom(CombatHitResult result) {
        return CombatHitResult.builder()
                .attackerUuid(result.getAttackerUuid())
                .targetUuid(result.getTargetUuid())
                .classification(result.getClassification())
                .baseScore(result.getBaseScore())
                .finalScore(result.getFinalScore())
                .reachDistance(result.getReachDistance())
                .yawError(result.getYawError())
                .pitchError(result.getPitchError())
                .normalHitboxHit(result.isNormalHitboxHit())
                .smallExpansionHit(result.isSmallExpansionHit())
                .fullExpansionHit(result.isFullExpansionHit())
                .lineOfSightValid(result.isLineOfSightValid())
                .ping(result.getPing())
                .allowedExpansion(result.getAllowedExpansion())
                .expansionTier(result.getExpansionTier())
                .pingCompensated(result.isPingCompensated())
                .preAimResult(result.getPreAimResult())
                .accuracySpikeResult(result.getAccuracySpikeResult())
                .aimCorrelationResult(result.getAimCorrelationResult())
                .hitPoint(result.getHitPoint())
                .hitPointData(result.getHitPointData())
                .targetSwitchResult(result.getTargetSwitchResult())
                .reasons(result.getReasons());
    }

    public CombatCancelDecision resolveCancellation(CombatHitResult result, CombatEvidence evidence) {
        return resolveCancellation(result, evidence, false);
    }

    public CombatCancelDecision resolveCancellation(CombatHitResult result, CombatEvidence evidence,
                                                      boolean lenientImpossibleRaytrace) {
        double preBuffer = evidence == null ? 0.0D : evidence.getBuffer();
        return CombatCancelDecision.resolve(
                result, preBuffer, config, cancelImpossibleHits, lenientImpossibleRaytrace);
    }

    public CombatAction getRecommendedAction(CombatHitResult result, CombatEvidence evidence) {
        if (result == null) {
            return CombatAction.ALLOW;
        }

        if (resolveCancellation(result, evidence).shouldCancel()) {
            return CombatAction.CANCEL;
        }

        double preBuffer = evidence == null ? 0.0D : evidence.getBuffer();

        // Tier order: CANCEL (above) for impossible/high buffer BAD, ALERT at flag buffer, PUNISH at punish buffer.
        if (preBuffer >= config.getPunishBuffer()) {
            return CombatAction.PUNISH;
        }
        if (preBuffer >= config.getFlagBuffer()) {
            return CombatAction.ALERT;
        }
        return CombatAction.ALLOW;
    }

    public boolean shouldCancelHit(UUID attackerId, CombatHitResult result) {
        if (attackerId == null || result == null) {
            return false;
        }
        CombatEvidence evidence = getEvidence(attackerId);
        return resolveCancellation(result, evidence).shouldCancel();
    }

    public void recordResult(CombatHitResult result) {
        if (result == null || result.getAttackerUuid() == null) {
            return;
        }
        getOrCreateEvidence(result.getAttackerUuid()).addResult(result);
    }

    public void recordRotation(UUID playerId, float yaw, float pitch, long timestampMs) {
        PlayerCombatData data = getOrCreateCombatData(playerId);
        if (data != null) {
            data.addRotation(yaw, pitch, timestampMs);
        }
    }

    public void recordMovement(UUID playerId, Location location, boolean onGround, long timestampMs) {
        PlayerCombatData data = getOrCreateCombatData(playerId);
        if (data != null) {
            data.addMovement(location, onGround, timestampMs);
        }
    }

    public void recordAttack(UUID playerId, UUID target, long timestampMs, float yaw, float pitch,
                             double reach, CombatHitClassification classification) {
        // Attack history enables target-switch detection on the next hit.
        PlayerCombatData data = getOrCreateCombatData(playerId);
        if (data != null) {
            data.addAttack(target, timestampMs, yaw, pitch, reach, classification);
        }
    }

    public void tickDecay(long nowMs) {
        for (CombatEvidence evidence : evidenceByAttacker.values()) {
            evidence.decayBuffer(nowMs);
        }
    }
}
