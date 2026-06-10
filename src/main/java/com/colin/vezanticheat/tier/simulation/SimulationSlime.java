package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Slime block bounce sub-signal.
 */
public final class SimulationSlime extends AbstractMovementTierCheck {

    public SimulationSlime(VezAntiCheat plugin) {
        super(plugin, "SimulationSlime", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.onSlime) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.08D);
        double slimeOff = result.slimeOffset();
        if (slimeOff <= threshold) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1, "slimeOff=" + r(slimeOff) + " vOff=" + r(result.verticalOffset) + " " + result.debug);
    }
}
