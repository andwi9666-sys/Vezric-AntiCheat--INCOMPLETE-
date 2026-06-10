package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.math.AngleUtil;
import com.colin.vezanticheat.combat.math.BoundingBox;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-attack rotation history analysis for silent-aim evidence.
 * Silent aim often shows no tracking before the hit then perfect attack yaw; the signal
 * is the pre-attack window, not the attack packet alone.
 */
public final class PreAimAnalyzer {

    private static final double ATTACK_AIM_SNAP_YAW = 6.0D;
    private static final double HIGH_PRE_AIM_AVG_YAW = 25.0D;
    private static final double NO_NEAR_TARGET_SCORE = 1.25D;
    private static final double SNAP_ON_ATTACK_SCORE = 1.5D;

    private PreAimAnalyzer() {}

    public static PreAimResult analyze(CombatSample sample, CombatConfig config) {
        if (sample == null || config == null) {
            return PreAimResult.EMPTY;
        }

        Location eye = sample.getAttackerEye();
        Location targetBase = sample.getTargetLocation();
        if (eye == null || targetBase == null) {
            return PreAimResult.EMPTY;
        }

        // History window before attack time is the pre-aim signal, not attack-packet aim alone.
        List<CombatSample.RotationPoint> preAttack = selectPreAttackRotations(sample, config.getPreAimTicks());
        if (preAttack.isEmpty()) {
            return PreAimResult.EMPTY;
        }

        BoundingBox targetBox = BoundingBox.fromFeet(
                targetBase, sample.getTargetWidth(), sample.getTargetHeight());

        double sumYaw = 0.0D;
        double sumPitch = 0.0D;
        double closestYaw = Double.MAX_VALUE;
        double closestPitch = Double.MAX_VALUE;
        int nearTarget = 0;
        int strongPreAim = 0;

        double nearTargetYaw = config.getPreAimNearYawError();
        double nearTargetPitch = config.getPreAimNearPitchError();
        double strongPreAimYaw = config.getPreAimStrongYawError();

        for (CombatSample.RotationPoint rotation : preAttack) {
            AngleUtil.YawPitchError error = AngleUtil.getYawPitchError(
                    eye, rotation.getYaw(), rotation.getPitch(), targetBox);
            double yawError = error.getYawError();
            double pitchError = error.getPitchError();

            sumYaw += yawError;
            sumPitch += pitchError;
            closestYaw = Math.min(closestYaw, yawError);
            closestPitch = Math.min(closestPitch, pitchError);

            // nearTarget / strongPreAim count how many pre-attack ticks were already on target.
            if (yawError <= nearTargetYaw && pitchError <= nearTargetPitch) {
                nearTarget++;
            }
            if (yawError <= strongPreAimYaw) {
                strongPreAim++;
            }
        }

        int checked = preAttack.size();
        double avgYaw = sumYaw / checked;
        double avgPitch = sumPitch / checked;

        AngleUtil.YawPitchError attackError = AngleUtil.getYawPitchError(
                eye, sample.getAttackerYaw(), sample.getAttackerPitch(), targetBox);
        double attackYawError = attackError.getYawError();

        double score = 0.0D;
        List<String> reasons = new ArrayList<String>();
        reasons.add("preAimSamples=" + checked);
        reasons.add("preAimNear=" + nearTarget);

        if (nearTarget == 0) {
            score += NO_NEAR_TARGET_SCORE;
            reasons.add("No pre-aim samples near target");
        }

        if (avgYaw > HIGH_PRE_AIM_AVG_YAW && attackYawError < ATTACK_AIM_SNAP_YAW) {
            score += SNAP_ON_ATTACK_SCORE;
            reasons.add("Pre-attack aim far off but attack aim snapped on target");
        }

        if (strongPreAim > 0) {
            reasons.add("strongPreAim=" + strongPreAim);
        }

        return PreAimResult.builder()
                .hadPreAim(nearTarget > 0)
                .samplesChecked(checked)
                .samplesNearTarget(nearTarget)
                .averageYawError(avgYaw)
                .averagePitchError(avgPitch)
                .closestYawError(closestYaw)
                .closestPitchError(closestPitch)
                .suspiciousScore(score)
                .reasons(reasons)
                .build();
    }

    private static List<CombatSample.RotationPoint> selectPreAttackRotations(CombatSample sample, int preAimTicks) {
        List<CombatSample.RotationPoint> result = new ArrayList<CombatSample.RotationPoint>();
        if (sample == null || preAimTicks <= 0) {
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

        int start = Math.max(0, beforeAttack.size() - preAimTicks);
        return new ArrayList<CombatSample.RotationPoint>(beforeAttack.subList(start, beforeAttack.size()));
    }
}
