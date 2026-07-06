package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.MovementEnvelopeUtil;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import com.colin.vezanticheat.utils.PotionUtil;
import com.colin.vezanticheat.utils.SpeedPatternUtil;
import com.colin.vezanticheat.utils.SpeedUtil;
import org.bukkit.entity.Player;

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
        // Legit server-granted flight (/fly, creative): this horizontal envelope is built from
        // ground/sprint physics, so at max fly speed the offset overshoots and false-flags. isFlying() is
        // authoritative server-side state (the server only accepts the flying ability when it granted
        // allow-flight, so a cheat cannot fake it without already having free flight). We gate on isFlying()
        // rather than getAllowFlight() so a flight-capable player who is WALKING is still speed-checked.
        if (p.isFlying()) {
            cool(p, 0.6D);
            return;
        }
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

            if (com.colin.vezanticheat.utils.FallArcTracker.shouldSuppressLegitFallMovement(plugin, data, nowMs)) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
                data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
                data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.65D));
                cool(p, 0.45D);
                return;
            }

            if (EngineMovementGrace.isLikelyWallStop(er)) {
                data.setEngineSpeedOvershootTicks(0);
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.5D);
                cool(p, 0.45D);
                return;
            }

            boolean forceSpeedDetection = SpeedPatternUtil.shouldForceSpeedDetection(plugin, p, data, er, nowMs);
            if (!forceSpeedDetection
                    && MovementEnvelopeUtil.isWithinLegalMovementEnvelope(plugin, p, data, er, nowMs, name())) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineMicroRatioStreak(Math.max(0, data.getEngineMicroRatioStreak() - 1));
                data.setEngineHorizontalGainStreak(Math.max(0, data.getEngineHorizontalGainStreak() - 1));
                data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.70D));
                cool(p, 0.45D);
                return;
            }

            double jumpGrace = cfgDouble("jumpGraceMaxOffset", 0.85D) + PotionUtil.jumpArcOffsetAllowance(p);
            if (!forceSpeedDetection && MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.70D);
                cool(p, 0.45D);
                return;
            }
            if (!forceSpeedDetection
                    && EngineMovementGrace.isLikelyLegitJumpArc(plugin, p, data, er, nowMs)
                    && !SpeedPatternUtil.isYPortSlam(data, er)
                    && er.horizontalOffset <= jumpGrace) {
                data.setEngineSpeedOvershootTicks(Math.max(0, data.getEngineSpeedOvershootTicks() - 1));
                data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.75D);
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

            double advantageThr = cfgDouble("engineAdvantageThreshold", 0.10D);
            boolean speedBoost = PotionUtil.hasSpeedBoost(p);

            if (!forceSpeedDetection
                    && EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, er, nowMs, name())) {
                cool(p, 0.45D);
                return;
            }

            // Grim-style detection. With the corrected simulation physics a legit player's predicted motion
            // matches their real motion, so the horizontal offset collapses to ~0. We therefore flag purely
            // on the simulation offset, via two signals:
            //   1. offsetFlag    — a single tick whose horizontal offset exceeds the tolerance (overt speed)
            //   2. advantageFlag — sub-tolerance offset integrated over time by the noise-floored accumulator
            //                      in MovementSimulator.advanceAdvantage() (subtle / low-percentage speed)
            // plus the distinct YPort slam pattern. The previous ratio / micro-ratio / gain-streak /
            // ground-burst heuristics were artifacts of the under-predicting physics and are removed.
            boolean yport = SpeedPatternUtil.isYPortSlam(data, er) || data.getEngineYPortStreak() >= 2;
            boolean offsetFlag = er.horizontalOffset > thr && (!speedBoost || forceSpeedDetection);
            boolean advantageFlag = data.getEngineOffsetAdvantage() >= advantageThr;

            if (!yport && !offsetFlag && !advantageFlag) {
                cool(p, 0.4D);
                return;
            }

            // Sustained advantage is unambiguous impossible movement — setback immediately.
            if (data.getEngineOffsetAdvantage() >= cfgDouble("engineBlatantAdvantage", 0.18D)) {
                requestBlatant(p, data, "speed-adv=" + r(data.getEngineOffsetAdvantage()));
                fail(p, data, cfgDouble("failVl", 1.0D),
                        "adv=" + r(data.getEngineOffsetAdvantage()) + " " + er.debug);
                data.setEngineOffsetAdvantage(0.0D);
                resetBuffer(p);
                return;
            }

            int gain = (yport
                    || er.horizontalOffset > thr * 2.5D
                    || er.horizontalOffset > cfgDouble("engineBlatantHorizontalOffset", 0.55D)) ? 2 : 1;
            flagBuffered(p, data, gain,
                    "hOff=" + r(er.horizontalOffset) + " ratio=" + r(SpeedPatternUtil.horizontalRatio(er))
                            + " yport=" + yport + " adv=" + r(data.getEngineOffsetAdvantage()) + " " + er.debug);
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
