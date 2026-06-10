package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Player;

/** Arm swing while using item (Prism tier). */
public final class PrismMultiActionsE extends TierCheck {

    public PrismMultiActionsE(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsE", CheckTier.PRISM);
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        long nowMs = System.currentTimeMillis();
        if (UseItemTracker.isLegitSwordBlockSession(p, data, nowMs)) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }
        if (!UseItemTracker.isUsingItem(p, data)) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 0.9D), "swing-while-using");
            resetBuffer(p);
        } else {
            verbose(p, "buf=" + buffer(p.getUniqueId()) + "/" + bufferToFlag() + " swing-while-using");
        }
    }
}
