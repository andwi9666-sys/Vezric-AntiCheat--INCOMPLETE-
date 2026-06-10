package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Player;

/**
 * NoSlow decomposed sub-signal: excess horizontal movement while using items.
 */
public final class SimulationNoSlow extends AbstractMovementTierCheck {

    public SimulationNoSlow(VezAntiCheat plugin) {
        super(plugin, "SimulationNoSlow", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;

        boolean compensatedUse = UseItemTracker.isCompensatedItemUse(p, data, nowMs);
        if (!result.usingItem && !compensatedUse) {
            cool(p, 0.3D);
            return;
        }
        if (!compensatedUse
                && EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.025D);
        double excess = result.noSlowExcess;
        if (excess <= threshold && result.horizontalOffset <= threshold + 0.01D) {
            cool(p, 0.3D);
            return;
        }

        int gain = excess > threshold * 2.5D || result.horizontalOffset > threshold * 2.0D ? 2 : 1;
        flagBuffered(p, data, gain,
                "noSlowExcess=" + r(excess) + " hOff=" + r(result.horizontalOffset)
                        + " fakeRel=" + (compensatedUse && !UseItemTracker.isUsingItem(p, data))
                        + " " + result.debug);
    }
}
