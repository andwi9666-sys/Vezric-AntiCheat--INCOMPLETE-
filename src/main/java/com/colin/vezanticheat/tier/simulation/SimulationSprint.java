package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Illegal sprint sub-signal: client sprinting with horizontal offset above sprint envelope.
 *
 * <p>Uses {@link EngineResult#illegalSprint} computed when sprinting without KB/item-use
 * and horizontal offset exceeds configured sprint threshold.</p>
 */
public final class SimulationSprint extends AbstractMovementTierCheck {

    public SimulationSprint(VezAntiCheat plugin) {
        super(plugin, "SimulationSprint", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.illegalSprint) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.12D);
        if (result.horizontalOffset <= threshold) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new org.bukkit.util.Vector() : result.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (distH < 0.06D) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1,
                "illegalSprint hOff=" + r(result.horizontalOffset) + " off=" + r(result.offset)
                        + " " + result.debug);
    }
}
