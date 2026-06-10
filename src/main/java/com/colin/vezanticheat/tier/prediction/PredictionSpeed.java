package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import com.colin.vezanticheat.utils.PotionUtil;
import com.colin.vezanticheat.utils.SpeedPatternUtil;
import com.colin.vezanticheat.utils.SpeedUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Horizontal movement envelope vs simulation (speed / bhop / strafe / YPort / Grim friction cheats).
 */
public final class PredictionSpeed extends AbstractMovementTierCheck {

    public PredictionSpeed(VezAntiCheat plugin) {
        super(plugin, "PredictionSpeed", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (data.isEatMovementGrace() || data.isActivelyEating()) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.75D));
            cool(p, 0.45D);
            return;
        }

        if (engineActive()) {
            if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
                cool(p, 0.2D);
                return;
            }

            EngineResult er = engineResult(data);
            if (er == null) {
                cool(p, 0.45D);
                return;
            }

            if (EngineMovementGrace.isLikelyWallStop(er)) {
                data.setEngineSpeedOvershootTicks(0);
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.5D);
                cool(p, 0.45D);
                return;
            }

            double jumpGrace = cfgDouble("jumpGraceMaxOffset", 0.85D) + PotionUtil.jumpArcOffsetAllowance(p);
            if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.70D);
                cool(p, 0.45D);
                return;
            }
            if (EngineMovementGrace.isLikelyLegitJumpArc(plugin, p, data, er, nowMs)
                    && !SpeedPatternUtil.isYPortSlam(data, er)
                    && er.horizontalOffset <= jumpGrace) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.75D);
                cool(p, 0.45D);
                return;
            }

            if (PotionUtil.hasSpeedBoost(p) && p.isSprinting()
                    && EngineMovementGrace.isLikelyLegitGroundLocomotion(er)) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.65D);
                cool(p, 0.45D);
                return;
            }

            double thr = cfgDouble("engineHorizontalOffset", 0.032D) + PotionUtil.combinedSpeedOffsetAllowance(p);
            if (SpeedPatternUtil.isSuspiciousAirStrafe(plugin, p, data, er, nowMs)) {
                int gain = er.horizontalOffset > thr * 2.0D ? 2 : 1;
                flagBuffered(p, data, gain,
                        "airStrafe hOff=" + r(er.horizontalOffset) + " ratio=" + r(SpeedPatternUtil.horizontalRatio(er))
                                + " " + er.debug);
                return;
            }

            if (PotionUtil.hasSpeedBoost(p) && er.horizontalOffset <= thr) {
                cool(p, 0.45D);
                return;
            }

            double ratio = SpeedPatternUtil.horizontalRatio(er);
            double ratioMin = cfgDouble("engineSpeedRatioMin", 1.048D) + (PotionUtil.speedLevel(p) * 0.012D);
            int ratioTicks = cfgInt("engineSpeedRatioTicks", 4);
            double advantageThr = cfgDouble("engineAdvantageThreshold", 0.14D);

            if (!SpeedPatternUtil.shouldForceSpeedDetection(plugin, p, data, er, nowMs)
                    && EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, er, nowMs, name())) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                cool(p, 0.45D);
                return;
            }

            Vector actual = er.actual == null ? new Vector() : er.actual;
            double actH = Math.hypot(actual.getX(), actual.getZ());
            if (ratio >= ratioMin && actH > 0.07D
                    && !SpeedPatternUtil.isInLegitSpeedArc(plugin, p, data, er, nowMs)) {
                data.setEngineSpeedOvershootTicks(data.getEngineSpeedOvershootTicks() + 1);
            } else {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
            }

            boolean yport = SpeedPatternUtil.isYPortSlam(data, er) || data.getEngineYPortStreak() >= 2;
            boolean offsetFlag = er.horizontalOffset > thr;
            boolean ratioFlag = data.getEngineSpeedOvershootTicks() >= ratioTicks && ratio >= ratioMin;
            boolean microFlag = data.getEngineMicroRatioStreak() >= cfgInt("engineMicroRatioTicks", 10);
            boolean gainFlag = data.getEngineHorizontalGainStreak() >= cfgInt("engineGainStreakTicks", 8);
            boolean advantageFlag = data.getEngineOffsetAdvantage() >= advantageThr;
            boolean groundBurst = er.clientGround && er.predictedOnGround && actH > 0.14D
                    && ratio >= cfgDouble("engineGroundRatioMin", 1.08D)
                    && !PotionUtil.hasSpeedBoost(p)
                    && !MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data);

            if (!yport && !offsetFlag && !ratioFlag && !microFlag && !gainFlag && !advantageFlag && !groundBurst) {
                cool(p, 0.4D);
                return;
            }

            int gain = 1;
            if (yport || er.horizontalOffset > thr * 2.5D || ratio >= ratioMin + 0.05D
                    || data.getEngineOffsetAdvantage() >= cfgDouble("engineBlatantAdvantage", 0.22D)) {
                gain = 2;
            }
            if (data.getEngineOffsetAdvantage() >= cfgDouble("engineBlatantAdvantage", 0.22D)) {
                requestBlatant(p, data, "speed-adv=" + r(data.getEngineOffsetAdvantage()));
                fail(p, data, cfgDouble("failVl", 1.0D),
                        "adv=" + r(data.getEngineOffsetAdvantage()) + " " + er.debug);
                data.setEngineOffsetAdvantage(0.0D);
                resetBuffer(p);
                return;
            }

            flagBuffered(p, data, gain,
                    "hOff=" + r(er.horizontalOffset) + " ratio=" + r(ratio)
                            + " yport=" + yport + " micro=" + data.getEngineMicroRatioStreak()
                            + " adv=" + r(data.getEngineOffsetAdvantage()) + " " + er.debug);
            return;
        }

        if (data.isVelocityExempt() || data.isTeleportExempt()
                || EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)
                || SpeedUtil.isRecentExplosion(plugin, data, nowMs)) {
            cool(p, 0.45D);
            return;
        }

        PredictionResult result = predictionResult(data);
        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)
                && result != null
                && result.horizontalOffset <= cfgDouble("jumpGraceMaxOffset", 1.10D)) {
            cool(p, 0.55D);
            return;
        }
        if (data.isPotionExempt() || PotionUtil.hasSpeed(p)) {
            cool(p, 0.45D);
            return;
        }
        if (result == null || result.usingItem || result.inLiquid || result.weirdSurface || !result.cleanMovement) {
            cool(p, 0.45D);
            return;
        }
        if (!result.simulated || !result.horizontalViolation) {
            cool(p, 0.45D);
            return;
        }

        int gain = result.horizontalOffset > 0.12D ? 2 : 1;
        flagBuffered(p, data, gain,
                "offsetH=" + r(result.horizontalOffset) + " " + result.debugSummary);
    }
}
