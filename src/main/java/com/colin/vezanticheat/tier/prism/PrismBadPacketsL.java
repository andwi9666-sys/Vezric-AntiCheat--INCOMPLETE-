package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.EntityIndex;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Attack with missing entity target (ghost attack). */
public final class PrismBadPacketsL extends PrismBadPacketCheck {

    public PrismBadPacketsL(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsL", 'L');
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;
        if (!attack) return;

        if (target != null) {
            decayLetterBuffer(data, 1);
            decay(p, 0.25D);
            return;
        }

        if (!canTrustMissingTarget(p)) {
            decayLetterBuffer(data, 1);
            decay(p, 0.25D);
            return;
        }

        // Require sustained ghost attacks; cache misses and just-spawned entities are not evidence.
        int bufferToFlag = Math.max(5, plugin.tierCfg().checkInt(name(), "bufferToFlag", 8));
        int buf = incrementLetterBuffer(p, data, 1, bufferToFlag, "ghostAttack entityId=" + entityId);
        if (buf >= bufferToFlag) {
            long now = System.currentTimeMillis();
            if (plugin.tierCfg().checkBoolean(name(), "cancelGhostAttackOnFlag", false)) {
                blockAttack(p, data, "ghostAttack entityId=" + entityId);
            }
            recordBlatant(data, 2, now);
            flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.4D),
                    "ghostAttack entityId=" + entityId);
        }
    }

    private boolean canTrustMissingTarget(Player p) {
        EntityIndex index = plugin.entityIndex();
        if (index == null) return false;

        int minIndexed = plugin.tierCfg().checkInt(name(), "minIndexedEntities", 2);
        if (index.size() < minIndexed) return false;

        double forgivenessRange = plugin.tierCfg().checkDouble(name(), "nearbyEntityForgivenessRange", 6.0D);
        return index.nearby(p, forgivenessRange).isEmpty();
    }
}
