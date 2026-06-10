package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Phase / no-clip detection: movement through solid collision axes.
 *
 * <p>Engine path: horizontal collision on X/Z with horizontal offset above threshold.
 * Legacy path: {@link PredictionResult#phaseViolation} with solid-path corroboration.</p>
 */
public final class PredictionPhase extends AbstractMovementTierCheck {

    public PredictionPhase(VezAntiCheat plugin) {
        super(plugin, "PredictionPhase", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        if (engineActive()) {
            EngineResult er = engineResult(data);
            if (er == null) {
                cool(p, 0.35D);
                return;
            }
            if (EngineMovementGrace.shouldExemptPhaseFlag(plugin, p, data, er, nowMs, name())) {
                cool(p, 0.35D);
                return;
            }

            double thr = cfgDouble("enginePhaseOffset", 0.10D);
            boolean throughWall = (er.collisionX || er.collisionZ) && er.horizontalOffset > thr;
            if (!throughWall) {
                cool(p, 0.35D);
                return;
            }

            int gain = er.horizontalOffset > thr * 1.5D ? 2 : 1;
            if (flagBuffered(p, data, gain,
                    "throughWall hOff=" + r(er.horizontalOffset) + " " + er.debug)) {
                predictionSetback(p, data, "simulation");
            }
            return;
        }

        PredictionResult result = predictionResult(data);
        if (result == null || result.inLiquid || result.climbable || result.inWeb
                || result.weirdSurface || !result.cleanMovement) {
            cool(p, 0.35D);
            return;
        }
        if (!result.phaseViolation) {
            cool(p, 0.35D);
            return;
        }

        int gain = (result.toSolid >= 4 || result.pathSolid >= 3) ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "toSolid=" + result.toSolid + " pathSolid=" + result.pathSolid + " " + result.debugSummary)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
