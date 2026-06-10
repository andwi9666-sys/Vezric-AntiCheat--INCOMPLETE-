package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

/** Positionless flying packet streak detection. */
public final class PrismBadPacketsF extends PrismBadPacketCheck {

    public PrismBadPacketsF(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsF", 'F');
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return;

        if (plugin.tierCfg().gateByLag() && plugin.tps() != null && plugin.tps().getTps() < plugin.tierCfg().minTps()) {
            setLetterBuffer(data, 0);
            return;
        }

        if (LagProfileUtil.activeScore(plugin, p, data, nowMs)
                >= plugin.tierCfg().checkDouble(name(), "lagCoverScore", 0.9D)) {
            decayLetterBuffer(data, 2);
            decay(p, 0.4D);
            return;
        }

        int streak = data.badPackets().positionlessFlyingStreak();
        int maxStreak = plugin.tierCfg().checkInt(name(), "maxStreak", 16);
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 5);
        long sincePositionMs = data.badPackets().lastPositionPacketMs() > 0L
                ? nowMs - data.badPackets().lastPositionPacketMs() : Long.MAX_VALUE;
        long minPositionlessMs = plugin.tierCfg().checkLong(name(), "minPositionlessMs", 400L);

        if (streak >= maxStreak && sincePositionMs >= minPositionlessMs) {
            int buf = incrementLetterBuffer(p, data, 1, bufferToFlag, "poslessStreak=" + streak);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                        "poslessStreak=" + streak);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.25D);
        }
    }
}
