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

        resolvePending(p, data, now, false);

        long attackMs = data.getLastUseEntityTime();
        if (hasMatchingSwing(data, attackMs, now)) {
            markClean(p, data);
            return;
        }

        data.badPackets().setPendingNoSwingAttack(attackMs, data.getLastPacketInteractEntityId());
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null) return;
        long pending = data.badPackets().pendingNoSwingAttackMs();
        if (pending <= 0L) return;

        long now = System.currentTimeMillis();
        long postWindow = plugin.tierCfg().checkLong(name(), "postAttackSwingMs", 180L);
        if (now >= pending && now - pending <= postWindow) {
            data.badPackets().clearPendingNoSwingAttack();
            markClean(p, data);
        }
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null || data.isTeleportExempt()) return;
        resolvePending(p, data, nowMs, true);
    }

    private void resolvePending(Player p, PlayerData data, long now, boolean forceExpired) {
        long pending = data.badPackets().pendingNoSwingAttackMs();
        if (pending <= 0L) return;

        if (hasMatchingSwing(data, pending, now)) {
            data.badPackets().clearPendingNoSwingAttack();
            markClean(p, data);
            return;
        }

        long minResolveMs = forceExpired
                ? plugin.tierCfg().checkLong(name(), "postAttackSwingMs", 180L)
                : plugin.tierCfg().checkLong(name(), "minPendingResolveMs", 45L);
        if (now - pending < minResolveMs) {
            return;
        }

        data.badPackets().clearPendingNoSwingAttack();
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 6);
        int buf = incrementLetterBuffer(p, data, 1, bufferToFlag,
                "no-swing unresolved age=" + (now - pending) + "ms");

        if (buf >= bufferToFlag && plugin.tierCfg().checkBoolean(name(), "cancelNoSwingOnFlag", false)) {
            blockAttack(p, data, "no-swing unresolved buf=" + buf);
        }

        if (buf >= bufferToFlag) {
            flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.2D),
                    "attack-no-swing unresolved age=" + (now - pending) + "ms");
        }
    }

    private boolean hasMatchingSwing(PlayerData data, long attackMs, long now) {
        long lastSwing = data.getLastArmSwingPacket();
        if (lastSwing <= 0L || attackMs <= 0L) return false;

        long preWindow = plugin.tierCfg().checkLong(name(), "lenientSwingDeltaMs", 220L);
        long postWindow = plugin.tierCfg().checkLong(name(), "postAttackSwingMs", 180L);
        if (lastSwing <= attackMs && attackMs - lastSwing <= preWindow) {
            return true;
        }
        return lastSwing > attackMs && lastSwing - attackMs <= postWindow && lastSwing <= now;
    }

    private void markClean(Player p, PlayerData data) {
        decayLetterBuffer(data, 1);
        decay(p, 0.35D);
    }
}
