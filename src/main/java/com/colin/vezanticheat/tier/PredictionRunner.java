package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import org.bukkit.entity.Player;

import java.util.List;

public final class PredictionRunner extends AbstractTierRunner {
    public PredictionRunner(VezAntiCheat plugin, List<TierCheck> checks) {
        super(plugin, CheckTier.PREDICTION, checks);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p != null && data != null && ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) {
            for (TierCheck check : checks) {
                if (check instanceof AbstractMovementTierCheck) {
                    ((AbstractMovementTierCheck) check).tickItemUseMovementGrace(p, data);
                }
            }
            return;
        }
        super.onFlyingPacket(p, data, nowMs);
    }
}
