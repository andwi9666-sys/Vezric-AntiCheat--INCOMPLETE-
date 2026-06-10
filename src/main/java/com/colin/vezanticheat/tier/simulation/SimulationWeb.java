package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Cobweb movement sub-signal.
 */
public final class SimulationWeb extends AbstractMovementTierCheck {

    public SimulationWeb(VezAntiCheat plugin) {
        super(plugin, "SimulationWeb", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.inWeb) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.06D);
        if (result.offset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1, "web off=" + r(result.offset) + " " + result.debug);
    }
}
