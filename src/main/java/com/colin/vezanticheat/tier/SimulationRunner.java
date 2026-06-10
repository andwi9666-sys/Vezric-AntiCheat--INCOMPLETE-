package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import org.bukkit.entity.Player;

import java.util.List;

public final class SimulationRunner extends AbstractTierRunner {
    public SimulationRunner(VezAntiCheat plugin, List<TierCheck> checks) {
        super(plugin, CheckTier.SIMULATION, checks);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p != null && data != null && ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) {
            for (TierCheck check : checks) {
                if (check instanceof AbstractMovementTierCheck) {
                    ((AbstractMovementTierCheck) check).tickItemUseMovementGrace(p, data);
                }
            }
            return;
        }
        super.onEngineResult(p, data, result, nowMs);
    }
}
