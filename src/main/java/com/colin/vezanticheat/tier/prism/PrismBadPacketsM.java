package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

/** Multi-target interact window detection (distinct entities per flying window). */
public final class PrismBadPacketsM extends PrismBadPacketCheck {

    public PrismBadPacketsM(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsM", 'M');
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        long now = System.currentTimeMillis();
        if (LagProfileUtil.activeScore(plugin, p, data, now)
                >= plugin.tierCfg().checkDouble(name(), "lagCoverScore", 0.9D)) {
            decayLetterBuffer(data, 1);
            return;
        }

        int distinct = data.badPackets().distinctInteractEntitiesThisWindow();
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);

        if (distinct > 1) {
            int buf = incrementLetterBuffer(p, data, 1, bufferToFlag, "multiTarget distinct=" + distinct);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.3D),
                        "multiTarget distinct=" + distinct);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.25D);
        }
    }
}
