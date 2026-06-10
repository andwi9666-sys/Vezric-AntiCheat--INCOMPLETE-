package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FlyUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Decomposed vertical offset sub-signal (fly/step/jump envelope corroboration).
 */
public final class SimulationOffsetVertical extends AbstractMovementTierCheck {

    public SimulationOffsetVertical(VezAntiCheat plugin) {
        super(plugin, "SimulationOffsetVertical", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (result.couldSkipTick) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        if (FlyUtil.engineVerticalGrace(plugin, p, data, result.verticalOffset, dy, distH,
                result.clientGround, "PredictionFly")) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.22D);
        double vOff = result.verticalOffset;
        if (vOff <= threshold || result.offset < threshold * 0.75D) {
            cool(p, 0.3D);
            return;
        }

        int gain = vOff > threshold * 2.5D ? 2 : 1;
        flagBuffered(p, data, gain, "vOff=" + r(vOff) + " dy=" + r(dy) + " off=" + r(result.offset) + " " + result.debug);
    }
}
