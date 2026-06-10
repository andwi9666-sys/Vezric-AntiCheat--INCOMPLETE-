package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Web movement cheat: horizontal/vertical offset while inside cobweb blocks.
 *
 * <p>Engine-only. Requires {@link EngineResult#inWeb} and compares total offset against threshold
 * after standard engine grace exemptions.</p>
 */
public final class PredictionWeb extends AbstractMovementTierCheck {

    public PredictionWeb(VezAntiCheat plugin) {
        super(plugin, "PredictionWeb", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null || !result.inWeb) {
            cool(p, 0.35D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.35D);
            return;
        }

        double threshold = cfgDouble("engineOffsetThreshold", 0.08D);
        if (result.offset <= threshold) {
            cool(p, 0.35D);
            return;
        }

        int gain = result.offset > threshold * 2.0D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "web off=" + r(result.offset) + " hOff=" + r(result.horizontalOffset)
                        + " " + result.debug)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
