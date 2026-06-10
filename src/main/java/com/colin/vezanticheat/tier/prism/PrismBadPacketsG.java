package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.BadPacketValidationUtil;
import org.bukkit.entity.Player;

/** Invalid hotbar slot detection (outside 0–8). */
public final class PrismBadPacketsG extends PrismBadPacketCheck {

    public PrismBadPacketsG(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsG", 'G');
    }

    @Override
    public void onHeldItemChange(Player p, PlayerData data, int slot) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;

        if (!BadPacketValidationUtil.isInvalidHotbarSlot(slot)) {
            decayLetterBuffer(data, 1);
            decay(p, 0.3D);
            return;
        }

        recordBlatant(data, 2, System.currentTimeMillis());
        flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 2.0D), "invalidSlot slot=" + slot);
    }
}
