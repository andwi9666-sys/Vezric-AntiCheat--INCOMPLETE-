package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.BadPacketValidationUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Out-of-world bounds position detection. */
public final class PrismBadPacketsE extends PrismBadPacketCheck {

    public PrismBadPacketsE(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsE", 'E');
    }

    @Override
    public void onMove(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        Location loc = data.getLastLoc();
        if (loc == null) return;

        double minY = plugin.tierCfg().checkDouble(name(), "minY", -64.0D);
        double maxY = plugin.tierCfg().checkDouble(name(), "maxY", 320.0D);
        double maxHorizontal = plugin.tierCfg().checkDouble(name(), "maxHorizontal", 64.0D);
        if (!BadPacketValidationUtil.isOutOfWorldBounds(loc, minY, maxY, maxHorizontal)) {
            decayLetterBuffer(data, 1);
            decay(p, 0.3D);
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);
        int buf = incrementLetterBuffer(p, data, 2, bufferToFlag, "outOfBounds y=" + r(loc.getY()));

        double blatantYOffset = plugin.tierCfg().checkDouble(name(), "blatantYOffset", 32.0D);
        boolean blatant = loc.getY() < minY - blatantYOffset || loc.getY() > maxY + blatantYOffset;
        if (blatant) {
            long now = System.currentTimeMillis();
            blockMovement(data, "outOfBounds blatant y=" + r(loc.getY()));
            recordBlatant(data, 2, now);
        }

        if (buf >= bufferToFlag) {
            flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.6D),
                    "outOfBounds x=" + r(loc.getX()) + " y=" + r(loc.getY()) + " z=" + r(loc.getZ()));
        }
    }
}
