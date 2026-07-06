package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Tracks ground-spoof patterns, especially client onGround while Y is still decreasing.
 */
public final class GroundSpoofTracker {

    private GroundSpoofTracker() {}

    public static void observe(VezAntiCheat plugin, Player p, PlayerData data, EngineResult er, long nowMs) {
        if (data == null || er == null || !er.checked) return;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        Location loc = data.getLastLoc();

        if (shouldResetSession(er)) {
            resetSession(data);
            return;
        }

        DescentConfig cfg = descentConfig(plugin, "PredictionGroundSpoofDescent");
        if (!er.clientGround) {
            resetSession(data);
            return;
        }

        if (data.getGroundDescentSessionStartMs() <= 0L) {
            data.setGroundDescentSessionStartMs(nowMs);
            data.setGroundDescentCumulativeDy(0.0D);
            data.setGroundSpoofAirTicks(0);
            if (loc != null) {
                data.setGroundSpoofLastY(loc.getY());
            }
        }

        if (loc != null && data.getGroundSpoofLastY() != 0.0D) {
            double yDelta = loc.getY() - data.getGroundSpoofLastY();
            if (yDelta < 0.0D) {
                data.setGroundDescentCumulativeDy(data.getGroundDescentCumulativeDy() + yDelta);
            }
            data.setGroundSpoofLastY(loc.getY());
        } else if (loc != null) {
            data.setGroundSpoofLastY(loc.getY());
        }

        if (isGroundWhileDescending(data, er, cfg)) {
            data.setGroundSpoofAirTicks(data.getGroundSpoofAirTicks() + 1);
        } else if (dy >= 0.0D) {
            data.setGroundSpoofAirTicks(Math.max(0, data.getGroundSpoofAirTicks() - 1));
        }
    }

    public static void resetSession(PlayerData data) {
        if (data == null) return;
        data.setGroundDescentSessionStartMs(0L);
        data.setGroundDescentCumulativeDy(0.0D);
        data.setGroundSpoofAirTicks(0);
        data.setGroundSpoofLastY(0.0D);
    }

    public static boolean isGroundWhileDescending(PlayerData data, EngineResult er, DescentConfig cfg) {
        if (data == null || er == null || cfg == null || !er.clientGround) return false;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        return actual.getY() < cfg.minDescentDy;
    }

    public static boolean isSustainedGroundDescent(PlayerData data, DescentConfig cfg) {
        if (data == null || cfg == null) return false;
        if (data.getGroundSpoofAirTicks() >= cfg.descentTicksToFlag) return true;
        return data.getGroundSpoofAirTicks() >= 3
                && Math.abs(data.getGroundDescentCumulativeDy()) >= cfg.cumulativeDescentMin;
    }

    public static boolean isAirBelowGroundClaim(NoFallUtil.Context ctx) {
        return ctx != null && ctx.clientGround && ctx.blockBelowAir && !ctx.softLanding;
    }

    public static boolean isServerGroundDisagreement(PlayerData data, EngineResult er) {
        if (data == null || er == null || !er.clientGround) return false;
        NoFallUtil.Context ctx = null;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        if (actual.getY() >= 0.0D) return false;
        Location to = data.getLastLoc();
        if (to == null) return false;
        for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
            for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                org.bukkit.Material below = to.clone().add(ox, -0.1, oz).getBlock().getType();
                if (below.isSolid()) return false;
            }
        }
        return true;
    }

    public static boolean shouldSkipGroundSpoofCheck(Player p, PlayerData data, EngineResult er,
                                                     long nowMs, VezAntiCheat plugin) {
        if (p == null || data == null || er == null) return true;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return true;
        if (p.getAllowFlight() || p.isFlying() || p.isInsideVehicle()) return true;
        if (er.inWater || er.onClimbable || er.inWeb) return true;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return true;
        if (er.knockbackTick || er.explosionTick) return true;

        NoFallUtil.Context ctx = NoFallUtil.analyze(plugin, p, data);
        if (ctx != null && ctx.softLanding) return true;
        if (plugin != null && FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) return true;

        long jumpWindow = plugin != null
                ? plugin.getConfig().getLong("movement-analysis.jump-phase-window-ms", 420L) : 420L;
        if (data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= jumpWindow) {
            return true;
        }
        return false;
    }

    public static DescentConfig descentConfig(VezAntiCheat plugin, String checkName) {
        double minDescentDy = globalDouble(plugin, "min-descent-dy", -0.04D);
        int descentTicksToFlag = (int) globalLong(plugin, "descent-ticks-to-flag", 3L);
        double cumulativeDescentMin = globalDouble(plugin, "cumulative-descent-min", 0.12D);

        if (plugin != null && checkName != null) {
            minDescentDy = plugin.tierCfg().checkDouble(checkName, "minDescentDy", minDescentDy);
            descentTicksToFlag = plugin.tierCfg().checkInt(checkName, "descentTicksToFlag", descentTicksToFlag);
            cumulativeDescentMin = plugin.tierCfg().checkDouble(checkName, "cumulativeDescentMin", cumulativeDescentMin);
        }
        return new DescentConfig(minDescentDy, descentTicksToFlag, cumulativeDescentMin);
    }

    private static boolean shouldResetSession(EngineResult er) {
        if (er.knockbackTick || er.explosionTick) return true;
        if (er.inWater || er.onClimbable || er.inWeb) return true;
        return false;
    }

    private static double globalDouble(VezAntiCheat plugin, String key, double def) {
        if (plugin == null) return def;
        return plugin.getConfig().getDouble("nofall.ground-spoof." + key, def);
    }

    private static long globalLong(VezAntiCheat plugin, String key, long def) {
        if (plugin == null) return def;
        return plugin.getConfig().getLong("nofall.ground-spoof." + key, def);
    }

    public static final class DescentConfig {
        public final double minDescentDy;
        public final int descentTicksToFlag;
        public final double cumulativeDescentMin;

        public DescentConfig(double minDescentDy, int descentTicksToFlag, double cumulativeDescentMin) {
            this.minDescentDy = minDescentDy;
            this.descentTicksToFlag = descentTicksToFlag;
            this.cumulativeDescentMin = cumulativeDescentMin;
        }
    }
}
