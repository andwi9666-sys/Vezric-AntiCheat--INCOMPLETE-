package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Detects cheat-client speed patterns (YPort slam, Grim friction drift, sustained ratio overshoot).
 */
public final class SpeedPatternUtil {

    private SpeedPatternUtil() {}

    public static void observe(VezAntiCheat plugin, Player player, PlayerData data, EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null || !er.checked) return;

        if (ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
            data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            return;
        }

        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
            data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            return;
        }

        Vector actual = er.actual == null ? new Vector() : er.actual;
        Vector predicted = er.predicted == null ? new Vector() : er.predicted;
        double dy = actual.getY();
        double predH = Math.hypot(predicted.getX(), predicted.getZ());
        double actH = Math.hypot(actual.getX(), actual.getZ());
        double ratio = predH > 0.05D ? actH / predH : 1.0D;

        if (isYPortSlam(data, dy)) {
            data.setEngineYPortStreak(data.getEngineYPortStreak() + 1);
        } else {
            data.setEngineYPortStreak(Math.max(0, data.getEngineYPortStreak() - 1));
        }

        data.setLastMoveDy(dy);

        if (isInLegitSpeedArc(plugin, player, data, er, nowMs)) {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
            data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            return;
        }

        if (player != null && PotionUtil.hasSpeedBoost(player) && player.isSprinting()
                && er.clientGround && er.predictedOnGround && !er.knockbackTick && !er.explosionTick) {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
            data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            return;
        }

        if (player != null && MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, player, data)) {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
            data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            return;
        }

        double microRatioMin = plugin.getConfig().getDouble("movement-analysis.speed-micro-ratio-min", 1.008D);
        if (ratio >= microRatioMin && actH > 0.06D && !er.usingItem) {
            data.setEngineMicroRatioStreak(data.getEngineMicroRatioStreak() + 1);
        } else {
            data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
        }

        double gainMin = plugin.getConfig().getDouble("movement-analysis.speed-gain-min", 0.022D);
        double gain = Math.max(0.0D, actH - predH);
        if (gain > gainMin && actH > 0.07D) {
            data.setEngineHorizontalGainStreak(data.getEngineHorizontalGainStreak() + 1);
        } else {
            data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
        }
    }

    public static boolean isInLegitSpeedArc(VezAntiCheat plugin, Player player, PlayerData data,
                                            EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null) return false;
        if (isYPortSlam(data, er)) return false;

        long arcWindow = plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L);
        if (data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= arcWindow) {
            double maxOffset = plugin.getConfig().getDouble("movement-analysis.jump-arc-max-offset", 0.52D);
            maxOffset += PotionUtil.jumpArcOffsetAllowance(player);
            if (player != null && player.isSprinting()) {
                maxOffset += 0.18D;
            }
            if (er.offset <= maxOffset + 0.22D) return true;
        }
        return false;
    }

    /** YPort: jump tick then forced slam (~-0.76) with boosted horizontal carry. */
    public static boolean isYPortSlam(PlayerData data, EngineResult er) {
        if (data == null || er == null) return false;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        return isYPortSlam(data, actual.getY());
    }

    public static boolean isYPortSlam(PlayerData data, double dy) {
        if (data == null) return false;
        double prevDy = data.getLastMoveDy();
        return prevDy > 0.30D && dy <= -0.55D && dy >= -0.85D;
    }

    public static double horizontalRatio(EngineResult er) {
        if (er == null) return 1.0D;
        Vector predicted = er.predicted == null ? new Vector() : er.predicted;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        double predH = Math.hypot(predicted.getX(), predicted.getZ());
        double actH = Math.hypot(actual.getX(), actual.getZ());
        return predH > 0.05D ? actH / predH : 1.0D;
    }

    /**
     * Air strafe: faster horizontal movement than prediction while airborne (not KB/fall arc).
     */
    public static boolean isSuspiciousAirStrafe(VezAntiCheat plugin, Player player, PlayerData data,
                                                EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null || !er.checked) return false;
        if (er.clientGround || er.predictedOnGround) return false;
        if (er.inWater || er.onClimbable || er.inWeb) return false;
        if (er.knockbackTick || er.explosionTick) return false;
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) return false;
        if (data.isFallArcActive() || FallArcTracker.isInFallArcWindow(plugin, data, nowMs)) return false;

        long arcWindow = plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L);
        if (data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= arcWindow) return false;
        if (player != null && MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, player, data)) return false;
        if (player != null && EngineMovementGrace.isLikelyLegitJumpArc(plugin, player, data, er, nowMs)) {
            return false;
        }

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double actH = Math.hypot(actual.getX(), actual.getZ());
        if (actH < 0.06D) return false;

        double ratio = horizontalRatio(er);
        double ratioMin = CheckConfigUtil.checkDouble(plugin, "PredictionSpeed", "engineAirStrafeRatioMin", 1.032D);
        double offsetMin = CheckConfigUtil.checkDouble(plugin, "PredictionSpeed", "engineAirStrafeOffset", 0.026D);
        return ratio >= ratioMin || er.horizontalOffset >= offsetMin;
    }

    /** When true, horizontal speed grace paths must not suppress detection. */
    public static boolean shouldForceSpeedDetection(VezAntiCheat plugin, Player player, PlayerData data,
                                                    EngineResult er, long nowMs) {
        if (plugin == null || data == null || er == null) return false;
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) return false;
        if (EngineMovementGrace.isLikelyWallStop(er)) return false;
        if (player != null && PotionUtil.hasSpeedBoost(player)
                && er.horizontalOffset <= CheckConfigUtil.checkDouble(plugin, "PredictionSpeed",
                        "engineHorizontalOffset", 0.032D) + PotionUtil.combinedSpeedOffsetAllowance(player)) {
            return false;
        }

        if (player != null && PotionUtil.hasSpeedBoost(player)
                && player.isSprinting() && er.clientGround && er.predictedOnGround) {
            return false;
        }

        if (isInLegitSpeedArc(plugin, player, data, er, nowMs)) return false;
        if (player != null && MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, player, data)) {
            return false;
        }

        double ratio = horizontalRatio(er);
        double ratioMin = CheckConfigUtil.checkDouble(plugin, "PredictionSpeed", "engineSpeedRatioMin", 1.055D);
        double microMin = plugin.getConfig().getDouble("movement-analysis.speed-micro-ratio-min", 1.008D);
        int microTicks = plugin.tierCfg().checkInt("PredictionSpeed", "engineMicroRatioTicks", 10);
        int gainTicks = plugin.tierCfg().checkInt("PredictionSpeed", "engineGainStreakTicks", 8);
        double advantageThr = CheckConfigUtil.checkDouble(plugin, "PredictionSpeed", "engineAdvantageThreshold", 0.14D);

        if (isSuspiciousAirStrafe(plugin, player, data, er, nowMs)) return true;
        if (isYPortSlam(data, er)) return true;
        if (data.getEngineYPortStreak() >= 2) return true;
        if (data.getEngineSpeedOvershootTicks() >= cfgInt(plugin, "engineSpeedRatioTicks", 4) && ratio >= ratioMin) {
            return true;
        }
        if (data.getEngineMicroRatioStreak() >= microTicks && ratio >= microMin + 0.004D) return true;
        if (data.getEngineHorizontalGainStreak() >= gainTicks) return true;
        if (data.getEngineOffsetAdvantage() >= advantageThr) return true;
        if (UseItemTracker.isFakeReleaseUse(player, data, nowMs) && er.horizontalOffset > 0.018D) {
            return true;
        }

        return false;
    }

    private static int cfgInt(VezAntiCheat plugin, String key, int def) {
        return plugin.tierCfg().checkInt("PredictionSpeed", key, def);
    }
}
