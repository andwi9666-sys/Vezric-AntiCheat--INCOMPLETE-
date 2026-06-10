package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Tracks fall peaks, landings, blink gaps, and pending fall-damage validation.
 */
public final class NoFallTracker {

    private static final int SAMPLE_HISTORY = 24;

    private NoFallTracker() {}

    public static void observe(VezAntiCheat plugin, Player p, PlayerData data, EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null || !er.checked) return;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return;

        double dy = to.getY() - from.getY();
        boolean clientGround = er.clientGround;
        boolean serverGround = isServerGround(to);
        boolean grounded = clientGround || serverGround || er.predictedOnGround;
        float clientFall = p == null ? 0.0F : p.getFallDistance();

        if (!grounded && dy <= 0.05D) {
            if (!data.hasNoFallAPeakY() || to.getY() > data.getNoFallAPeakY()) {
                data.setNoFallAPeakY(to.getY());
                data.setNoFallAHasPeakY(true);
            }
            if (!data.hasNoFallCPeakY() || to.getY() > data.getNoFallCPeakY()) {
                data.setNoFallCPeakY(to.getY());
                data.setNoFallCHasPeakY(true);
            }
            if (clientFall > data.getNoFallCMaxReportedFall()) {
                data.setNoFallCMaxReportedFall(clientFall);
            }
        }

        double serverFall = data.hasNoFallAPeakY()
                ? Math.max(0.0D, data.getNoFallAPeakY() - to.getY()) : 0.0D;
        data.recordNoFallSample(dy, clientGround, serverFall, clientFall, nowMs, SAMPLE_HISTORY);

        trackFallDistanceReset(data, p, serverFall, clientFall, grounded, nowMs);

        boolean wasGrounded = data.isNoFallAWasOnGround();
        data.setNoFallAWasOnGround(grounded);

        if (wasGrounded || !grounded) {
            return;
        }

        double fallDistance = data.hasNoFallAPeakY()
                ? Math.max(0.0D, data.getNoFallAPeakY() - to.getY()) : 0.0D;
        double minFallDistance = plugin.getConfig().getDouble("nofall.min-fall-distance", 2.9D);
        if (fallDistance < minFallDistance) {
            clearFallPeak(data);
            return;
        }

        if (p != null && NoFallUtil.shouldSkipNoFallExpectations(plugin, p)) {
            clearFallPeak(data);
            return;
        }

        if (er.knockbackTick || er.explosionTick || data.isTeleportExempt()) {
            clearFallPeak(data);
            return;
        }

        long damageWaitMs = plugin.getConfig().getLong("nofall.damage-wait-ms", 140L);
        data.setNoFallALandAtMs(nowMs);
        data.setNoFallALandFall(fallDistance);
        data.setNoFallAExpectedDamageAfterMs(nowMs + damageWaitMs);
        data.setNoFallDamageResolved(false);
        data.setLastFallDamageTime(0L);
        data.setLastFallDamageAmount(0.0D);

        long interval = data.getLastFlyingIntervalMs();
        long blinkMinGapMs = plugin.getConfig().getLong("nofall.blink-min-gap-ms", 120L);
        if (interval >= blinkMinGapMs && data.getPreBlinkPeakY() > 0.0D) {
            double blinkDrop = data.getPreBlinkPeakY() - to.getY();
            data.setNoFallBlinkDrop(blinkDrop);
            data.setNoFallBlinkLandMs(nowMs);
        }
    }

    public static void noteBlinkGap(VezAntiCheat plugin, Player p, PlayerData data, long nowMs) {
        if (data == null) return;
        long blinkMinGapMs = plugin != null
                ? plugin.getConfig().getLong("nofall.blink-min-gap-ms", 120L) : 120L;
        long interval = data.getLastFlyingIntervalMs();
        if (interval < blinkMinGapMs) return;

        Location loc = data.getLastLoc();
        double peak = loc == null ? (p == null ? 0.0D : p.getLocation().getY()) : loc.getY();
        if (data.hasNoFallAPeakY()) {
            peak = Math.max(peak, data.getNoFallAPeakY());
        }
        data.setPreBlinkPeakY(peak);
        data.setPreBlinkMs(nowMs);
    }

    public static void onFallDamage(VezAntiCheat plugin, Player p, PlayerData data, double damage, long nowMs) {
        if (data == null) return;
        data.setNoFallDamageResolved(true);
        data.setLastFallDamageTime(nowMs);
        data.setLastFallDamageAmount(damage);
        clearFallPeak(data);
        data.setNoFallBlinkLandMs(0L);
        data.setNoFallBlinkDrop(0.0D);
        data.setPreBlinkPeakY(0.0D);
        data.setPreBlinkMs(0L);
    }

    public static boolean hasPendingDamageCheck(PlayerData data, long nowMs) {
        if (data == null || data.getNoFallALandAtMs() <= 0L) return false;
        if (data.isNoFallDamageResolved()) return false;
        return nowMs >= data.getNoFallAExpectedDamageAfterMs();
    }

    public static boolean isPendingDamageExpired(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (data == null || data.getNoFallALandAtMs() <= 0L) return false;
        long windowMs = plugin.getConfig().getLong("nofall.damage-window-ms", 1000L);
        return nowMs - data.getNoFallALandAtMs() > windowMs;
    }

    public static boolean hasPendingBlinkLand(PlayerData data, long nowMs, VezAntiCheat plugin) {
        if (data == null || data.getNoFallBlinkLandMs() <= 0L) return false;
        if (data.isNoFallDamageResolved()) return false;
        long windowMs = plugin.getConfig().getLong("nofall.damage-window-ms", 1000L);
        return nowMs - data.getNoFallBlinkLandMs() <= windowMs;
    }

    private static void trackFallDistanceReset(PlayerData data, Player p, double serverFall,
                                               float clientFall, boolean grounded, long nowMs) {
        if (data == null || p == null || grounded) return;

        double minTrackedFall = 3.2D;
        double hardReset = 0.45D;
        if (serverFall >= minTrackedFall && clientFall <= hardReset
                && data.getNoFallCMaxReportedFall() >= minTrackedFall) {
            data.setNoFallCResetCount(data.getNoFallCResetCount() + 1);
            data.setNoFallCMaxReportedFall(clientFall);
        }

        long windowMs = 850L;
        if (data.getNoFallCResetCount() > 0 && data.getNoFallSampleTimestamps().size() > 0) {
            Long oldest = data.getNoFallSampleTimestamps().peekFirst();
            if (oldest != null && nowMs - oldest > windowMs) {
                data.setNoFallCResetCount(0);
            }
        }
    }

    private static void clearFallPeak(PlayerData data) {
        if (data == null) return;
        data.setNoFallAPeakY(0.0D);
        data.setNoFallAHasPeakY(false);
        data.setNoFallALandAtMs(0L);
        data.setNoFallALandFall(0.0D);
        data.setNoFallAExpectedDamageAfterMs(0L);
    }

    private static boolean isServerGround(Location loc) {
        if (loc == null) return false;
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                org.bukkit.Material below = loc.clone().add(ox, -0.1, oz).getBlock().getType();
                if (below.isSolid() || below.name().contains("FENCE") || below.name().contains("WALL")
                        || below.name().contains("STEP") || below.name().contains("STAIRS")) {
                    return true;
                }
            }
        }
        return false;
    }
}
