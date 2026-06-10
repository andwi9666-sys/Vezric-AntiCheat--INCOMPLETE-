package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.LiquidLocomotionUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Climbable (ladder/vine) movement sub-signal, including fast-ladder vertical overspeed.
 */
public final class SimulationClimbable extends AbstractMovementTierCheck {

    public SimulationClimbable(VezAntiCheat plugin) {
        super(plugin, "SimulationClimbable", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!result.onClimbable) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptLiquidLocomotionFlag(plugin, data, result, nowMs)) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        double vOff = result.verticalOffset;
        double legitMaxDy = cfgDouble("ladderLegitMaxDy", LiquidLocomotionUtil.ladderLegitMaxDy(plugin));
        double legitMaxVOff = cfgDouble("ladderLegitMaxVOff", LiquidLocomotionUtil.ladderLegitMaxVOff(plugin));
        double blatantDy = cfgDouble("ladderBlatantDy", LiquidLocomotionUtil.ladderBlatantDy(plugin));

        if (!LiquidLocomotionUtil.isLegitLadderMotion(dy, vOff, legitMaxDy, legitMaxVOff)) {
            int gain = LiquidLocomotionUtil.isBlatantFastLadder(dy, vOff, legitMaxDy, blatantDy, legitMaxVOff) ? 2 : 1;
            flagBuffered(p, data, gain,
                    "fastLadder dy=" + r(dy) + " vOff=" + r(vOff) + " off=" + r(result.offset) + " " + result.debug);
            return;
        }

        cool(p, 0.3D);
    }
}
