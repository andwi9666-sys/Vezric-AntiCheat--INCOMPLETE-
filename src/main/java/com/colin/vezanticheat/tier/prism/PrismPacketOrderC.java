package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Player;

/** Tick/input/interact ordering check (DIG_WITHOUT_MOVE). */
public final class PrismPacketOrderC extends TierCheck {

    public PrismPacketOrderC(VezAntiCheat plugin) {
        super(plugin, "PrismPacketOrderC", CheckTier.PRISM);
    }

    @Override
    public void onDigging(Player p, PlayerData data,
                          com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        evaluate(p, data, System.currentTimeMillis());
    }

    private void evaluate(Player p, PlayerData data, long nowMs) {
        if (UseItemTracker.isLegitSwordBlockSession(p, data, nowMs)) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }
        double suspicion = PrismPacketOrderSupport.score(plugin, this, data, nowMs,
                PrismPacketOrderSupport.OrderKind.DIG_WITHOUT_MOVE);
        double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.55D);
        if (suspicion < threshold) {
            coolBuffer(p, 1);
            decay(p, 0.3D);
            return;
        }
        if (incrementBuffer(p, suspicion >= threshold * 1.35D ? 2 : 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "order suspicion=" + round3(suspicion) + " kind=DIG_WITHOUT_MOVE");
            resetBuffer(p);
        }
    }
}
