package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FlyPatternUtil;
import com.colin.vezanticheat.utils.FlyPhysicsTracker;
import com.colin.vezanticheat.utils.FlyUtil;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import com.colin.vezanticheat.utils.NoFallUtil;
import com.colin.vezanticheat.utils.SpeedUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Vertical movement envelope vs simulation (fly / hover / illegal ascent / glide fly).
 */
public final class PredictionFly extends AbstractMovementTierCheck {

    public PredictionFly(VezAntiCheat plugin) {
        super(plugin, "PredictionFly", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.getAllowFlight() || p.isFlying()) {
            cool(p, 0.6D);
            return;
        }

        if (engineActive()) {
            if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
                cool(p, 0.2D);
                return;
            }

            EngineResult er = engineResult(data);
            if (er == null) {
                cool(p, 0.4D);
                return;
            }

            Vector actual = er.actual == null ? new Vector() : er.actual;
            double dy = actual.getY();
            double distH = Math.hypot(actual.getX(), actual.getZ());

            if (NoFallUtil.shouldExemptFallDamagePrediction(plugin, p, data, er, nowMs) && dy < -0.05D) {
                cool(p, 0.4D);
                return;
            }

            int hoverTicks = data.getEngineHoverTicks();
            int hoverBlatant = cfgInt("engineHoverBlatantTicks", 6);
            int hoverMin = cfgInt("engineHoverMinTicks", 4);
            double hoverThr = cfgDouble("engineHoverVerticalOffset", 0.045D);

            if (FlyPatternUtil.isSuspiciousGlide(data, er, nowMs)
                    || FlyPatternUtil.isSuspiciousAirborneTravel(data, er, nowMs)) {
                if (flagBuffered(p, data, 2,
                        "glide t=" + data.getEngineGlideTicks() + " air=" + data.getEngineAirborneTicks()
                                + " dy=" + r(dy) + " h=" + r(distH) + " " + er.debug)) {
                    requestBlatant(p, data, "glide-fly");
                }
                return;
            }

            if (FlyPatternUtil.isSuspiciousBobbingHover(data, er) && er.verticalOffset > hoverThr) {
                flagBuffered(p, data, 2,
                        "bob-hover t=" + hoverTicks + " rev=" + data.getYReversalCount()
                                + " vOff=" + r(er.verticalOffset) + " " + er.debug);
                return;
            }

            if (hoverTicks >= hoverBlatant && Math.abs(dy) < 0.03D && !er.clientGround) {
                requestBlatant(p, data, "hover t=" + hoverTicks);
                fail(p, data, cfgDouble("failVl", 1.0D),
                        "hover t=" + hoverTicks + " vOff=" + r(er.verticalOffset) + " " + er.debug);
                resetBuffer(p);
                return;
            }

            if (FlyPatternUtil.isSuspiciousHover(data, er) && er.verticalOffset > hoverThr) {
                flagBuffered(p, data, 2,
                        "hover t=" + hoverTicks + " vOff=" + r(er.verticalOffset) + " " + er.debug);
                return;
            }

            if (hoverTicks >= hoverMin && Math.abs(dy) < 0.03D && !er.clientGround
                    && er.verticalOffset > hoverThr) {
                flagBuffered(p, data, 2,
                        "hover t=" + hoverTicks + " vOff=" + r(er.verticalOffset) + " " + er.debug);
                return;
            }

            double blatantV = cfgDouble("engineBlatantVerticalOffset", 0.32D);
            if (!er.clientGround && !er.inWater && !er.onClimbable && !er.inWeb
                    && data.getEngineAirborneTicks() >= 5
                    && er.verticalOffset > blatantV
                    && dy > -0.45D && dy < 0.30D) {
                if (flagBuffered(p, data, 2,
                        "airVOff=" + r(er.verticalOffset) + " air=" + data.getEngineAirborneTicks()
                                + " dy=" + r(dy) + " " + er.debug)) {
                    requestBlatant(p, data, "air-fly");
                }
                return;
            }

            if (!FlyPhysicsTracker.hasActiveBobbingEvidence(data)
                    && EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, er, nowMs, name())) {
                double decayRate = EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs) ? 0.55D : 0.45D;
                cool(p, decayRate);
                return;
            }

            double thr = cfgDouble("engineVerticalOffset", 0.08D);
            if (er.verticalOffset <= thr) {
                cool(p, 0.4D);
                return;
            }

            int gain = er.verticalOffset > thr * 2.5D ? 2 : 1;
            flagBuffered(p, data, gain,
                    "vOff=" + r(er.verticalOffset) + " " + er.debug);
            return;
        }

        if (data.isVelocityExempt() || data.isTeleportExempt() || data.isPotionExempt()
                || SpeedUtil.isRecentExplosion(plugin, data, nowMs)
                || EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            cool(p, 0.35D);
            return;
        }

        PredictionResult result = predictionResult(data);
        FlyUtil.Context flyCtx = FlyUtil.analyze(plugin, p, data);

        if (flyCtx != null && FlyUtil.isLikelyLegitJumpPhase(plugin, data, flyCtx)
                && result != null
                && result.verticalOffset <= cfgDouble("jumpGraceMaxOffset", 0.32D)) {
            cool(p, 0.45D);
            return;
        }
        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)
                && result != null
                && result.verticalOffset <= cfgDouble("jumpGraceMaxOffset", 0.32D)
                && result.dy >= cfgDouble("jumpGraceMinDy", -0.20D)) {
            cool(p, 0.45D);
            return;
        }
        if (flyCtx != null
                && FlyUtil.isLikelyLegitStepMotion(plugin, name(), flyCtx.dy, flyCtx.distH, flyCtx, data.wasLastClientGround())
                && result != null
                && result.verticalOffset <= cfgDouble("stepGraceMaxOffset", 0.32D)) {
            cool(p, 0.45D);
            return;
        }
        if (flyCtx != null
                && FlyUtil.isLikelyLegitDropMotion(plugin, name(), flyCtx.dy, flyCtx.distH, flyCtx, data.wasLastClientGround())
                && result != null
                && result.verticalOffset <= cfgDouble("dropGraceMaxOffset", 0.30D)) {
            cool(p, 0.45D);
            return;
        }
        if (result == null || result.inLiquid || result.climbable || result.inWeb
                || (result.weirdSurface && !flyCtxNearStep(flyCtx))
                || !result.cleanMovement) {
            cool(p, 0.35D);
            return;
        }

        boolean suspicious = result.hoverViolation || (result.simulated && result.verticalViolation);
        if (!suspicious) {
            cool(p, 0.35D);
            return;
        }

        int gain = (result.verticalOffset > 0.14D || result.hoverViolation) ? 2 : 1;
        flagBuffered(p, data, gain,
                "offsetV=" + r(result.verticalOffset) + " hover=" + result.hoverViolation + " " + result.debugSummary);
    }

    private boolean flyCtxNearStep(FlyUtil.Context ctx) {
        return ctx != null && (ctx.weirdSurface || ctx.blockNearby);
    }
}
