package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.LiquidLocomotionUtil;
import org.bukkit.entity.Player;

/**
 * Liquid (water) movement sub-signal without climbable context.
 */
public final class SimulationLiquid extends AbstractMovementTierCheck {

    public SimulationLiquid(VezAntiCheat plugin) {
        super(plugin, "SimulationLiquid", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        boolean surfaceJesus = LiquidLocomotionUtil.isWalkingOnWaterSurface(p, data, result);
        if ((!result.inWater && !surfaceJesus) || result.onClimbable) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptLiquidLocomotionFlag(plugin, data, result, nowMs)) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.06D);
        double surfaceThr = cfgDouble("surfaceThreshold", 0.055D);
        double hOff = result.horizontalOffset;
        boolean violation = hOff > threshold || (surfaceJesus && hOff > surfaceThr);
        if (!violation) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1,
                "liquidOff=" + r(result.liquidOffset()) + " hOff=" + r(hOff)
                        + " surface=" + surfaceJesus + " " + result.debug);
    }
}
