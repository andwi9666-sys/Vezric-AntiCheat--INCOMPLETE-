package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Player;

/** Invalid rotation packet detection (NaN, out-of-bounds pitch/yaw). */
public final class PrismBadPacketsA extends PrismBadPacketCheck {

    public PrismBadPacketsA(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsA", 'A');
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        if (p == null || data == null || data.isTeleportExempt()) return;

        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "blatantFailVl", 1.8D),
                    "invalidRot nanOrInfinite yaw=" + yaw + " pitch=" + pitch);
            return;
        }

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);
        if (isInvalidRotation(yaw, pitch)) {
            int buf = incrementLetterBuffer(p, data, 2, bufferToFlag,
                    "invalidRot pitch=" + pitch + " yaw=" + yaw);
            if (buf >= bufferToFlag) {
                flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.4D),
                        "invalidRot pitch=" + pitch + " yaw=" + yaw);
            }
        } else {
            decayLetterBuffer(data, 1);
            decay(p, 0.4D);
        }
    }
}
