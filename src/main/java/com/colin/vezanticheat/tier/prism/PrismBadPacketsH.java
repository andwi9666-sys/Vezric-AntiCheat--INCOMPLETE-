package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import org.bukkit.entity.Player;

/** Hotbar slot change spam within a single flying window. */
public final class PrismBadPacketsH extends PrismBadPacketCheck {

    public PrismBadPacketsH(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsH", 'H');
    }

    @Override
    public void onHeldItemChange(Player p, PlayerData data, int slot) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;

        long now = System.currentTimeMillis();
        if (LagProfileUtil.activeScore(plugin, p, data, now)
                >= plugin.tierCfg().checkDouble(name(), "lagCoverScore", 0.9D)) {
            decayLetterBuffer(data, 1);
            return;
        }

        int count = data.badPackets().slotChangeCountThisWindow();
        int maxChanges = plugin.tierCfg().checkInt(name(), "maxSlotChanges", 6);
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 3);

        if (count > maxChanges) {
            int buf = incrementLetterBuffer(p, data, 1, bufferToFlag, "slotSpam count=" + count);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.1D),
                        "slotSpam count=" + count);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.2D);
        }
    }
}
