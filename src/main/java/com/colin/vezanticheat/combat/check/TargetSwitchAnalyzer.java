package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
import com.colin.vezanticheat.combat.CombatSample;
import com.colin.vezanticheat.combat.PreAimAnalyzer;
import com.colin.vezanticheat.combat.PreAimResult;
import com.colin.vezanticheat.combat.history.AttackSample;
import com.colin.vezanticheat.combat.math.AngleUtil;
import com.colin.vezanticheat.combat.math.BoundingBox;
import com.colin.vezanticheat.combat.math.RayTraceUtil;
import com.colin.vezanticheat.data.PlayerCombatData;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Detects unrealistic instant target switches with perfect accuracy.
 * Requires prior attack history, angle between targets, switch timing, and pre-aim
 * toward the new target; instant perfect hits after large angle switches are rare.
 */
public final class TargetSwitchAnalyzer {

    private static final double MODERATE_SCORE = 1.5D;
    private static final double STRONG_SCORE = 2.25D;

    private TargetSwitchAnalyzer() {}

    public static TargetSwitchResult analyze(CombatSample sample, PlayerCombatData combatData,
                                             CombatConfig config) {
        if (sample == null || config == null || combatData == null) {
            return TargetSwitchResult.EMPTY;
        }
        if (!config.isTargetSwitchEnabled()) {
            return TargetSwitchResult.EMPTY;
        }

        long moderateSwitchMs = config.getTargetSwitchQuickMs();
        long strongSwitchMs = config.getTargetSwitchVeryQuickMs();
        double minTargetAngle = config.getTargetSwitchLargeAngle();
        double perfectHitYaw = config.getTargetSwitchPerfectHitYawError();

        UUID currentTarget = sample.getTargetUuid();
        if (currentTarget == null) {
            return TargetSwitchResult.EMPTY;
        }

        List<AttackSample> recentAttacks = combatData.getRecentAttacks(1);
        if (recentAttacks.isEmpty()) {
            return TargetSwitchResult.EMPTY;
        }

        AttackSample previous = recentAttacks.get(0);
        UUID previousTarget = previous.getTargetUuid();
        if (previousTarget == null || previousTarget.equals(currentTarget)) {
            return TargetSwitchResult.builder()
                    .switchedTarget(false)
                    .previousTarget(previousTarget)
                    .currentTarget(currentTarget)
                    .suspicious(false)
                    .suspiciousScore(0.0D)
                    .addReason("targetSwitch=same_target")
                    .build();
        }

        Location eye = sample.getAttackerEye();
        Location targetBase = sample.getClassificationTargetLocation();
        if (eye == null || targetBase == null) {
            return TargetSwitchResult.EMPTY;
        }

        long timeSinceLastTarget = Math.max(0L, sample.getTimestampMs() - previous.getTimestamp());
        double angleBetweenTargets = computeAngleBetweenTargets(previous, eye, targetBase,
                sample.getTargetWidth(), sample.getTargetHeight());

        BoundingBox targetBox = BoundingBox.fromFeet(
                targetBase, sample.getTargetWidth(), sample.getTargetHeight());
        double attackYawError = AngleUtil.getYawPitchError(
                eye, sample.getAttackerYaw(), sample.getAttackerPitch(), targetBox).getYawError();

        PreAimResult preAim = PreAimAnalyzer.analyze(sample, config);
        boolean previousTargetValid = isPreviousTargetValid(sample.getAttacker(), previousTarget);

        List<String> reasons = new ArrayList<String>();
        reasons.add("switchMs=" + timeSinceLastTarget);
        reasons.add("targetAngle=" + round(angleBetweenTargets));
        reasons.add("attackYaw=" + round(attackYawError));
        reasons.add("preAim=" + preAim.hadPreAim());

        double score = 0.0D;
        if (timeSinceLastTarget < strongSwitchMs
                && angleBetweenTargets > minTargetAngle
                && attackYawError <= perfectHitYaw) {
            score = STRONG_SCORE;
            reasons.add("Very quick target switch: large angle with perfect hit");
        } else if (timeSinceLastTarget < moderateSwitchMs
                && angleBetweenTargets > minTargetAngle
                && attackYawError <= perfectHitYaw) {
            score = MODERATE_SCORE;
            reasons.add("Instant target switch: large angle with perfect hit");
        }

        boolean suspiciousSwitch = score > 0.0D;
        if (suspiciousSwitch) {
            combatData.recordSuspiciousTargetSwitch(sample.getTimestampMs());
            int switchCount = combatData.getSuspiciousTargetSwitchCount();
            reasons.add("suspiciousSwitchCount=" + switchCount);
            if (switchCount >= 3) {
                score *= 1.25D;
                reasons.add("Repeated suspicious target switches in 30s window");
            }
        }

        // Pre-aim toward the new target halves suspicion only when the switch is not already suspicious.
        if (preAim.hadPreAim() && score > 0.0D && combatData.getSuspiciousTargetSwitchCount() < 2) {
            score *= 0.5D;
            reasons.add("Pre-aim toward new target reduces switch suspicion");
        }

        // Prior target left the fight or world: do not flag a contextless switch.
        if (!previousTargetValid) {
            score = 0.0D;
            reasons.add("targetSwitch=previous_target_invalid");
        }

        return TargetSwitchResult.builder()
                .switchedTarget(true)
                .previousTarget(previousTarget)
                .currentTarget(currentTarget)
                .timeSinceLastTarget(timeSinceLastTarget)
                .angleBetweenTargets(angleBetweenTargets)
                .suspicious(score > 0.0D)
                .suspiciousScore(score)
                .reasons(reasons)
                .build();
    }

    private static double computeAngleBetweenTargets(AttackSample previous, Location eye,
                                                     Location targetBase, double width, double height) {
        Vector oldDirection = RayTraceUtil.getLookVector(previous.getYaw(), previous.getPitch());

        BoundingBox targetBox = BoundingBox.fromFeet(targetBase, width, height);
        Vector eyeVector = eye.toVector();
        Vector aimPoint = targetBox.closestPoint(eyeVector);
        Vector newDirection = aimPoint.subtract(eyeVector);
        if (newDirection.lengthSquared() <= 1.0E-8) {
            return 0.0D;
        }
        newDirection.normalize();

        double dot = oldDirection.dot(newDirection);
        dot = Math.max(-1.0D, Math.min(1.0D, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean isPreviousTargetValid(Player attacker, UUID previousTarget) {
        if (attacker == null || previousTarget == null || attacker.getServer() == null) {
            return false;
        }

        Player previous = attacker.getServer().getPlayer(previousTarget);
        return previous != null
                && previous.isOnline()
                && !previous.isDead()
                && previous.getWorld() != null
                && attacker.getWorld() != null
                && previous.getWorld().equals(attacker.getWorld());
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
