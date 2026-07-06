package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatUtil;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Shared silent-aim scoring utilities for Characteristics tier checks.
 *
 * <p>Holds the NEW transient-fingerprint scorers (snap-restore, impossible-velocity-onto-hitbox,
 * center-lock, weighted fusion) alongside the preserved statistical scorers. Pure math, no hooks,
 * never raises violations. Every method exercised by the JUnit tests keeps its exact body.
 */
public final class CharSilentAimSignals {

    private CharSilentAimSignals() {}

    /** RT2-004: boost correlation/center/required-rotation weights below taper distance. */
    public static double closeRangeSignalScale(double dist, double taperBlocks, double boost) {
        if (taperBlocks <= 0.0D || dist >= taperBlocks) return 1.0D;
        double t = Math.max(0.0D, dist / taperBlocks);
        return 1.0D + (boost - 1.0D) * (1.0D - t);
    }

    public static double requiredRotationScore(double excessBeyondAllowanceDeg) {
        if (excessBeyondAllowanceDeg <= 0.0D) return 0.0D;
        return Math.min(1.0D, excessBeyondAllowanceDeg / 18.0D);
    }

    // ===================== NEW transient-fingerprint scorers =====================

    /**
     * Pure passthrough/normalizer for a {@code SilentAimAnalyzer} snap-restore severity. Lets the
     * check and tests map an analyzer score into the fusion without importing analyzer internals.
     */
    public static double snapRestoreScore(double rawAnalyzerScore) {
        return clamp01(rawAnalyzerScore);
    }

    /**
     * Impossible-velocity-onto-hitbox -> score (signal B, incl. 180 snaps). 0 when the landing step
     * velocity is within the ping-scaled snap threshold; otherwise ramps over a 600 deg/sec span.
     */
    public static double hitboxSnapVelocityScore(double velDegPerSec, int pingMs) {
        double threshold = RequiredRotationUtil.snapAngularVelocityThreshold(pingMs);
        if (velDegPerSec <= threshold) return 0.0D;
        return Math.min(1.0D, (velDegPerSec - threshold) / 600.0D);
    }

    /**
     * Center-lock (aim lands dead-center, not nearest point) combined with zero sub-degree jitter.
     * Returns 0 unless BOTH the center margin and the jitter are unnaturally small; otherwise scales
     * toward 1 as both shrink.
     */
    public static double centerLockScore(double avgCenterMarginDeg, double avgJitterDeg) {
        if (avgCenterMarginDeg > 1.0D || avgJitterDeg > 0.05D) return 0.0D;
        double marginPart = 1.0D - clamp01(avgCenterMarginDeg / 1.0D);
        double jitterPart = 1.0D - clamp01(avgJitterDeg / 0.05D);
        return clamp01(0.5D * marginPart + 0.5D * jitterPart);
    }

    /**
     * Central weighted fusion of all seven silent-aim signals -> combinedScore in 0..1. Divides by a
     * fixed denominator so no single weak signal can dominate. The CHECK (not this method) enforces
     * the "&gt;=2 strong transient signals + 1 corroborating" rule, so this stays a pure helper tests
     * can exercise deterministically.
     */
    public static double fuse(double snapRestore, double hitboxVel, double centerLock, double desync,
                              double lattice, double exclusivity, double linearRamp) {
        double weighted = clamp01(snapRestore) * 2.0D
                + clamp01(hitboxVel) * 1.8D
                + clamp01(centerLock) * 1.0D
                + clamp01(desync) * 1.0D
                + clamp01(lattice) * 1.0D
                + clamp01(exclusivity) * 1.2D
                + clamp01(linearRamp) * 0.8D;
        // Fixed denominator (sum of weights) keeps the result in 0..1 and prevents single-signal dominance.
        return clamp01(weighted / 8.8D);
    }

    /**
     * Counts how many of the two STRONG transient signals exceed 0.30. The check requires this &gt;=2
     * (OR a sustained snap-restore buffer) before flagging.
     */
    public static int strongSignalCount(double snapRestore, double hitboxVel) {
        int n = 0;
        if (snapRestore > 0.30D) n++;
        if (hitboxVel > 0.30D) n++;
        return n;
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }

    // ===================== legacy aggregator (re-pointed at stored transient values) =====================

    public static double score(VezAntiCheat plugin, Player p, PlayerData data, long now) {
        if (data == null) return 0.0D;
        double snapRestore = data.getSilentLastSnapRestoreScore();
        double hitboxVel = hitboxSnapVelocityScore(data.getSilentLastHitboxSnapVelDegPerSec(),
                Math.max(0, com.colin.vezanticheat.utils.PingUtil.getPing(p)));
        double centerLock = centerScore(data);
        double desync = movementMismatchScore(data);
        double lattice = aimAssistScore(data, "aimassista");
        double exclusivity = correlationScore(data);
        double linearRamp = 0.0D;
        Deque<Double> centerErrs = data.getKillAuraACenterErrors();
        if (centerErrs != null && centerErrs.size() >= 3) {
            double[] errs = new double[centerErrs.size()];
            int i = 0;
            for (Double d : centerErrs) errs[i++] = d == null ? 0.0D : d;
            linearRamp = com.colin.vezanticheat.utils.GcdLatticeAnalysis.distributedSnapRatio(errs);
        }
        return fuse(snapRestore, hitboxVel, centerLock, desync, lattice, exclusivity, linearRamp);
    }

    public static double angularScore(Player p, PlayerData data) {
        if (p == null || data == null) return 0.0D;
        org.bukkit.entity.Entity target = data.getLastTargetEntity();
        if (target == null) return 0.0D;
        org.bukkit.Location eye = p.getEyeLocation();
        double angle = CombatUtil.angleToEntity(eye, target, data.getPacketYaw(), data.getPacketPitch());
        return angle > 25.0D ? Math.min(1.0D, angle / 45.0D) : 0.0D;
    }

    public static double snapScore(PlayerData data) {
        if (data == null) return 0.0D;
        return Math.max(Math.min(1.0D, data.getKillAuraASnapRatio()), data.getSilentLastSnapRestoreScore());
    }

    public static double resetScore(PlayerData data) {
        if (data == null || !data.isKillAuraAPostResetActive()) return 0.0D;
        return 0.65D;
    }

    public static double movementMismatchScore(PlayerData data) {
        Deque<Double> angles = data.getKillAuraAMismatchAngles();
        if (angles == null || angles.isEmpty()) return 0.0D;
        double sum = 0.0D;
        for (Double d : angles) sum += d == null ? 0.0D : d;
        return Math.min(1.0D, sum / angles.size() / 45.0D);
    }

    public static double centerScore(PlayerData data) {
        Deque<Double> errors = data.getKillAuraACenterErrors();
        if (errors == null || errors.size() < 4) return 0.0D;
        double sum = 0.0D;
        for (Double d : errors) sum += d == null ? 0.0D : d;
        double avg = sum / errors.size();
        return avg < 0.08D ? 0.7D : 0.0D;
    }

    public static double correlationScore(PlayerData data) {
        int attack = data.getKillAuraARotOnAttackTicks();
        int nonAttack = data.getKillAuraARotOnNonAttackTicks();
        int total = attack + nonAttack + data.getKillAuraANoRotOnNonAttackTicks();
        if (total < 40) return 0.0D;
        double ratio = attack / (double) Math.max(1, total);
        return ratio > 0.55D ? Math.min(1.0D, ratio) : 0.0D;
    }

    public static double switchScore(PlayerData data) {
        return Math.min(1.0D, data.getKillAuraASwitchBuffer() / 6.0D);
    }

    public static double cpsScore(PlayerData data, long now) {
        Deque<Long> intervals = data.getAttackIntervals();
        if (intervals == null || intervals.size() < 8) return 0.0D;
        double avg = 0.0D;
        for (Long ms : intervals) avg += ms == null ? 0.0D : ms;
        avg /= intervals.size();
        double cps = avg <= 0.0D ? 0.0D : 1000.0D / avg;
        return cps > 16.0D ? Math.min(1.0D, (cps - 16.0D) / 8.0D) : 0.0D;
    }

    public static double timerScore(PlayerData data, long now) {
        long last = data.getLastFlyingPacket();
        if (last <= 0L) return 0.0D;
        long gap = now - last;
        return gap < 35L || gap > 70L ? 0.35D : 0.0D;
    }

    public static double velocityScore(PlayerData data) {
        return data.getPartialKbRatio() > 0.65D ? data.getPartialKbRatio() : 0.0D;
    }

    public static double combatTimingScore(PlayerData data, long now) {
        long lastHit = data.getLastDamageTakenMs();
        if (lastHit <= 0L) return 0.0D;
        long delta = now - lastHit;
        return delta >= 0L && delta <= 120L ? 0.55D : 0.0D;
    }

    public static double autoClickScore(PlayerData data, String kind) {
        Deque<Long> intervals = data.getClickIntervals();
        if (intervals == null || intervals.size() < 6) return 0.0D;
        double variance = AimAssistUtil.intervalVariance(intervals);
        if ("autoclicka".equals(kind)) return variance < 8.0D ? 0.6D : 0.0D;
        if ("autoclickb".equals(kind)) return variance < 4.0D ? 0.75D : 0.0D;
        return variance < 2.0D ? 0.85D : 0.0D;
    }

    public static double aimAssistScore(PlayerData data, String kind) {
        double gcdScore = AimAssistUtil.analyzeGcd(data.getYawDeltas());
        if ("aimassista".equals(kind)) return gcdScore > 0.45D ? gcdScore : 0.0D;
        if ("aimassistb".equals(kind)) return gcdScore > 0.6D ? gcdScore : 0.0D;
        return gcdScore > 0.75D ? gcdScore : 0.0D;
    }

    public static double criticalsScore(Player p, PlayerData data, String kind) {
        if (p == null || data == null) return 0.0D;
        boolean air = !p.isOnGround() && !data.wasLastClientGround();
        double micro = data.getLastMicroYOffsetMs() > 0L ? 0.5D : 0.0D;
        if ("criticalsa".equals(kind)) return air ? 0.55D : 0.0D;
        if ("criticalsb".equals(kind)) return air && micro > 0.0D ? 0.7D : 0.0D;
        return air && micro > 0.0D && data.getLastJumpTime() > 0L ? 0.85D : 0.0D;
    }

    public static double inventoryScore(PlayerData data, String kind) {
        int moves = data.getInventoryMoveCount();
        if ("inventorya".equals(kind)) return moves > 4 ? 0.45D : 0.0D;
        if ("inventoryb".equals(kind)) return moves > 8 ? 0.65D : 0.0D;
        return moves > 12 ? 0.85D : 0.0D;
    }

    public static double scaffoldScore(PlayerData data, String kind) {
        int flags = data.getScaffoldPlaceCount();
        if ("scaffolda".equals(kind)) return flags > 6 ? 0.4D : 0.0D;
        if ("scaffoldb".equals(kind)) return flags > 10 ? 0.55D : 0.0D;
        if ("scaffoldc".equals(kind)) return flags > 14 ? 0.65D : 0.0D;
        if ("scaffoldd".equals(kind)) return flags > 18 ? 0.75D : 0.0D;
        return flags > 22 ? 0.85D : 0.0D;
    }
}
