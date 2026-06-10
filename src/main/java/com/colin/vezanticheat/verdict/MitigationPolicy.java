package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.FallArcTracker;
import com.colin.vezanticheat.utils.MovementEnforcement;

/**
 * Tier-aware mitigation: cancel attacks/movement and request setbacks.
 */
public final class MitigationPolicy {

    public void blockMovement(PlayerData data, String reason) {
        MovementEnforcement.blockCurrentMovementPacket(data, reason);
    }

    public void cancelAttack(PlayerData data, String reason) {
        if (data == null) return;
        data.setBlockCurrentAttackPacket(true);
        data.setBlockedAttackReason(reason);
    }

    public void maybeSetback(VezAntiCheat plugin, org.bukkit.entity.Player player, PlayerData data,
                             CheckTier tier, String checkName, String debug) {
        if (player == null || data == null || tier == null) return;
        if (tier == CheckTier.CHARACTERISTICS || tier == CheckTier.PRISM) return;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) return;

        double blatant = plugin.getConfig().getDouble("tier.setback-blatant-offset", 0.15D);
        com.colin.vezanticheat.engine.EngineResult result = data.getLastEngineResult();
        if (result != null && result.offset >= blatant) {
            MovementEnforcement.requestBlatantEnforcement(plugin, player, data, checkName + " " + debug);
        }
    }
}
