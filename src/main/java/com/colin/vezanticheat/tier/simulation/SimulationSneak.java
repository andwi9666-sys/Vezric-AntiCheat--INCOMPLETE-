package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Illegal sneak sub-signal: client sneaking with horizontal offset above sneak envelope.
 */
public final class SimulationSneak extends AbstractMovementTierCheck {

    public SimulationSneak(VezAntiCheat plugin) {
        super(plugin, "SimulationSneak", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.illegalSneak) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.04D);
        if (result.horizontalOffset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1,
                "illegalSneak hOff=" + r(result.horizontalOffset) + " " + result.debug);
    }
}
