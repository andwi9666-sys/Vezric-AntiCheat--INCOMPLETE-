package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/** Transaction confirmation burst after silence (Prism tier). */
public final class PrismTransactionB extends TierCheck {

    public PrismTransactionB(VezAntiCheat plugin) {
        super(plugin, "PrismTransactionB", CheckTier.PRISM);
    }

    @Override
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (actionId < 0) return;

        int burst = data.getTransactionState().getClientConfirmBurst();
        int burstThreshold = plugin.tierCfg().checkInt(name(), "burstThreshold", 8);
        if (!data.getTransactionState().isClientConfirmBurstAfterSilence() || burst < burstThreshold) {
            coolBuffer(p, 1);
            decay(p, 0.30D);
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);
        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "txn-burst=" + burst + " id=" + actionId);
            resetBuffer(p);
        } else {
            verbose(p, "buf=" + buffer(p.getUniqueId()) + "/" + bufferToFlag + " txn-burst=" + burst);
        }
    }
}
