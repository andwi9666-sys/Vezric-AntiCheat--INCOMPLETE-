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
 * Decomposed horizontal offset sub-signal for corroboration and standalone SIMULATION flags.
 *
 * <p>Uses higher thresholds than PREDICTION tier; does not drive setbacks on its own.</p>
 */
public final class SimulationOffsetHorizontal extends AbstractMovementTierCheck {

    public SimulationOffsetHorizontal(VezAntiCheat plugin) {
        super(plugin, "SimulationOffsetHorizontal", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (result.couldSkipTick) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.isLikelyLegitGroundLocomotion(result)) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (distH < 0.04D) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.20D);
        double hOff = result.horizontalOffset;
        if (hOff <= threshold || result.offset < threshold * 0.75D) {
            cool(p, 0.3D);
            return;
        }

        int gain = hOff > threshold * 2.5D ? 2 : 1;
        flagBuffered(p, data, gain, "hOff=" + r(hOff) + " off=" + r(result.offset) + " " + result.debug);
    }
}
