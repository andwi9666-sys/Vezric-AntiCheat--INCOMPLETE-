package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Central movement enforcement: packet cancel + immediate client snap + server teleport sync.
 */
public final class MovementEnforcement {

    /** Per-player setback circuit breaker. Lazily configured from config on first use. */
    private static volatile SetbackRateLimiter rateLimiter;
    private static volatile int rlCapacity = -1;
    private static volatile long rlWindowMs = -1L;

    private MovementEnforcement() {}

    private static SetbackRateLimiter rateLimiter(VezAntiCheat plugin) {
        int cap = plugin.getConfig().getInt("setback-blocker.circuit-breaker.max-setbacks", 3);
        long window = plugin.getConfig().getLong("setback-blocker.circuit-breaker.window-ms", 2000L);
        SetbackRateLimiter limiter = rateLimiter;
        if (limiter == null || rlCapacity != cap || rlWindowMs != window) {
            synchronized (MovementEnforcement.class) {
                if (rateLimiter == null || rlCapacity != cap || rlWindowMs != window) {
                    rateLimiter = new SetbackRateLimiter(cap, window);
                    rlCapacity = cap;
                    rlWindowMs = window;
                }
                limiter = rateLimiter;
            }
        }
        return limiter;
    }

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
        return executeSetback(plugin, player, data, reason, null);
    }

    /**
     * GrimAC-faithful movement setback. Teleports to the last-valid-ground anchor and, when
     * {@code prediction.setback.preserve-velocity} is on, re-sends the carried/knockback velocity as an
     * EntityVelocity packet so a setback never eats knockback (Grim {@code SetBackData.velocity}). When
     * {@code prediction.setback.simulate} is on, the anchor is advanced by one collided+frictioned tick of
     * that velocity so the resync lands where the impulse carries the player.
     *
     * @param carriedOverride explicit velocity to preserve (e.g. the expected knockback for anti-KB
     *                        correction); {@code null} derives it from the last-known-good velocity plus
     *                        any fresh pending knockback/explosion.
     */
    public static boolean executeSetback(VezAntiCheat plugin, Player player, PlayerData data, String reason,
                                         Vector carriedOverride) {
        if (plugin == null || player == null || data == null) return false;
        if (!plugin.getConfig().getBoolean("prediction.setback.enabled", true)) return false;
        // SAFETY KILL-SWITCH: movement/velocity setback TELEPORTS are disabled by default. The prediction
        // engine still detects and alerts (VL accrues), it just will not teleport the player. Re-enable with
        // prediction.setback.enforce: true once the underlying movement prediction is verified false-positive
        // free on the live server. (Key is absent from existing configs, so the safe default applies.)
        if (!plugin.getConfig().getBoolean("prediction.setback.enforce", false)) return false;
        if (PlayerData.bypass(player)) return false;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return false;
        }
        // Don't stack setbacks: if one is already pending (the client hasn't confirmed the teleport yet) or
        // we're inside the teleport-exempt window, skip. Re-firing on the in-flight movement packets that
        // arrive before the client processes the S08 is exactly what created the setback loop / desync.
        if (data.isPendingSetback() || data.isTeleportExempt()) return false;

        Location target = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        if (target == null || target.getWorld() == null) {
            if (data.getPredictionState() != null) {
                target = data.getPredictionState().getLastKnownGoodLocation();
            }
            if ((target == null || target.getWorld() == null) && data.getLastMoveFrom() != null) {
                target = data.getLastMoveFrom();
            }
        }
        if (target == null || target.getWorld() == null) {
            Location freeze = data.getLastLoc() != null ? data.getLastLoc() : player.getLocation();
            if (freeze != null && freeze.getWorld() != null) {
                sendImmediatePositionPacket(player, freeze);
                SetbackBlocker.noteServerSetback(data, freeze);
                blockCurrentMovementPacket(data, reason + " no-valid-anchor-freeze");
                if (plugin.diagnostics() != null) {
                    plugin.diagnostics().record(player.getUniqueId(), "MovementEnforcement",
                            "setback-freeze-no-target", reason);
                }
                return false;
            }
            if (plugin.diagnostics() != null) {
                plugin.diagnostics().record(player.getUniqueId(), "MovementEnforcement",
                        "setback-skip-no-target", reason);
            }
            return false;
        }

        Location setback = target.clone();
        setback.setWorld(player.getWorld());
        // Use the client's last-sent look (thread-safe, current) so the S08 does not snap the head to a
        // stale orientation from player.getLocation() on the packet thread.
        setback.setYaw(data.getPacketYaw());
        setback.setPitch(data.getPacketPitch());

        // Circuit breaker: if the player has been set back too many times in a short window,
        // do NOT teleport again (which spirals into a setback loop). Instead hard-freeze them by
        // re-sending the last valid position and keeping the pending-setback block engaged until
        // the bucket refills.
        long now = System.currentTimeMillis();
        if (plugin.getConfig().getBoolean("setback-blocker.circuit-breaker.enabled", true)
                && !rateLimiter(plugin).tryAcquire(player.getUniqueId(), now)) {
            Location freeze = data.getPendingSetbackTarget();
            if (freeze == null || freeze.getWorld() == null) freeze = setback;
            sendImmediatePositionPacket(player, freeze);
            SetbackBlocker.noteServerSetback(data, freeze);
            long exemptHold = plugin.getConfig().getLong("prediction.setback.teleport-exempt-ms", 900L);
            data.markTeleportExempt(exemptHold);
            if (plugin.diagnostics() != null) {
                plugin.diagnostics().record(player.getUniqueId(), "MovementEnforcement",
                        "circuit-breaker-freeze", reason);
            }
            return false;
        }

        // GrimAC velocity preservation: build the velocity to carry through the setback (explicit override
        // for anti-KB, else last-known-good momentum + any fresh pending knockback/explosion).
        Vector setbackVel = buildSetbackVelocity(plugin, data, carriedOverride);

        // Simulating setback: advance the anchor by one collided + frictioned tick of that velocity so the
        // resync lands where the impulse carries the player rather than snapping to the raw anchor.
        if (setbackVel != null && plugin.getConfig().getBoolean("prediction.setback.simulate", true)) {
            setback = simulateSetbackPosition(data, setback, setbackVel);
        }

        // Exemption ordering: mark teleport exemption and engage the pending-setback block BEFORE
        // sending the S08 position packet. Otherwise in-flight movement packets that arrive between
        // the snap and the exemption mark re-flag and trigger another setback (loop).
        SetbackBlocker.noteServerSetback(data, setback);
        long exemptMs = plugin.getConfig().getLong("prediction.setback.teleport-exempt-ms", 900L);
        data.markTeleportExempt(exemptMs);

        sendImmediatePositionPacket(player, setback);
        syncServerPosition(plugin, player, data, setback);
        data.setEngineOffsetAdvantage(0.0D);

        // Re-send the carried velocity so knockback survives the teleport (Grim SetBackData.velocity), and
        // seed the engine's carried motion with it so the server predicts the knockback movement instead of
        // re-flagging it. A self-suppress window stops KnockbackHandler re-ingesting this packet, and we do
        // NOT refresh lastVelocityTime — doing so kept the impulse perpetually "fresh" and re-fed the loop.
        if (setbackVel != null && setbackVel.lengthSquared() > 1.0E-6D
                && plugin.getConfig().getBoolean("prediction.setback.preserve-velocity", true)) {
            long suppressMs = plugin.getConfig().getLong("engine.packet-knockback-self-suppress-ms", 120L);
            data.setSuppressVelocityCaptureUntilMs(System.currentTimeMillis() + suppressMs);
            sendVelocityPacket(player, setbackVel);
            data.getMovementState().carriedMotion = setbackVel.clone();
            data.markVelocityExempt(plugin.cfg().velocityExemptMs());
        }

        if (plugin.diagnostics() != null) {
            plugin.diagnostics().record(player.getUniqueId(), "MovementEnforcement", "setback", reason);
        }
        return true;
    }

    /**
     * Build the velocity to preserve across a setback. An explicit override (the expected knockback for
     * anti-KB correction) wins; otherwise start from the last-known-good post-tick momentum and fold in
     * fresh pending impulses — knockback REPLACES momentum, an explosion ADDS on top (mirrors Grim's
     * futureKb / futureExplosion handling). Returns {@code null} when velocity preservation is disabled.
     */
    private static Vector buildSetbackVelocity(VezAntiCheat plugin, PlayerData data, Vector carriedOverride) {
        if (!plugin.getConfig().getBoolean("prediction.setback.preserve-velocity", true)) return null;
        if (carriedOverride != null) return carriedOverride.clone();

        // Carry ONLY a genuine pending impulse (knockback / explosion) — NEVER the player's own walking
        // momentum. Carrying plain momentum and re-sending it as an EntityVelocity flung legit players
        // forward and fed a self-feedback loop. With no real impulse this returns null, so a normal
        // movement setback skips the simulate/velocity path and teleports straight to the raw anchor.
        long now = System.currentTimeMillis();
        Vector vel = null;

        long kbWindow = plugin.getConfig().getLong("movement-engine.velocity-window-ms", 450L);
        Vector kb = data.getLastVelocity();
        if (kb != null && data.getLastVelocityTime() > 0L && (now - data.getLastVelocityTime()) <= kbWindow
                && kb.lengthSquared() > 1.0E-6D) {
            vel = kb.clone();
        }
        long expWindow = plugin.getConfig().getLong("movement-engine.explosion-window-ms", 900L);
        Vector explosion = data.getLastExplosionVelocity();
        if (explosion != null && data.getLastDamageTime() > 0L
                && (now - data.getLastDamageTime()) <= expWindow && explosion.lengthSquared() > 1.0E-6D) {
            if (vel == null) vel = explosion.clone();
            else vel.add(explosion);
        }
        return vel;
    }

    /**
     * Advance the setback anchor by one collided tick of {@code vel} (so the resync accounts for the
     * impulse), and decay {@code vel} in place with end-of-tick friction so the re-sent velocity is what
     * the client should continue with — avoiding a double-counted tick.
     */
    private static Location simulateSetbackPosition(PlayerData data, Location anchor, Vector vel) {
        try {
            com.colin.vezanticheat.engine.CompensatedWorld world = data.getCompensatedWorld();
            boolean onGround = com.colin.vezanticheat.movement.CollisionResolver.isOnGround(
                    world, anchor.getX(), anchor.getY(), anchor.getZ());
            com.colin.vezanticheat.engine.Collisions.Result col =
                    com.colin.vezanticheat.movement.CollisionResolver.collide(world,
                            anchor.getX(), anchor.getY(), anchor.getZ(),
                            vel.getX(), vel.getY(), vel.getZ(), onGround);
            Location pos = anchor.clone();
            pos.add(col.movedX, col.movedY, col.movedZ);
            double slip = (onGround || col.onGround) ? (0.6D * 0.91D) : 0.91D;
            vel.setX(vel.getX() * slip);
            vel.setZ(vel.getZ() * slip);
            vel.setY((vel.getY() - 0.08D) * 0.98D);
            return pos;
        } catch (Throwable t) {
            return anchor;
        }
    }

    private static void sendVelocityPacket(Player player, Vector vel) {
        if (player == null || vel == null) return;
        try {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user == null) return;
            user.sendPacket(new WrapperPlayServerEntityVelocity(player.getEntityId(),
                    new Vector3d(vel.getX(), vel.getY(), vel.getZ())));
        } catch (Throwable ignored) {
        }
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

        snap.setYaw(data.getPacketYaw());
        snap.setPitch(data.getPacketPitch());

        // Exemption ordering: engage the pending-setback block and mark teleport exemption BEFORE
        // sending the S08 packet so in-flight movement packets do not re-flag and loop.
        SetbackBlocker.noteServerSetback(data, snap);
        data.markTeleportExempt(Math.max(0L, teleportExemptMs));

        sendImmediatePositionPacket(player, snap);
        syncPunitivePosition(plugin, player, data, snap);

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
