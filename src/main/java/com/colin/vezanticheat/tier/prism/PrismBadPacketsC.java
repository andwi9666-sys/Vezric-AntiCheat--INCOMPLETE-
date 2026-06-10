package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Player;

/** 1.8 attack without preceding arm swing (no-swing killaura). */
public final class PrismBadPacketsC extends PrismBadPacketCheck {

    public PrismBadPacketsC(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsC", 'C');
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || data.isTeleportExempt()) return;
        if (!data.wasLastUseEntityAttack()) return;

        long now = System.currentTimeMillis();
        long freshness = plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L);
        if (now - data.getLastUseEntityTime() > freshness) return;

        long swingDelta = data.getLastAttackSwingDeltaMs();
        long hardThreshold = plugin.tierCfg().checkLong(name(), "hardSwingDeltaMs", 120L);
        long lenientThreshold = plugin.tierCfg().checkLong(name(), "lenientSwingDeltaMs", 220L);
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);

        long lastSwing = data.getLastArmSwingPacket();
        if (lastSwing > 0L && now - lastSwing <= lenientThreshold) {
            decayLetterBuffer(data, 1);
            decay(p, 0.35D);
            return;
        }

        if (swingDelta != Long.MAX_VALUE && swingDelta <= hardThreshold) {
            decayLetterBuffer(data, 1);
            decay(p, 0.35D);
            return;
        }

        long combatMs = plugin.getConfig().getLong("engine.combat-movement-grace-ms", 450L);
        if (data.getLastUseEntityTime() > 0L && (now - data.getLastUseEntityTime()) <= combatMs
                && swingDelta != Long.MAX_VALUE && swingDelta <= lenientThreshold) {
            decayLetterBuffer(data, 1);
            decay(p, 0.35D);
            return;
        }

        int gain = swingDelta == Long.MAX_VALUE ? 2 : 1;
        int buf = incrementLetterBuffer(p, data, gain, bufferToFlag,
                "no-swing delta=" + (swingDelta == Long.MAX_VALUE ? "NEVER" : swingDelta + "ms"));

        if (swingDelta == Long.MAX_VALUE && buf >= Math.max(3, bufferToFlag)) {
            blockAttack(p, data, "no-swing NEVER buf=" + buf);
        }

        if (buf >= bufferToFlag) {
            flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                    "attack-no-swing delta=" + (swingDelta == Long.MAX_VALUE ? "NEVER" : swingDelta + "ms"));
        }
    }
}
