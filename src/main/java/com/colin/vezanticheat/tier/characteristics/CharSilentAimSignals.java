package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.AimAssistUtil;
import com.colin.vezanticheat.utils.CombatUtil;
import org.bukkit.entity.Player;

import java.util.Deque;

/** Shared 8-signal silent-aim scoring utilities for Characteristics tier checks. */
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

    public static double score(VezAntiCheat plugin, Player p, PlayerData data, long now) {
        double s1 = angularScore(p, data);
        double s2 = snapScore(data);
        double s3 = resetScore(data);
        double s4 = movementMismatchScore(data);
        double s5 = centerScore(data);
        double s6 = correlationScore(data);
        double s7 = switchScore(data);
        double s8 = cpsScore(data, now);
        double composite = s1 * 1.5D + s2 * 2.0D + s3 * 1.5D + s4 * 1.0D
                + s5 * 0.8D + s6 * 1.5D + s7 * 1.2D + s8 * 1.0D;
        int active = 0;
        if (s1 > 0.2D) active++;
        if (s2 > 0.2D) active++;
        if (s3 > 0.2D) active++;
        if (s4 > 0.2D) active++;
        if (s5 > 0.2D) active++;
        if (s6 > 0.2D) active++;
        if (s7 > 0.2D) active++;
        if (s8 > 0.2D) active++;
        if (active >= 3) composite *= 1.3D;
        if (active >= 4) composite *= 1.15D;
        return Math.min(1.0D, composite / 10.0D);
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
        return Math.min(1.0D, data.getKillAuraASnapRatio());
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
