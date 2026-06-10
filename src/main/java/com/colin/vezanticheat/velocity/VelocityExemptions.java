package com.colin.vezanticheat.velocity;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Central exemption gate for velocity knockback analysis.
 */
public final class VelocityExemptions {
    private VelocityExemptions() {}

    public static String evaluate(
            VezAntiCheat plugin,
            Player player,
            PlayerData data,
            VelocitySession session,
            long nowMs) {

        if (plugin == null || player == null || data == null || session == null || session.snapshot == null) {
            return "null-input";
        }

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return "gamemode";
        }
        if (PlayerData.bypass(player)) return "bypass";
        if (player.isInsideVehicle()) return "vehicle";
        if (player.getAllowFlight() && player.isFlying()) return "flying";

        if (data.isTeleportExempt()) return "teleport";
        if (session.snapshot.startLocation == null || session.snapshot.startLocation.getWorld() == null) {
            return "invalid-world";
        }

        double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0;
        double minTps = plugin.getConfig().getDouble("velocity-engine.leniency.min-tps", 18.5D);
        if (tps < minTps) return "low-tps";

        int ping = Math.max(0, PingUtil.getPing(player));
        int maxPing = plugin.getConfig().getInt("velocity-engine.leniency.max-ping", 250);
        if (ping > maxPing) return "high-ping";

        VelocitySnapshot snap = session.snapshot;
        if (snap.inLiquid) return "liquid";
        if (snap.inWeb) return "web";
        if (snap.restrictive && snap.expectedHorizontal < 0.12) return "restrictive-surface";

        long jumpWindow = plugin.getConfig().getLong("velocity-engine.jump-reset-window-ms", 220L);
        if (session.jumpNearVelocity || (nowMs - data.getLastJumpTime() <= jumpWindow)) {
            return "jump-reset";
        }

        long stackWindow = plugin.getConfig().getLong("velocity-engine.stack-window-ms", 120L);
        if (session.stackCount >= 2 && (nowMs - snap.timeMs) <= stackWindow) {
            return "stacked-velocity";
        }

        if (snap.horizontalBlocked && snap.verticalBlocked) return "blocked";
        if (snap.horizontalBlocked && snap.expectedHorizontal < 0.14) return "horizontal-blocked";
        if (snap.verticalBlocked && snap.expectedVertical < 0.14) return "vertical-blocked";

        return null;
    }

    public static boolean shouldReduceConfidence(VezAntiCheat plugin, Player player, PlayerData data, long nowMs) {
        if (plugin == null || player == null || data == null) return false;
        return LagProfileUtil.isVelocityCoverActive(plugin, player, data, nowMs);
    }
}
