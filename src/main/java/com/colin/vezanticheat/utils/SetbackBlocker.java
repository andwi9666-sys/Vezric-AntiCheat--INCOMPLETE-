package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * SetbackBlocker — cancels movement/attack packets while a server setback is pending acceptance.
 * Grim port: client must accept S08 before sending new positions.
 */
public final class SetbackBlocker {

    private SetbackBlocker() {}

    public static boolean shouldBlockMovement(VezAntiCheat plugin, PlayerData data) {
        if (plugin == null || data == null) return false;
        if (!plugin.getConfig().getBoolean("setback-blocker.enabled", true)) return false;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            data.clearPendingSetback();
            return false;
        }
        if (!data.isPendingSetback()) return false;
        // Transaction-confirmed acceptance (GrimAC): once the client acks a transaction sent after the
        // setback teleport, the teleport has been processed — stop blocking. The position-distance check
        // in onAcceptedPosition and the max-pending timeout below remain as fallbacks.
        if (plugin.getConfig().getBoolean("prediction.setback.transaction-confirmed", true)
                && data.getTransactionState().getLastSentSequence() >= 0L) {
            long seq = data.getPendingSetbackTxSeq();
            if (seq >= 0L && data.getTransactionState().getLastAckedSequence() >= seq) {
                data.clearPendingSetback();
                return false;
            }
        }
        long breakerWindow = plugin.getConfig().getLong("setback-blocker.circuit-breaker.window-ms", 2000L);
        long maxMs = Math.max(breakerWindow,
                plugin.getConfig().getLong("setback-blocker.max-pending-ms", breakerWindow));
        if (data.getPendingSetbackSinceMs() > 0L
                && (System.currentTimeMillis() - data.getPendingSetbackSinceMs()) > maxMs) {
            data.clearPendingSetback();
            return false;
        }
        return true;
    }

    public static void noteServerSetback(PlayerData data, Location target) {
        if (data == null || target == null) return;
        data.setPendingSetback(true);
        data.setPendingSetbackSinceMs(System.currentTimeMillis());
        data.setPendingSetbackTarget(target.clone());
        // Anchor acceptance to the next transaction sent after this teleport (cleared once it acks).
        data.setPendingSetbackTxSeq(data.getTransactionState().getLastSentSequence() + 1L);
    }

    public static void onAcceptedPosition(Player player, PlayerData data, Location packetLoc) {
        if (data == null || !data.isPendingSetback() || packetLoc == null) return;
        Location target = data.getPendingSetbackTarget();
        if (target == null || target.getWorld() == null || packetLoc.getWorld() == null) return;
        if (!target.getWorld().equals(packetLoc.getWorld())) return;

        double dist = target.distance(packetLoc);
        if (dist <= 1.0D) {
            data.clearPendingSetback();
        }
    }

    public static boolean blockIfPending(VezAntiCheat plugin, Player player, PlayerData data, String reason) {
        if (!shouldBlockMovement(plugin, data)) return false;
        MovementEnforcement.blockCurrentMovementPacket(plugin, data, reason);
        return true;
    }
}
