package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.BadPacketValidationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Invalid position packet detection (NaN/Infinity coordinates). */
public final class PrismBadPacketsD extends PrismBadPacketCheck {

    public PrismBadPacketsD(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsD", 'D');
    }

    @Override
    public void onMove(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;

        Location loc = data.getLastLoc();
        if (loc == null) return;

        boolean invalid = BadPacketValidationUtil.isInvalidDouble(loc.getX())
                || BadPacketValidationUtil.isInvalidDouble(loc.getY())
                || BadPacketValidationUtil.isInvalidDouble(loc.getZ());
        if (!invalid) {
            decayLetterBuffer(data, 1);
            decay(p, 0.3D);
            return;
        }

        long now = System.currentTimeMillis();
        blockMovement(data, "nanOrInf x=" + r(loc.getX()) + " y=" + r(loc.getY()) + " z=" + r(loc.getZ()));
        recordBlatant(data, 3, now);
        flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 2.0D),
                "nanOrInf x=" + r(loc.getX()) + " y=" + r(loc.getY()) + " z=" + r(loc.getZ()));
    }
}
