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
 * Jump envelope sub-signal: upward movement with vertical offset while not in KB/explosion tick.
 */
public final class SimulationJump extends AbstractMovementTierCheck {

    public SimulationJump(VezAntiCheat plugin) {
        super(plugin, "SimulationJump", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (result.knockbackTick || result.explosionTick || result.couldSkipTick) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        if (dy < cfgDouble("minRise", 0.10D)) {
            cool(p, 0.3D);
            return;
        }

        double jumpOff = result.jumpOffset();
        double threshold = cfgDouble("threshold", 0.18D);
        if (jumpOff <= threshold || result.offset < threshold * 0.75D) {
            cool(p, 0.3D);
            return;
        }

        int gain = jumpOff > threshold * 2.0D ? 2 : 1;
        flagBuffered(p, data, gain, "jumpOff=" + r(jumpOff) + " dy=" + r(dy) + " vOff=" + r(result.verticalOffset) + " " + result.debug);
    }
}
