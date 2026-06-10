package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/** Balance-based positive timer detection (ported from TimerA). */
public final class CharTimerLegacy extends TierCheck {

    public CharTimerLegacy(VezAntiCheat plugin) {
        super(plugin, "CharTimerLegacy", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) {
            resetTimerState(data);
            return;
        }

        if (plugin.tierCfg().gateByLag() && plugin.tps() != null
                && plugin.tps().getTps() < plugin.tierCfg().minTps()) {
            resetTimerState(data);
            return;
        }

        long interval = data.getLastFlyingIntervalMs();
        if (interval <= 0L) return;

        long minInterval = plugin.tierCfg().checkLong(name(), "minIntervalMs", 5L);
        long maxInterval = plugin.tierCfg().checkLong(name(), "maxIntervalMs", 200L);
        if (interval < minInterval || interval > maxInterval) {
            decay(p, 0.20);
            return;
        }

        long balance = data.getTimerABalance() + 50L - interval;
        long balanceFloor = plugin.tierCfg().checkLong(name(), "balanceFloorMs", -1000L);
        data.setTimerABalance(Math.max(balanceFloor, balance));

        long threshold = data.getTimerAThreshold();
        long baseThreshold = plugin.tierCfg().checkLong(name(), "baseThreshold", 250L);
        if (threshold <= 0L) {
            threshold = baseThreshold;
            data.setTimerAThreshold(threshold);
        }

        if (balance > threshold) {
            long increment = plugin.tierCfg().checkLong(name(), "thresholdIncrement", 50L);
            data.setTimerAThreshold(threshold + increment);
            int vb = data.getTimerAVerbose() + 1;
            data.setTimerAVerbose(vb);

            int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 5);
            if (vb >= bufferToFlag) {
                fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0),
                        "timer bal=" + balance + " interval=" + interval + " threshold=" + threshold);
                if (setbackEnabled()) {
                    blockMovement(data, "timer");
                }
                data.setTimerAVerbose(0);
                data.setTimerAThreshold(baseThreshold);
                data.setTimerABalance(0L);
            } else {
                verbose(p, "timer bal=" + balance + " vb=" + vb + "/" + bufferToFlag);
            }
        } else {
            data.setTimerAVerbose(Math.max(0, data.getTimerAVerbose() - 1));
            decay(p, 0.35);
        }
    }

    private void resetTimerState(PlayerData data) {
        data.setTimerAVerbose(0);
        data.setTimerABalance(0L);
        data.setTimerAThreshold(plugin.tierCfg().checkLong(name(), "baseThreshold", 250L));
    }
}
