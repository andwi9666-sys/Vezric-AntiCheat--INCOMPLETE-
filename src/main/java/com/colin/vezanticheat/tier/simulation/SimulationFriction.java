package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Ice/slime friction mismatch sub-signal.
 *
 * <p>Requires {@link EngineResult#onIce} or {@link EngineResult#onSlime} with total offset
 * above friction threshold ({@link EngineResult#frictionMismatch()}).</p>
 */
public final class SimulationFriction extends AbstractMovementTierCheck {

    public SimulationFriction(VezAntiCheat plugin) {
        super(plugin, "SimulationFriction", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.frictionMismatch()) {
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

        flagBuffered(p, data, 1,
                "friction off=" + r(result.offset) + " ice=" + result.onIce + " slime=" + result.onSlime
                        + " " + result.debug);
    }
}
