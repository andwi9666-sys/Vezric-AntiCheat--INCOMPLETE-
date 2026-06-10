package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Water movement cheat: offset while submerged (distinct from Jesus climbable path).
 *
 * <p>Engine-only. Requires {@link EngineResult#inWater} without climbable, flags on
 * horizontal offset above threshold.</p>
 */
public final class PredictionWater extends AbstractMovementTierCheck {

    public PredictionWater(VezAntiCheat plugin) {
        super(plugin, "PredictionWater", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null || !result.inWater || result.onClimbable) {
            cool(p, 0.35D);
            return;
        }
        if (EngineMovementGrace.shouldExemptLiquidLocomotionFlag(plugin, data, result, nowMs)) {
            cool(p, 0.35D);
            return;
        }

        double threshold = cfgDouble("engineHorizontalOffset", 0.10D);
        if (result.horizontalOffset <= threshold) {
            cool(p, 0.35D);
            return;
        }

        int gain = result.horizontalOffset > threshold * 2.0D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "water hOff=" + r(result.horizontalOffset) + " off=" + r(result.offset)
                        + " " + result.debug)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
