package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Self entity interaction detection (attacking own entity ID). */
public final class PrismBadPacketsK extends PrismBadPacketCheck {

    public PrismBadPacketsK(VezAntiCheat plugin) {
        super(plugin, "PrismBadPacketsK", 'K');
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {
        handleSelfTarget(p, data, entityId, attack);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;
        if (!data.wasLastUseEntityAttack()) return;
        if (data.isBlockCurrentAttackPacket()) return;
        handleSelfTarget(p, data, data.getLastPacketInteractEntityId(), true);
    }

    private void handleSelfTarget(Player p, PlayerData data, int entityId, boolean attack) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt()) return;
        if (!attack || entityId != p.getEntityId()) return;

        long now = System.currentTimeMillis();
        blockAttack(p, data, "selfInteract entityId=" + entityId);
        recordBlatant(data, 3, now);
        flagLetter(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 2.0D),
                "selfInteract entityId=" + entityId);
    }
}
