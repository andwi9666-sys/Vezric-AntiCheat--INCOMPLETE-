package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.velocity.PredictedTick;
import com.colin.vezanticheat.velocity.VelocitySession;
import org.bukkit.entity.Player;

/**
 * Derives post-knockback horizontal speed allowance from the velocity prediction envelope
 * instead of a flat bonus + fixed grace window.
 */
public final class KbSpeedAllowance {

    private KbSpeedAllowance() {}

    public static double horizontalAllowance(VezAntiCheat plugin, Player player, PlayerData data, long nowMs) {
        if (plugin == null || player == null || data == null || plugin.velocity() == null) {
            return 0.0D;
        }
        VelocitySession session = plugin.velocity().getSession(player.getUniqueId());
        return horizontalAllowanceFromSession(session, player.isSprinting(), nowMs);
    }

    static double horizontalAllowanceFromSession(VelocitySession session, boolean sprinting, long nowMs) {
        if (session == null || session.evaluated || session.snapshot == null) {
            return 0.0D;
        }
        long elapsed = Math.max(0L, nowMs - session.snapshot.timeMs);
        if (elapsed > session.windowMs) {
            return 0.0D;
        }

        int tickIdx = (int) Math.min(session.predictedTicks.size() - 1, Math.max(0, elapsed / 50L));
        double envelopeH = envelopeHorizontalSpan(session, tickIdx);
        envelopeH = Math.max(envelopeH, session.maxHorizontal);

        double normalCap = sprinting ? 0.30D : 0.23D;
        if (envelopeH <= normalCap + 0.02D) {
            return 0.0D;
        }
        return Math.min(0.48D, (envelopeH - normalCap) + 0.05D);
    }

    public static boolean isKbEnvelopeActive(VezAntiCheat plugin, Player player, PlayerData data, long nowMs) {
        return horizontalAllowance(plugin, player, data, nowMs) > 0.0D;
    }

    private static double envelopeHorizontalSpan(VelocitySession session, int tickIdx) {
        double max = 0.0D;
        for (int i = 0; i <= tickIdx && i < session.predictedTicks.size(); i++) {
            PredictedTick tick = session.predictedTicks.get(i);
            if (tick == null) continue;
            double spanX = Math.max(0.0D, tick.maxX - tick.minX);
            double spanZ = Math.max(0.0D, tick.maxZ - tick.minZ);
            max = Math.max(max, Math.hypot(spanX, spanZ) * 0.5D);
        }
        return max;
    }
}
