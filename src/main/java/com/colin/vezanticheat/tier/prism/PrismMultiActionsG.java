package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/** Attack while inside a vehicle (Prism tier). */
public final class PrismMultiActionsG extends TierCheck {

    public PrismMultiActionsG(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsG", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!p.isInsideVehicle()) {
            coolBuffer(p, 1);
            decay(p, 0.30D);
            return;
        }

        fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D), "attack-in-vehicle");
        blockAttack(p, data, "PrismMultiActionsG attack-in-vehicle");
    }
}
