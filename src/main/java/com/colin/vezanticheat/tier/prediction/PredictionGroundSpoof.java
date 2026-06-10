package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FlyUtil;
import com.colin.vezanticheat.utils.GroundSpoofTracker;
import com.colin.vezanticheat.utils.NoFallUtil;
import org.bukkit.entity.Player;

/**
 * Ground spoof: client claims on-ground while simulation predicts airborne with vertical motion.
 */
public final class PredictionGroundSpoof extends AbstractMovementTierCheck {

    public PredictionGroundSpoof(VezAntiCheat plugin) {
        super(plugin, "PredictionGroundSpoof", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        if (engineActive()) {
            EngineResult er = engineResult(data);
            if (er == null) {
                cool(p, 0.3D);
                return;
            }
            if (GroundSpoofTracker.shouldSkipGroundSpoofCheck(p, data, er, nowMs, plugin)) {
                cool(p, 0.3D);
                return;
            }
            if (EngineMovementGrace.shouldExemptGroundSpoofFlag(plugin, data, er, nowMs, name())) {
                cool(p, 0.3D);
                return;
            }

            double minDy = cfgDouble("minDy",
                    plugin.getConfig().getDouble("prediction.ground-spoof.min-dy", 0.05D));
            double minDescentDy = cfgDouble("minDescentDy",
                    plugin.getConfig().getDouble("nofall.ground-spoof.min-descent-dy", -0.04D));
            double dy = er.actual == null ? 0.0D : er.actual.getY();

            boolean serverGround = data.isServerGround();
            boolean mismatch = er.clientGround && !er.predictedOnGround && Math.abs(dy) > minDy;
            boolean serverMismatch = er.clientGround && !serverGround && Math.abs(dy) > minDy * 0.5D;
            boolean descentSpoof = er.clientGround && dy < -minDy;
            NoFallUtil.Context ctx = NoFallUtil.analyze(plugin, p, data);
            boolean airBelow = GroundSpoofTracker.isAirBelowGroundClaim(ctx);

            if (!mismatch && !serverMismatch && !descentSpoof && !airBelow) {
                cool(p, 0.3D);
                return;
            }

            int gain = airBelow || serverMismatch ? 2 : 1;
            flagBuffered(p, data, gain,
                    "clientGround=true predictedGround=" + er.predictedOnGround
                            + " serverGround=" + serverGround
                            + " dy=" + r(dy) + " airBelow=" + (airBelow ? 1 : 0) + " " + er.debug);
            return;
        }

        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            cool(p, 0.3D);
            return;
        }

        PredictionResult result = predictionResult(data);
        FlyUtil.Context flyCtx = FlyUtil.analyze(plugin, p, data);
        if (flyCtx != null
                && (flyCtx.weirdSurface || EngineMovementGrace.hasStepSurfaceNear(flyCtx.to))
                && FlyUtil.isLikelyLegitStepMotion(plugin, name(), flyCtx.dy, flyCtx.distH, flyCtx, true)) {
            cool(p, 0.3D);
            return;
        }

        if (result == null || result.inLiquid || result.climbable || result.inWeb || result.groundUncertain) {
            cool(p, 0.3D);
            return;
        }

        double minDy = plugin.getConfig().getDouble("prediction.ground-spoof.min-dy", 0.05D);
        if (!result.clientGround || result.effectiveServerGround || Math.abs(result.dy) <= minDy) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1,
                "clientGround=true effectiveGround=false rawGround=" + result.serverGround
                        + " dy=" + r(result.dy) + " " + result.debugSummary);
    }
}
