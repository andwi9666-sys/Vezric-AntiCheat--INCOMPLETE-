package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Entity push / piston uncertainty sub-signal.
 *
 * <p>Uses {@link EngineResult#entityPushOffset()} populated when piston push or boat proximity
 * contributes to unexplained horizontal movement.</p>
 */
public final class SimulationEntityPush extends AbstractMovementTierCheck {

    public SimulationEntityPush(VezAntiCheat plugin) {
        super(plugin, "SimulationEntityPush", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;

        double pushOff = result.entityPushOffset();
        if (pushOff <= 0.0D) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.05D);
        if (pushOff <= threshold) {
            cool(p, 0.3D);
            return;
        }

        flagBuffered(p, data, 1, "pushOff=" + r(pushOff) + " off=" + r(result.offset) + " " + result.debug);
    }
}
