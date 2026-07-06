package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shared legal movement envelope used by prediction checks before they buffer.
 */
public final class MovementEnvelopeUtil {

    private MovementEnvelopeUtil() {}

    public static boolean isWithinLegalMovementEnvelope(VezAntiCheat plugin, Player player, PlayerData data,
                                                        EngineResult er, long nowMs, String checkName) {
        if (data == null || er == null) return true;
        if (er.knockbackTick || er.explosionTick) return true;
        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()) return true;
        if (data.isBlockStateExempt()) return true;
        if (data.isEatMovementGrace() || ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) return true;
        if (FallArcTracker.shouldSuppressLegitFallMovement(plugin, data, nowMs)) return true;
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) return true;
        if (player != null && EngineMovementGrace.isLikelyLegitBridgeMovement(plugin, player, data, er, nowMs)) {
            return true;
        }
        if (isLikelyVanillaJumpChain(plugin, player, data, er, nowMs, checkName)) return true;
        return isLegalSpeedPotionGroundMovement(plugin, player, er, checkName);
    }

    public static boolean isLikelyVanillaJumpChain(VezAntiCheat plugin, Player player, PlayerData data,
                                                   EngineResult er, long nowMs, String checkName) {
        return isLikelyVanillaJumpChain(data, er, nowMs, JumpEnvelopeSettings.from(plugin, player, checkName));
    }

    static boolean isLikelyVanillaJumpChain(PlayerData data, EngineResult er, long nowMs,
                                            JumpEnvelopeSettings settings) {
        if (data == null || er == null || settings == null) return false;
        if (er.knockbackTick || er.explosionTick || er.inWater || er.onClimbable || er.inWeb) return false;
        if (data.getLastJumpTime() <= 0L) return false;

        long sinceJump = nowMs - data.getLastJumpTime();
        if (sinceJump < 0L || sinceJump > settings.arcWindowMs) return false;
        if (SpeedPatternUtil.isYPortSlam(data, er)) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (dy < settings.minDy || dy > settings.maxDy) return false;
        if (distH > settings.maxHorizontal) return false;
        if (er.horizontalOffset > settings.maxHorizontalOffset) return false;
        if (er.offset > settings.maxOffset) return false;

        int airTicks = data.getEngineAirborneTicks();
        if (isIllegalLowHopLike(data, er, sinceJump, airTicks)) return false;

        if (Math.abs(dy) < 0.018D && !er.clientGround && !er.predictedOnGround && airTicks >= 5) {
            return false;
        }
        if (airTicks > 24 && sinceJump > 650L) return false;

        boolean rising = dy > 0.055D;
        boolean falling = dy < -0.025D;
        boolean landing = er.clientGround && dy >= settings.minDy && dy <= 0.18D;
        boolean airborne = !er.predictedOnGround || !er.clientGround;
        boolean moving = distH >= 0.035D || Math.abs(dy) >= 0.025D;
        return moving && (rising || falling || landing || airborne);
    }

    public static boolean isLegalSpeedPotionGroundMovement(VezAntiCheat plugin, Player player,
                                                           EngineResult er, String checkName) {
        return isLegalSpeedPotionGroundMovement(er, SpeedGroundSettings.from(plugin, player, checkName));
    }

    static boolean isLegalSpeedPotionGroundMovement(EngineResult er, SpeedGroundSettings settings) {
        if (er == null || settings == null || !settings.hasSpeedBoost) return false;
        if (!er.clientGround || !er.predictedOnGround) return false;
        if (er.knockbackTick || er.explosionTick || er.inWater || er.onClimbable || er.inWeb) return false;
        if (SpeedPatternUtil.isYPortSlam(null, er)) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double actH = Math.hypot(actual.getX(), actual.getZ());
        if (Math.abs(dy) > 0.12D || actH < 0.03D || actH > settings.maxActualHorizontal) return false;

        double ratio = SpeedPatternUtil.horizontalRatio(er);
        return er.horizontalOffset <= settings.maxHorizontalOffset && ratio <= settings.maxRatio;
    }

    static boolean isIllegalLowHopLike(PlayerData data, EngineResult er, long sinceJump, int airTicks) {
        if (data == null || er == null) return false;
        if (er.clientGround || er.predictedOnGround) return false;
        if (sinceJump > 140L || airTicks > 4) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double actH = Math.hypot(actual.getX(), actual.getZ());
        return dy > 0.02D && dy < 0.18D && actH > 0.16D && er.horizontalOffset > 0.035D;
    }

    static final class JumpEnvelopeSettings {
        final long arcWindowMs;
        final double minDy;
        final double maxDy;
        final double maxHorizontal;
        final double maxOffset;
        final double maxHorizontalOffset;

        JumpEnvelopeSettings(long arcWindowMs, double minDy, double maxDy, double maxHorizontal,
                             double maxOffset, double maxHorizontalOffset) {
            this.arcWindowMs = arcWindowMs;
            this.minDy = minDy;
            this.maxDy = maxDy;
            this.maxHorizontal = maxHorizontal;
            this.maxOffset = maxOffset;
            this.maxHorizontalOffset = maxHorizontalOffset;
        }

        static JumpEnvelopeSettings defaults() {
            return new JumpEnvelopeSettings(950L, -0.82D, 0.55D, 1.08D, 0.52D, 0.85D);
        }

        static JumpEnvelopeSettings from(VezAntiCheat plugin, Player player, String checkName) {
            JumpEnvelopeSettings def = defaults();
            long arcWindow = plugin == null ? def.arcWindowMs
                    : plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", def.arcWindowMs);
            double minDy = plugin == null ? def.minDy
                    : plugin.getConfig().getDouble("movement-analysis.jump-arc-min-dy", def.minDy);
            double maxDy = plugin == null ? def.maxDy
                    : plugin.getConfig().getDouble("movement-analysis.jump-arc-max-dy", def.maxDy);
            double maxHorizontal = plugin == null ? def.maxHorizontal
                    : plugin.getConfig().getDouble("movement-analysis.jump-arc-max-horizontal", def.maxHorizontal);
            double maxOffset = plugin == null ? def.maxOffset
                    : plugin.getConfig().getDouble("movement-analysis.jump-arc-max-offset", def.maxOffset);
            double maxHorizontalOffset = plugin == null ? def.maxHorizontalOffset
                    : CheckConfigUtil.checkDouble(plugin, checkName == null ? "PredictionSpeed" : checkName,
                    "jumpGraceMaxOffset", def.maxHorizontalOffset);

            maxOffset += PotionUtil.jumpArcOffsetAllowance(player);
            if (player != null && PotionUtil.hasSpeed(player)) {
                maxHorizontal += 0.12D * PotionUtil.speedLevel(player);
                maxHorizontalOffset += 0.05D * PotionUtil.speedLevel(player);
            }
            if (player != null && PotionUtil.hasJumpBoost(player)) {
                maxDy += 0.10D * PotionUtil.jumpBoostLevel(player) + 0.05D;
            }
            if (player != null && player.isSprinting()) {
                maxHorizontal += 0.14D;
                maxOffset += 0.18D;
                maxHorizontalOffset += 0.08D;
            }
            return new JumpEnvelopeSettings(arcWindow, minDy, maxDy, maxHorizontal, maxOffset, maxHorizontalOffset);
        }
    }

    static final class SpeedGroundSettings {
        final boolean hasSpeedBoost;
        final double maxHorizontalOffset;
        final double maxRatio;
        final double maxActualHorizontal;

        SpeedGroundSettings(boolean hasSpeedBoost, double maxHorizontalOffset, double maxRatio,
                            double maxActualHorizontal) {
            this.hasSpeedBoost = hasSpeedBoost;
            this.maxHorizontalOffset = maxHorizontalOffset;
            this.maxRatio = maxRatio;
            this.maxActualHorizontal = maxActualHorizontal;
        }

        static SpeedGroundSettings defaults(boolean hasSpeedBoost) {
            return new SpeedGroundSettings(hasSpeedBoost, 0.132D, 1.14D, 0.52D);
        }

        static SpeedGroundSettings from(VezAntiCheat plugin, Player player, String checkName) {
            boolean hasSpeedBoost = player != null && PotionUtil.hasSpeedBoost(player);
            String cfg = checkName == null ? "PredictionSpeed" : checkName;
            double base = plugin == null ? 0.032D : CheckConfigUtil.checkDouble(plugin, cfg, "engineHorizontalOffset", 0.032D);
            double allowance = PotionUtil.combinedSpeedOffsetAllowance(player);
            double level = Math.max(0, PotionUtil.speedLevel(player));
            double maxHorizontalOffset = base + allowance + 0.045D;
            double maxRatio = 1.10D + Math.min(0.05D, level * 0.018D);
            double maxActualHorizontal = 0.52D + Math.min(0.18D, level * 0.05D);
            return new SpeedGroundSettings(hasSpeedBoost, maxHorizontalOffset, maxRatio, maxActualHorizontal);
        }
    }
}
