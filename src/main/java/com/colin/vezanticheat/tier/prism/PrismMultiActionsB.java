package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.BadPacketTracker;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Break block while using item (Prism tier). */
public final class PrismMultiActionsB extends TierCheck {

    public PrismMultiActionsB(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsB", CheckTier.PRISM);
    }

    @Override
    public void onDigging(Player p, PlayerData data, BadPacketTracker.DiggingActionType action, Block block) {
        if (p == null || data == null || lagGated(p, data)) return;
        long nowMs = System.currentTimeMillis();
        if (action == BadPacketTracker.DiggingActionType.RELEASE
                || UseItemTracker.isLegitSwordBlockSession(p, data, nowMs)) {
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
            blockDig(data, "dig-while-using");
            failWithMitigation(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "dig-while-using " + action,
                    com.colin.vezanticheat.verdict.PrismMitigationPolicy.Confidence.HIGH);
            resetBuffer(p);
        }
    }
}
