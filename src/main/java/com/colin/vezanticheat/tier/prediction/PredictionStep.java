package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Step cheat: vertical offset above step-height envelope near slab/stair surfaces.
 *
 * <p>Requires engine active. Only evaluates when step context exists near from/to positions
 * ({@link EngineMovementGrace#hasStepSurfaceNear}). Flags on {@link EngineResult#verticalOffset}.</p>
 */
public final class PredictionStep extends AbstractMovementTierCheck {

    public PredictionStep(VezAntiCheat plugin) {
        super(plugin, "PredictionStep", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null) {
            cool(p, 0.4D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.4D);
            return;
        }

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        boolean stepContext = EngineMovementGrace.hasStepSurfaceNear(from)
                || EngineMovementGrace.hasStepSurfaceNear(to);
        if (!stepContext) {
            cool(p, 0.35D);
            return;
        }

        double threshold = cfgDouble("engineVerticalOffset", 0.12D);
        if (result.verticalOffset <= threshold) {
            cool(p, 0.35D);
            return;
        }

        int gain = result.verticalOffset > threshold * 2.0D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "vOff=" + r(result.verticalOffset) + " step=1 " + result.debug)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
