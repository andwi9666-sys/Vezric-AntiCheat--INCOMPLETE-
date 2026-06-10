package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Central movement enforcement: packet cancel + immediate client snap + server teleport sync.
 */
public final class MovementEnforcement {

    private MovementEnforcement() {}

    public static void requestImmediateSetback(VezAntiCheat plugin, Player player, PlayerData data,
                                               String reason, long delayMs) {
        if (plugin == null || player == null || data == null) return;
        if (!plugin.getConfig().getBoolean("movement-enforcement.enabled", true)) return;
        if (PlayerData.bypass(player)) return;

        long delay = Math.max(0L, Math.min(5L, delayMs));
        Runnable run = new Runnable() {
            @Override
            public void run() {
                requestBlatantEnforcement(plugin, player, data, reason);
            }
        };
        if (delay <= 0L || Bukkit.isPrimaryThread()) {
            run.run();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, run, 1L);
        }
    }

    public static void requestBlatantEnforcement(VezAntiCheat plugin, Player player, PlayerData data, String reason) {
        if (plugin == null || player == null || data == null) return;
        if (!plugin.getConfig().getBoolean("movement-enforcement.enabled", true)) return;
        if (PlayerData.bypass(player)) return;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return;
        }

        data.setBlockCurrentMovementPacket(true);
        data.setBlockedMovementReason(reason);
        executeSetback(plugin, player, data, reason);
    }

    public static void blockCurrentMovementPacket(PlayerData data, String reason) {
        if (data == null) return;
        data.setBlockCurrentMovementPacket(true);
        data.setBlockedMovementReason(reason);
    }

    /** Suppresses packet cancel during legitimate fall descent (same gate as teleport setbacks). */
    public static void blockCurrentMovementPacket(VezAntiCheat plugin, PlayerData data, String reason) {
        if (data == null) return;
        if (plugin != null && FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return;
        }
        blockCurrentMovementPacket(data, reason);
    }

    public static boolean executeSetback(VezAntiCheat plugin, Player player, PlayerData data, String reason) {
        if (plugin == null || player == null || data == null) return false;
        if (!plugin.getConfig().getBoolean("prediction.setback.enabled", true)) return false;
        if (PlayerData.bypass(player)) return false;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return false;
        }

        Location target = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        if (target == null || target.getWorld() == null) return false;

        final Location setback = target.clone();
        setback.setWorld(player.getWorld());
        setback.setYaw(player.getLocation().getYaw());
        setback.setPitch(player.getLocation().getPitch());

        sendImmediatePositionPacket(player, setback);
        syncServerPosition(plugin, player, data, setback);
        data.setEngineOffsetAdvantage(0.0D);
        data.clearPendingSetback();

        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(player.getUniqueId(), "MovementEnforcement", "setback", reason);
        }
        SetbackBlocker.noteServerSetback(data, setback);
        long exemptMs = plugin.getConfig().getLong("prediction.setback.teleport-exempt-ms", 900L);
        data.markTeleportExempt(exemptMs);
        return true;
    }

    /**
     * Punitive combat setback: snap position without blocking movement packets or resetting
     * engine offset advantage (unlike {@link #executeSetback}).
     */
    public static boolean executePunitiveTeleport(VezAntiCheat plugin, Player player, PlayerData data,
                                                 Location snap, String reason, long teleportExemptMs) {
        if (plugin == null || player == null || data == null || snap == null) return false;
        if (PlayerData.bypass(player)) return false;
        if (snap.getWorld() == null && player.getWorld() != null) {
            snap.setWorld(player.getWorld());
        }
        if (snap.getWorld() == null) return false;

        snap.setYaw(player.getLocation().getYaw());
        snap.setPitch(player.getLocation().getPitch());

        sendImmediatePositionPacket(player, snap);
        syncPunitivePosition(plugin, player, data, snap);

        SetbackBlocker.noteServerSetback(data, snap);
        data.markTeleportExempt(Math.max(0L, teleportExemptMs));

        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(player.getUniqueId(), "CombatMitigation", "punitive-setback", reason);
        }
        return true;
    }

    /** Snap the client immediately with S08; do not wait for the next server tick. */
    public static void sendPositionPacket(Player player, Location loc) {
        sendImmediatePositionPacket(player, loc);
    }

    private static void sendImmediatePositionPacket(Player player, Location loc) {
        if (player == null || loc == null) return;
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) return;
            WrapperPlayServerPlayerPositionAndLook packet = new WrapperPlayServerPlayerPositionAndLook(
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(), (byte) 0, 0, false);
            user.sendPacket(packet);
        } catch (Throwable ignored) {
        }
    }

    private static void syncServerPosition(VezAntiCheat plugin, Player player, PlayerData data, Location setback) {
        data.setLastLoc(setback.clone());
        data.setLastMoveFrom(setback.clone());
        data.clearFallArc();
        data.resetEngineAirState();
        if (plugin.engine() != null) {
            plugin.engine().onTeleport(player, data, setback);
        }
        scheduleServerTeleport(plugin, player, setback);
    }

    private static void syncPunitivePosition(VezAntiCheat plugin, Player player, PlayerData data, Location snap) {
        data.setLastLoc(snap.clone());
        data.setLastMoveFrom(snap.clone());
        if (plugin.engine() != null) {
            plugin.engine().onTeleport(player, data, snap);
        }
        scheduleServerTeleport(plugin, player, snap);
    }

    private static void scheduleServerTeleport(VezAntiCheat plugin, Player player, Location target) {
        Runnable teleport = new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                try {
                    player.teleport(target);
                } catch (Throwable ignored) {
                }
            }
        };

        if (Bukkit.isPrimaryThread()) {
            teleport.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, teleport);
        }
    }
}
