package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.BadPacketTracker;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Block place and entity interact in the same packet window (Prism tier). */
public final class PrismMultiActionsF extends TierCheck {

    public PrismMultiActionsF(VezAntiCheat plugin) {
        super(plugin, "PrismMultiActionsF", CheckTier.PRISM);
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {
        if (p == null || data == null || lagGated(p, data)) return;
        long nowMs = System.currentTimeMillis();
        if (UseItemTracker.isLegitSwordBlockSession(p, data, nowMs)) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        BadPacketTracker tracker = data.badPackets();
        if (!tracker.placingThisWindow() || !tracker.attackedThisWindow()) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D), "place+interact same window");
            resetBuffer(p);
        } else {
            verbose(p, "buf=" + buffer(p.getUniqueId()) + "/" + bufferToFlag()
                    + " place+interact same window");
        }
    }
}
