package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

/** Missing transaction responses while movement continues (Prism tier). */
public final class PrismTransactionA extends TierCheck {

    public PrismTransactionA(VezAntiCheat plugin) {
        super(plugin, "PrismTransactionA", CheckTier.PRISM);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (!plugin.getConfig().getBoolean("combat-engine.transactions", true)) return;

        int sentWithoutAck = data.getTransactionState().getConsecutiveSentWithoutAck();
        int threshold = plugin.tierCfg().checkInt(name(), "sentWithoutAckThreshold", 40);
        if (sentWithoutAck < threshold) {
            coolBuffer(p, 1);
            decay(p, 0.25D);
            return;
        }

        long sinceConfirm = data.getTransactionState().getLastClientConfirmMs();
        if (sinceConfirm > 0L && (nowMs - sinceConfirm) < plugin.tierCfg().checkLong(name(), "graceMs", 2000L)) {
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 3);
        if (incrementBuffer(p, 1)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "missing-txn sent=" + sentWithoutAck + " sinceConfirm=" + (nowMs - sinceConfirm) + "ms");
            MovementEnforcement.blockCurrentMovementPacket(data,
                    "PrismTransactionA missing acks=" + sentWithoutAck);
            resetBuffer(p);
        } else if (buffer(p.getUniqueId()) >= bufferToFlag - 1) {
            verbose(p, "buf=" + buffer(p.getUniqueId()) + "/" + bufferToFlag + " missing-txn=" + sentWithoutAck);
        }
    }
}
