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
            // Two-strike rule: a single corrupt packet (proxy/network mangling) must not
            // flag alone. Repeats inside the window are no accident.
            int strikesToFlag = Math.max(1, plugin.tierCfg().checkInt(name(), "nanStrikesToFlag", 2));
            long windowMs = plugin.tierCfg().checkLong(name(), "nanStrikeWindowMs", 30000L);
            long now = System.currentTimeMillis();
            int strikes = (data.getLastNanRotationMs() > 0L && (now - data.getLastNanRotationMs()) <= windowMs)
                    ? data.getNanRotationStrikes() + 1
                    : 1;
            data.setNanRotationStrikes(strikes);
            data.setLastNanRotationMs(now);
            if (strikes >= strikesToFlag) {
                data.setNanRotationStrikes(0);
                fail(p, data, plugin.tierCfg().checkDouble(name(), "blatantFailVl", 1.8D),
                        "invalidRot nanOrInfinite yaw=" + yaw + " pitch=" + pitch + " strikes=" + strikes);
            } else {
                plugin.tierChecks().verboseToStaff(p.getName(), name(),
                        "nan/inf rotation strike " + strikes + "/" + strikesToFlag + " (no flag yet)");
            }
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
