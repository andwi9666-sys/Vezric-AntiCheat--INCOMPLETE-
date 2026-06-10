package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
import com.colin.vezanticheat.combat.CombatHitResult;
import com.colin.vezanticheat.combat.CombatSample;
import com.colin.vezanticheat.combat.math.AngleUtil;
import com.colin.vezanticheat.combat.math.BoundingBox;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects aim that is only accurate on the attack packet while tracking is poor.
 * Compares average pre-attack tracking error to attack-packet accuracy; flags when
 * crosshair was loose then suddenly perfect on the hit.
 */
public final class AccuracySpikeAnalyzer {

    private static final double NEAR_CROSSHAIR_YAW = 6.0D;
    private static final int NEAR_CROSSHAIR_SAMPLES = 3;
    private static final double MODERATE_SCORE = 1.5D;
    private static final double STRONG_SCORE = 2.25D;

    private AccuracySpikeAnalyzer() {}

    public static AccuracySpikeResult analyze(CombatSample sample, CombatHitResult currentResult,
                                            CombatConfig config) {
        if (sample == null || config == null) {
            return AccuracySpikeResult.EMPTY;
        }
        if (!config.isAccuracySpikeEnabled()) {
            return AccuracySpikeResult.EMPTY;
        }

        double consistentTrackingThreshold = config.getAccuracySpikeTrackingErrorThreshold();
        double moderateAttackYaw = config.getAccuracySpikeAttackErrorThreshold();
        double strongAvgYaw = config.getAccuracySpikeSevereTrackingErrorThreshold();
        double strongAttackYaw = config.getAccuracySpikeSevereAttackErrorThreshold();
        double moderateAvgYaw = consistentTrackingThreshold;

        Location eye = sample.getAttackerEye();
        Location targetBase = sample.getTargetLocation();
        if (eye == null || targetBase == null) {
            return AccuracySpikeResult.EMPTY;
        }

        List<CombatSample.RotationPoint> tracking = selectPreAttackRotations(
                sample, config.getAccuracySpikeTicks());
        if (tracking.isEmpty()) {
            return AccuracySpikeResult.EMPTY;
        }

        BoundingBox targetBox = BoundingBox.fromFeet(
                targetBase, sample.getTargetWidth(), sample.getTargetHeight());

        double sumYaw = 0.0D;
        int nearCrosshair = 0;

        for (CombatSample.RotationPoint rotation : tracking) {
            AngleUtil.YawPitchError error = AngleUtil.getYawPitchError(
                    eye, rotation.getYaw(), rotation.getPitch(), targetBox);
            double yawError = error.getYawError();
            sumYaw += yawError;
            if (yawError <= NEAR_CROSSHAIR_YAW) {
                nearCrosshair++;
            }
        }

        int checked = tracking.size();
        double avgYaw = sumYaw / checked;
        double attackYawError = resolveAttackYawError(sample, currentResult, eye, targetBox);
        double ratio = avgYaw > 0.0D ? attackYawError / avgYaw : 0.0D;

        List<String> reasons = new ArrayList<String>();
        reasons.add("trackingAvg=" + round(avgYaw));
        reasons.add("attackYaw=" + round(attackYawError));
        reasons.add("ratio=" + round(ratio));

        if (avgYaw < consistentTrackingThreshold || nearCrosshair >= NEAR_CROSSHAIR_SAMPLES) {
            reasons.add("accuracySpike=consistent_tracking");
            return AccuracySpikeResult.builder()
                    .averageTrackingYawError(avgYaw)
                    .attackYawError(attackYawError)
                    .ratio(ratio)
                    .spikeDetected(false)
                    .suspiciousScore(0.0D)
                    .reasons(reasons)
                    .build();
        }

        double score = 0.0D;
        boolean detected = false;

        // Strong/moderate branches: poor pre-attack tracking ratio to near-perfect hit aim.
        if (avgYaw >= strongAvgYaw && attackYawError <= strongAttackYaw) {
            score = STRONG_SCORE;
            detected = true;
            reasons.add("Strong accuracy spike: very poor tracking, near-perfect attack aim");
        } else if (avgYaw >= moderateAvgYaw && attackYawError <= moderateAttackYaw) {
            score = MODERATE_SCORE;
            detected = true;
            reasons.add("Accuracy spike: poor tracking before attack, perfect on hit");
        }

        return AccuracySpikeResult.builder()
                .averageTrackingYawError(avgYaw)
                .attackYawError(attackYawError)
                .ratio(ratio)
                .spikeDetected(detected)
                .suspiciousScore(score)
                .reasons(reasons)
                .build();
    }

    private static double resolveAttackYawError(CombatSample sample, CombatHitResult currentResult,
                                                Location eye, BoundingBox targetBox) {
        if (currentResult != null) {
            return currentResult.getYawError();
        }
        return AngleUtil.getYawPitchError(
                eye, sample.getAttackerYaw(), sample.getAttackerPitch(), targetBox).getYawError();
    }

    private static List<CombatSample.RotationPoint> selectPreAttackRotations(CombatSample sample, int ticks) {
        List<CombatSample.RotationPoint> result = new ArrayList<CombatSample.RotationPoint>();
        if (sample == null || ticks <= 0) {
            return result;
        }

        long attackTime = sample.getTimestampMs();
        List<CombatSample.RotationPoint> rotations = sample.getRecentRotations();
        if (rotations == null || rotations.isEmpty()) {
            return result;
        }

        List<CombatSample.RotationPoint> beforeAttack = new ArrayList<CombatSample.RotationPoint>();
        for (CombatSample.RotationPoint rotation : rotations) {
            if (rotation != null && rotation.getTimeMs() < attackTime) {
                beforeAttack.add(rotation);
            }
        }

        int start = Math.max(0, beforeAttack.size() - ticks);
        return new ArrayList<CombatSample.RotationPoint>(beforeAttack.subList(start, beforeAttack.size()));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
