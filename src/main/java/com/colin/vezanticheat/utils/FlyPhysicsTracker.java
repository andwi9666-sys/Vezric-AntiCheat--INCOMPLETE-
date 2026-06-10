package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Tracks per-airborne-session vertical physics (bobbing, fall arcs, creep ascent) and
 * feeds {@link com.colin.vezanticheat.data.PlayerData#getRecentYMotions()} for gravity analysis.
 */
public final class FlyPhysicsTracker {

    private static final int MAX_Y_MOTION_SAMPLES = 24;

    private FlyPhysicsTracker() {}

    public static void observe(VezAntiCheat plugin, PlayerData data, EngineResult er, long nowMs) {
        if (data == null || er == null || !er.checked) return;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();

        Deque<Double> motions = data.getRecentYMotions();
        motions.addLast(dy);
        while (motions.size() > MAX_Y_MOTION_SAMPLES) {
            motions.removeFirst();
        }

        if (shouldResetSession(er)) {
            resetSession(data);
            data.setFlyBobEvidenceActive(false);
            return;
        }

        if (data.getAirborneSessionStartMs() <= 0L) {
            data.setAirborneSessionStartMs(nowMs);
            data.setSessionMinY(Double.MAX_VALUE);
            data.setSessionMaxY(-Double.MAX_VALUE);
            data.setSessionCumulativeDy(0.0D);
            data.setYReversalCount(0);
            data.setMonotonicFallTicks(0);
            data.setCreepUpTicks(0);
            data.setFlySessionLastDy(0.0D);
        }

        Location loc = data.getLastLoc();
        if (loc != null) {
            double y = loc.getY();
            if (y < data.getSessionMinY()) data.setSessionMinY(y);
            if (y > data.getSessionMaxY()) data.setSessionMaxY(y);
        }

        double lastDy = data.getFlySessionLastDy();
        if (data.getEngineAirborneTicks() > 1
                && ((lastDy > 0.01D && dy < -0.01D) || (lastDy < -0.01D && dy > 0.01D))) {
            data.setYReversalCount(data.getYReversalCount() + 1);
        }
        data.setFlySessionLastDy(dy);
        data.setSessionCumulativeDy(data.getSessionCumulativeDy() + dy);

        if (dy < -0.04D) {
            data.setMonotonicFallTicks(data.getMonotonicFallTicks() + 1);
        }
        if (dy >= 0.02D && dy <= 0.20D) {
            data.setCreepUpTicks(data.getCreepUpTicks() + 1);
        }

        BobConfig cfg = bobConfig(plugin, "PredictionFlyBob");
        data.setFlyBobEvidenceActive(isBobbingFlySession(data, er, nowMs, cfg));
    }

    public static void resetSession(PlayerData data) {
        if (data == null) return;
        data.setAirborneSessionStartMs(0L);
        data.setSessionMinY(0.0D);
        data.setSessionMaxY(0.0D);
        data.setSessionCumulativeDy(0.0D);
        data.setYReversalCount(0);
        data.setMonotonicFallTicks(0);
        data.setCreepUpTicks(0);
        data.setFlySessionLastDy(0.0D);
        data.setFlyBobEvidenceActive(false);
    }

    public static boolean hasActiveBobbingEvidence(PlayerData data) {
        return data != null && data.isFlyBobEvidenceActive();
    }

    public static boolean isMonotonicFallSession(PlayerData data, EngineResult er, long nowMs) {
        if (data == null) return false;
        if (data.isFallArcActive()) return true;

        long sessionStart = data.getAirborneSessionStartMs();
        if (sessionStart <= 0L) return false;

        long sessionDuration = nowMs - sessionStart;
        int airborneTicks = data.getEngineAirborneTicks();
        if (sessionDuration >= 1300L && data.getSessionCumulativeDy() <= -0.35D) {
            return true;
        }
        return airborneTicks > 0
                && data.getMonotonicFallTicks() >= (int) Math.ceil(airborneTicks * 0.75D);
    }

    public static boolean isBobbingFlySession(PlayerData data, EngineResult er, long nowMs, BobConfig cfg) {
        if (data == null || er == null || cfg == null) return false;
        if (er.knockbackTick || er.explosionTick) return false;
        if (isMonotonicFallSession(data, er, nowMs)) return false;

        long sessionStart = data.getAirborneSessionStartMs();
        if (sessionStart <= 0L) return false;

        long airborneDuration = nowMs - sessionStart;
        if (airborneDuration < cfg.bobMinAirMs) return false;
        if (data.getYReversalCount() < cfg.bobMinReversals) return false;

        double amplitude = data.getSessionMaxY() - data.getSessionMinY();
        if (amplitude < cfg.bobMinAmplitude) return false;
        if (er.verticalOffset <= cfg.bobMinVerticalOffset) return false;

        return true;
    }

    public static boolean isBobbingFlySession(VezAntiCheat plugin, PlayerData data, EngineResult er, long nowMs) {
        return isBobbingFlySession(data, er, nowMs, bobConfig(plugin, "PredictionFlyBob"));
    }

    /** Shared gate for creative/fly/liquids/climbables used by new fly checks. */
    public static boolean shouldSkipMovementFlyCheck(Player p, PlayerData data, EngineResult er, long nowMs,
                                                     VezAntiCheat plugin) {
        if (p == null || data == null || er == null) return true;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return true;
        if (p.getAllowFlight() || p.isFlying()) return true;
        if (er.inWater || er.onClimbable || er.inWeb) return true;

        long jumpWindow = plugin != null
                ? plugin.getConfig().getLong("movement-analysis.jump-phase-window-ms", 420L) : 420L;
        if (data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= jumpWindow) {
            return true;
        }
        return false;
    }

    public static boolean isBurstExempt(VezAntiCheat plugin, PlayerData data, EngineResult er, long nowMs,
                                        long velocityWindowMs) {
        if (data == null || er == null) return true;
        if (data.isVelocityExempt()) return true;
        if (data.getLastVelocityTime() > 0L && nowMs - data.getLastVelocityTime() <= velocityWindowMs) {
            return true;
        }
        if (er.explosionTick || (plugin != null && SpeedUtil.isRecentExplosion(plugin, data, nowMs))) {
            return true;
        }
        if (er.knockbackTick) return true;
        if (er.onClimbable || er.inWater || er.inWeb) return true;
        if (er.entityPushOffset() > 0.0D) return true;

        long jumpWindow = plugin != null
                ? plugin.getConfig().getLong("movement-analysis.jump-phase-window-ms", 420L) : 420L;
        return data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= jumpWindow;
    }

    public static int gravityViolationStreak(PlayerData data, int sampleSize, double errorThreshold) {
        if (data == null || sampleSize < 2) return 0;

        Deque<Double> motions = data.getRecentYMotions();
        if (motions.size() < sampleSize) return 0;

        ArrayDeque<Double> window = new ArrayDeque<Double>(motions);
        while (window.size() > sampleSize) {
            window.removeFirst();
        }

        int consecutive = 0;
        int maxConsecutive = 0;
        Double[] samples = window.toArray(new Double[0]);
        double prevDy = samples[0];
        for (int i = 1; i < samples.length; i++) {
            double expected = FlyUtil.expectedNextDy(prevDy);
            double error = Math.abs(samples[i] - expected);
            if (error > errorThreshold) {
                consecutive++;
                maxConsecutive = Math.max(maxConsecutive, consecutive);
            } else {
                consecutive = 0;
            }
            prevDy = samples[i];
        }
        return maxConsecutive;
    }

    public static BobConfig bobConfig(VezAntiCheat plugin, String checkName) {
        long bobMinAirMs = globalLong(plugin, "bob-min-air-ms", 1300L);
        int bobMinReversals = (int) globalLong(plugin, "bob-min-reversals", 3L);
        double bobMinAmplitude = globalDouble(plugin, "bob-min-amplitude", 0.06D);
        double bobMinVerticalOffset = globalDouble(plugin, "bob-min-vertical-offset", 0.04D);

        if (plugin != null && checkName != null) {
            bobMinAirMs = plugin.tierCfg().checkLong(checkName, "bobMinAirMs", bobMinAirMs);
            bobMinReversals = plugin.tierCfg().checkInt(checkName, "bobMinReversals", bobMinReversals);
            bobMinAmplitude = plugin.tierCfg().checkDouble(checkName, "bobMinAmplitude", bobMinAmplitude);
            bobMinVerticalOffset = plugin.tierCfg().checkDouble(checkName, "bobMinVerticalOffset", bobMinVerticalOffset);
        }
        return new BobConfig(bobMinAirMs, bobMinReversals, bobMinAmplitude, bobMinVerticalOffset);
    }

    private static boolean shouldResetSession(EngineResult er) {
        if (er.clientGround || er.predictedOnGround) return true;
        if (er.inWater || er.onClimbable || er.inWeb) return true;
        if (er.knockbackTick || er.explosionTick) return true;
        return false;
    }

    private static double globalDouble(VezAntiCheat plugin, String key, double def) {
        if (plugin == null) return def;
        return plugin.getConfig().getDouble("movement-analysis.fly-physics." + key, def);
    }

    private static long globalLong(VezAntiCheat plugin, String key, long def) {
        if (plugin == null) return def;
        return plugin.getConfig().getLong("movement-analysis.fly-physics." + key, def);
    }

    public static final class BobConfig {
        public final long bobMinAirMs;
        public final int bobMinReversals;
        public final double bobMinAmplitude;
        public final double bobMinVerticalOffset;

        public BobConfig(long bobMinAirMs, int bobMinReversals, double bobMinAmplitude, double bobMinVerticalOffset) {
            this.bobMinAirMs = bobMinAirMs;
            this.bobMinReversals = bobMinReversals;
            this.bobMinAmplitude = bobMinAmplitude;
            this.bobMinVerticalOffset = bobMinVerticalOffset;
        }
    }
}
