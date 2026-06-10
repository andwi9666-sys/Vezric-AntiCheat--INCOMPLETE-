package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
import com.colin.vezanticheat.combat.CombatSample;
import com.colin.vezanticheat.combat.math.AngleUtil;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Correlates attacker yaw tracking with target movement across recent pre-attack ticks.
 * A single fast flick is normal in PvP; suspicion requires sustained failure to track
 * target angular movement over several pre-attack samples (scored in {@link com.colin.vezanticheat.combat.CombatAnalyzer}).
 */
public final class AimCorrelationAnalyzer {

    private static final double EYE_HEIGHT = 1.62D;
    private static final double AIM_HEIGHT_RATIO = 0.5D;

    private AimCorrelationAnalyzer() {}

    public static AimCorrelationResult analyze(CombatSample sample, CombatConfig config) {
        if (sample == null || config == null) {
            return AimCorrelationResult.EMPTY;
        }

        int ticks = config.getAimCorrelationTicks();
        List<CombatSample.MovementPoint> attackerMoves = selectPreAttackMovements(
                sample.getRecentMovements(), sample.getTimestampMs(), ticks);
        List<CombatSample.MovementPoint> targetMoves = selectPreAttackMovements(
                sample.getRecentTargetMovements(), sample.getTimestampMs(), ticks);
        List<CombatSample.RotationPoint> rotations = selectPreAttackRotations(sample, ticks);

        if (attackerMoves.size() < 2 || targetMoves.size() < 2 || rotations.size() < 2) {
            return AimCorrelationResult.EMPTY;
        }

        Location targetFallback = sample.getTargetLocation();
        double targetHeight = sample.getTargetHeight();
        double aimYOffset = targetHeight * AIM_HEIGHT_RATIO;

        List<Float> requiredYaws = new ArrayList<Float>();
        for (CombatSample.MovementPoint attackerMove : attackerMoves) {
            Vector eye = new Vector(
                    attackerMove.getX(),
                    attackerMove.getY() + EYE_HEIGHT,
                    attackerMove.getZ());
            CombatSample.MovementPoint targetMove = nearestTargetAtOrBefore(targetMoves, attackerMove.getTimeMs());
            double targetX;
            double targetY;
            double targetZ;
            if (targetMove != null) {
                targetX = targetMove.getX();
                targetY = targetMove.getY();
                targetZ = targetMove.getZ();
            } else if (targetFallback != null) {
                targetX = targetFallback.getX();
                targetY = targetFallback.getY();
                targetZ = targetFallback.getZ();
            } else {
                return AimCorrelationResult.EMPTY;
            }

            Vector aimPoint = new Vector(targetX, targetY + aimYOffset, targetZ);
            requiredYaws.add(AngleUtil.getYawTo(eye, aimPoint));
        }

        float targetAngularChange = AngleUtil.angleDifference(requiredYaws.get(0), requiredYaws.get(requiredYaws.size() - 1));

        double attackerYawChange = 0.0D;
        for (int i = 1; i < rotations.size(); i++) {
            attackerYawChange += AngleUtil.angleDifference(
                    rotations.get(i - 1).getYaw(),
                    rotations.get(i).getYaw());
        }

        double correlationScore = attackerYawChange / Math.max(targetAngularChange, 1.0D);

        List<String> reasons = new ArrayList<String>();
        reasons.add("targetAngular=" + round(targetAngularChange));
        reasons.add("attackerYawDelta=" + round(attackerYawChange));
        reasons.add("correlation=" + round(correlationScore));

        return AimCorrelationResult.builder()
                .enoughData(true)
                .targetAngularChange(targetAngularChange)
                .attackerYawChange(attackerYawChange)
                .correlationScore(correlationScore)
                .suspicious(false)
                .suspiciousScore(0.0D)
                .reasons(reasons)
                .build();
    }

    private static List<CombatSample.MovementPoint> selectPreAttackMovements(
            List<CombatSample.MovementPoint> movements, long attackTime, int ticks) {
        List<CombatSample.MovementPoint> result = new ArrayList<CombatSample.MovementPoint>();
        if (movements == null || movements.isEmpty() || ticks <= 0) {
            return result;
        }

        List<CombatSample.MovementPoint> beforeAttack = new ArrayList<CombatSample.MovementPoint>();
        for (CombatSample.MovementPoint movement : movements) {
            if (movement != null && movement.getTimeMs() < attackTime) {
                beforeAttack.add(movement);
            }
        }

        int start = Math.max(0, beforeAttack.size() - ticks);
        return new ArrayList<CombatSample.MovementPoint>(beforeAttack.subList(start, beforeAttack.size()));
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

    private static CombatSample.MovementPoint nearestTargetAtOrBefore(
            List<CombatSample.MovementPoint> targetMoves, long timestampMs) {
        CombatSample.MovementPoint nearest = null;
        for (CombatSample.MovementPoint movement : targetMoves) {
            if (movement == null || movement.getTimeMs() > timestampMs) {
                continue;
            }
            if (nearest == null || movement.getTimeMs() > nearest.getTimeMs()) {
                nearest = movement;
            }
        }
        return nearest;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
