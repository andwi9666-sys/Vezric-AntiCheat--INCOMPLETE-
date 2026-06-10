package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

/** Duplicate / tiny-interval flying packet burst detection. */
public final class PrismBadPacketsB extends PrismBadPacketCheck {

    public PrismBadPacketsB(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsB", 'B');
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return;

        long swingGraceMs = plugin.tierCfg().checkLong(name(), "swingGraceMs", 350L);
        if (data.getLastArmSwingPacket() > 0L && (nowMs - data.getLastArmSwingPacket()) <= swingGraceMs) {
            decayLetterBuffer(data, 1);
            return;
        }

        // Rotation/ground keepalive and post-setback resync bursts are not duplicate spam.
        if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
            decayLetterBuffer(data, 1);
            return;
        }

        if (plugin.tierCfg().gateByLag() && plugin.tps() != null && plugin.tps().getTps() < plugin.tierCfg().minTps()) {
            setLetterBuffer(data, 0);
            return;
        }

        if (LagProfileUtil.activeScore(plugin, p, data, nowMs)
                >= plugin.tierCfg().checkDouble(name(), "lagCoverScore", 1.15D)) {
            decayLetterBuffer(data, 2);
            decay(p, 0.5D);
            return;
        }

        long diff = data.getLastFlyingIntervalMs();
        if (diff <= 0L) return;

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 5);
        long resetGap = plugin.tierCfg().checkLong(name(), "resetGapMs", 110L);

        if (diff > resetGap) {
            decayLetterBuffer(data, 2);
            decay(p, 0.3D);
            return;
        }

        long rotAge = PrismPacketOrderSupport.effectiveRotationAgeMs(data, nowMs);
        long minNoRotationMs = plugin.tierCfg().checkLong(name(), "minNoRotationMs", 120L);

        boolean duplicateSpam = diff <= plugin.tierCfg().checkLong(name(), "maxDuplicateIntervalMs", 5L);
        boolean tinyBurst = diff <= plugin.tierCfg().checkLong(name(), "maxTinyIntervalMs", 12L)
                && rotAge > minNoRotationMs;

        if (duplicateSpam || tinyBurst) {
            int add = duplicateSpam ? 2 : 1;
            int buf = incrementLetterBuffer(p, data, add, bufferToFlag,
                    "packetBurst diff=" + diff + " rotAgo=" + rotAge);
            recordBlatant(data, add, nowMs);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                        "packetBurst diff=" + diff + " dup=" + duplicateSpam + " tiny=" + tinyBurst);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.25D);
        }
    }
}
