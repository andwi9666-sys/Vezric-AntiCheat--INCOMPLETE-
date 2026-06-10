package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

/** Entity action packet spam within a single flying window. */
public final class PrismBadPacketsJ extends PrismBadPacketCheck {

    public PrismBadPacketsJ(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsJ", 'J');
    }

    @Override
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        long now = System.currentTimeMillis();
        if (LagProfileUtil.activeScore(plugin, p, data, now)
                >= plugin.tierCfg().checkDouble(name(), "lagCoverScore", 0.9D)) {
            decayLetterBuffer(data, 1);
            return;
        }

        int count = data.badPackets().entityActionCountThisWindow();
        int maxActions = plugin.tierCfg().checkInt(name(), "maxEntityActions", 8);
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 3);

        if (count > maxActions) {
            int buf = incrementLetterBuffer(p, data, 1, bufferToFlag, "entityActionSpam count=" + count);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                        "entityActionSpam count=" + count + " action=" + actionName);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.2D);
        }
    }
}
