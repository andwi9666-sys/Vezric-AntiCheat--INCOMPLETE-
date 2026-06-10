package com.colin.vezanticheat.listeners;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.combat.CombatPacketHandler;
import com.colin.vezanticheat.tier.TierCheckManager;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.data.PlayerDataManager;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.SetbackUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * PacketListener — Primary data intake for the anticheat.
 *
 * This class intercepts ALL client→server packets via PacketEvents (Netty pipeline injection).
 * It is the single source of truth for player state — Bukkit events are secondary and only
 * handle things packets don't cover (inventory, block place confirmation, damage).
 *
 * Packet Types Handled:
 * =====================
 * PLAYER_FLYING          — Ground state only (no position/rotation). Sent every tick.
 * PLAYER_POSITION        — X/Y/Z + ground state. Sent when player moves.
 * PLAYER_POSITION_AND_ROTATION — X/Y/Z + Yaw/Pitch + ground. Sent when player moves AND rotates.
 * PLAYER_ROTATION        — Yaw/Pitch + ground. Sent when player looks around without moving.
 * ANIMATION              — Arm swing. Used for no-swing detection (KillAuraE).
 * INTERACT_ENTITY        — Attack/interact with entity. Used for combat tierChecks.
 * PLAYER_DIGGING         — Block breaking start/stop. Used for FastBreak/Nuker.
 * PLAYER_BLOCK_PLACEMENT — Block place. Used for Scaffold tierChecks.
 *
 * Why Packets (not Bukkit Events):
 * ================================
 * Bukkit events fire AFTER the server processes the packet. By then:
 * - Position is already updated (can't compare before/after)
 * - Timing information is lost (can't measure packet intervals)
 * - Packet ordering is lost (can't detect no-swing or timer manipulation)
 * - Can't cancel packets before they affect game state
 *
 * Packet interception gives us raw client data at the earliest possible point,
 * before the server processes it, allowing prediction comparison and packet cancellation.
 *
 * Data Flow:
 * ==========
 * Packet arrives → onPacketReceive()
 *   → Update PlayerData (position, rotation, timing, intervals)
 *   → Run prediction/simulation (compare actual vs expected)
 *   → Dispatch to checks (onMove, onAttack, onRotation, onFlyingPacket)
 *   → If violation detected, optionally cancel the packet (event.setCancelled)
 */
public class PacketListener extends PacketListenerAbstract {

    private static final long EXCEPTION_LOG_INTERVAL_MS = 30_000L;

    private final VezAntiCheat plugin;
    private final PlayerDataManager data;
    private final TierCheckManager tierChecks;
    private final CombatPacketHandler combatHandler;
    private final ConcurrentHashMap<String, Long> lastListenerExceptionLogMs = new ConcurrentHashMap<>();

    public PacketListener(JavaPlugin plugin, PlayerDataManager data, TierCheckManager tierChecks) {
        this(plugin, data, tierChecks, PacketListenerPriority.NORMAL);
    }

    public PacketListener(JavaPlugin plugin, PlayerDataManager data, TierCheckManager tierChecks,
                          PacketListenerPriority priority) {
        super(priority == null ? PacketListenerPriority.NORMAL : priority);
        this.plugin = (VezAntiCheat) plugin;
        this.data = data;
        this.tierChecks = tierChecks;
        this.combatHandler = new CombatPacketHandler((VezAntiCheat) plugin, ((VezAntiCheat) plugin).combat());
    }

    /** Register this listener with the PacketEvents event manager. */
    public void hook() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    /** Unregister from PacketEvents (called on plugin disable). */
    public void unhook() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    /**
     * Called for every packet received from any client.
     * This runs on the Netty IO thread — must be fast and non-blocking.
     * Heavy computation (like simulation) is deferred to check dispatch.
     */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPlayer() == null) return;
        Player p = (Player) event.getPlayer();
        PlayerData d = data.get(p);
        if (d == null) return;
        long now = System.currentTimeMillis();

        // --- ARM_ANIMATION (swing) ---
        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            long lastSwing = d.getLastArmSwingPacket();
            d.getArmSwings().addLast(now);
            while (d.getArmSwings().size() > 50) d.getArmSwings().removeFirst();
            if (lastSwing > 0L) {
                long interval = now - lastSwing;
                if (interval > 0L && interval < 300L) {
                    d.getClickIntervals().addLast(interval);
                    while (d.getClickIntervals().size() > 50) d.getClickIntervals().removeFirst();
                }
            }
            d.setLastArmSwingPacket(now);
            d.badPackets().noteSwing();
            d.badPackets().clearPositionlessFlyingStreak();
            tierChecks.onArmSwing(p, d);
            return;
        }

        // --- Flying / Position / Look packets ---
        boolean isFlying = event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING;
        boolean isPosition = event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION;
        boolean isPositionLook = event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
        boolean isLook = event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION;

        if (isFlying || isPosition || isPositionLook || isLook) {
            if (p.getWorld() == null) {
                return;
            }

            boolean clientGround;
            Location packetLoc = null;
            boolean positionIncluded = isPosition || isPositionLook;

            if (isPosition) {
                WrapperPlayClientPlayerPosition wrapper = new WrapperPlayClientPlayerPosition(event);
                clientGround = wrapper.isOnGround();
                com.github.retrooper.packetevents.protocol.world.Location peLoc = wrapper.getLocation();
                Location last = d.getLastLoc() != null ? d.getLastLoc() : p.getLocation();
                packetLoc = new Location(p.getWorld(), peLoc.getX(), peLoc.getY(), peLoc.getZ(), last.getYaw(), last.getPitch());
            } else if (isPositionLook) {
                WrapperPlayClientPlayerPositionAndRotation wrapper = new WrapperPlayClientPlayerPositionAndRotation(event);
                clientGround = wrapper.isOnGround();
                com.github.retrooper.packetevents.protocol.world.Location peLoc = wrapper.getLocation();
                packetLoc = new Location(p.getWorld(), peLoc.getX(), peLoc.getY(), peLoc.getZ(), peLoc.getYaw(), peLoc.getPitch());
            } else if (isLook) {
                WrapperPlayClientPlayerRotation wrapper = new WrapperPlayClientPlayerRotation(event);
                clientGround = wrapper.isOnGround();
                Location last = d.getLastLoc() != null ? d.getLastLoc() : p.getLocation();
                packetLoc = new Location(p.getWorld(), last.getX(), last.getY(), last.getZ(), wrapper.getYaw(), wrapper.getPitch());
            } else {
                // Pure flying packet (no position, no rotation)
                WrapperPlayClientPlayerFlying wrapper = new WrapperPlayClientPlayerFlying(event);
                clientGround = wrapper.isOnGround();
                Location last = d.getLastLoc() != null ? d.getLastLoc() : p.getLocation();
                packetLoc = last.clone();
            }

            // Rotation handling
            if (isLook || isPositionLook) {
                float yaw = packetLoc.getYaw();
                float pitch = packetLoc.getPitch();
                d.recordRotationSample(yaw, pitch);
                d.setCameraRotation(yaw, pitch);
                d.setLastRotationPacket(now);
                tierChecks.onRotation(p, d, yaw, pitch);
            } else if (packetLoc != null) {
                d.setCameraRotation(packetLoc.getYaw(), packetLoc.getPitch());
            }

            // Client ground state
            d.setLastClientGround(clientGround, now);

            if (combatHandler != null && plugin.getConfig().getBoolean("combat-analyzer.enabled", true)) {
                combatHandler.onFlyingPacket(
                        p, d, packetLoc, positionIncluded, isLook || isPositionLook, clientGround, now);
            }

            // Flying interval tracking
            long previousFlying = d.getLastFlyingPacket();
            if (previousFlying > 0L) {
                long interval = Math.max(0L, now - previousFlying);
                d.setLastFlyingIntervalMs(interval);
                if (interval > 0L && interval < 250L) {
                    d.getFlyingIntervals().addLast(interval);
                    while (d.getFlyingIntervals().size() > 30) d.getFlyingIntervals().removeFirst();
                }
            } else {
                d.setLastFlyingIntervalMs(0L);
            }
            LagProfileUtil.handleFlyingInterval(plugin, p, d, now, d.getLastFlyingIntervalMs());
            com.colin.vezanticheat.utils.NoFallTracker.noteBlinkGap(plugin, p, d, now);
            // Persistent drift ledger: a movement gap debits a bounded amount but NEVER resets the
            // ledger, so a timer cheat cannot farm advantage then stall to wipe it.
            com.colin.vezanticheat.engine.PlayerClock.onMovementGap(plugin, d, d.getLastFlyingIntervalMs());
            com.colin.vezanticheat.engine.PlayerClock.onFlyingPacket(plugin, p, d, positionIncluded);
            com.colin.vezanticheat.utils.UseItemTracker.decayReleaseStreak(d, now);

            // Position tracking
            if (positionIncluded && packetLoc != null) {
                d.badPackets().notePositionPacket(now);
                // Bucket by the real server tick (from the 1-tick TransactionTracker task) rather than
                // wall-clock/50, which mis-buckets under lag and can split or merge a single tick.
                d.incrementPositionPacketsThisTick(
                        com.colin.vezanticheat.engine.TransactionTracker.currentServerTick());
                Location previous = d.getLastLoc();
                if (previous == null) previous = p.getLocation().clone();
                else previous = previous.clone();

                double dy = packetLoc.getY() - previous.getY();
                if (Math.abs(dy - 0.0625D) < 1.0E-4D || Math.abs(dy - 0.11D) < 1.0E-3D) {
                    d.setLastMicroYOffsetMs(now);
                }
                if (p.isOnGround() || (dy > 0.0 && previous.getBlockY() <= packetLoc.getBlockY())) {
                    if (dy > 0.10 && previous.getY() + 1.0E-4 < packetLoc.getY()) {
                        d.setLastJumpTime(now);
                    }
                }

                d.setLastMoveFrom(previous);
                d.setLastLoc(packetLoc.clone());
                d.setLastMoveMillis(now);
                d.setLastRotationPacket(now);
                com.colin.vezanticheat.utils.FallArcTracker.observe(plugin, p, d, previous, packetLoc, clientGround, now);
                d.recordPosition(packetLoc, p.isSneaking(), now);
                LagrangeUtil.observeCombatMovement(plugin, p, d, now);
                tierChecks.onMove(p, d);
            } else if (packetLoc != null) {
                if (shouldCountPositionlessStreak(d, p, isLook, isFlying, now)) {
                    d.badPackets().notePositionlessFlying();
                } else {
                    d.badPackets().clearPositionlessFlyingStreak();
                }
            }

            // Scaffold rotation sample
            if (d.isScaffoldRotationPending() && packetLoc != null) {
                float yawDelta = Math.abs(wrapAngleTo180(packetLoc.getYaw() - d.getLastBlockPlacePacketYaw()));
                float pitchDelta = Math.abs(packetLoc.getPitch() - d.getLastBlockPlacePacketPitch());
                d.completeScaffoldRotationSample(yawDelta, pitchDelta);
            }

            // Grim-style offset prediction engine (primary movement validator).
            safeStage("engine", () -> {
                if (plugin.engine() != null && plugin.engine().isEnabled()) {
                    plugin.engine().onMovement(p, d, d.getLastMoveFrom(), d.getLastLoc(), clientGround, positionIncluded, now);
                }
            });

            // Legacy heuristic/envelope processor (kept for compatibility / fallback).
            boolean skipLegacyMovement = plugin.engine() != null && plugin.engine().isEnabled()
                    && plugin.getConfig().getBoolean("engine.skip-legacy-movement-prediction", true);
            if (!skipLegacyMovement) {
                safeStage("prediction", () -> {
                    if (plugin.prediction() != null) {
                        plugin.prediction().handleMovement(p, d, clientGround, now, positionIncluded);
                    }
                });
            } else {
                safeStage("prediction-velocity", () -> {
                    if (plugin.prediction() != null) {
                        plugin.prediction().tickVelocitySession(p, d, now);
                    }
                });
            }

            if (positionIncluded && d.getLastLoc() != null) {
                SetbackUtil.recordMovementSample(plugin, d, d.getLastLoc(), now);
            }

            if (positionIncluded && com.colin.vezanticheat.utils.SetbackBlocker.shouldBlockMovement(plugin, d)) {
                com.colin.vezanticheat.utils.SetbackBlocker.blockIfPending(plugin, p, d, "pending-setback");
            }

            if (positionIncluded && d.isBlockCurrentMovementPacket()) {
                event.setCancelled(true);
                Location revert = d.getLastMoveFrom();
                if (revert != null) {
                    Location snapped = revert.clone();
                    if (packetLoc != null) {
                        snapped.setYaw(revert.getYaw());
                        snapped.setPitch(revert.getPitch());
                        d.setCameraRotation(revert.getYaw(), revert.getPitch());
                    }
                    d.setLastLoc(snapped);
                }
                d.clearCurrentMovementBlock();
                return;
            }

            tierChecks.onFlyingPacket(p, d, now);
            d.setLastFlyingPacket(now);
            d.resetScaffoldPacketWindow();
            return;
        }

        // --- USE_ENTITY (attack/interact) ---
        if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            d.clearCurrentAttackBlock();
            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            int entityId = wrapper.getEntityId();
            boolean attack = (wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK);

            Entity target = null;
            if (p.getWorld() != null) {
                for (Entity entity : p.getWorld().getEntities()) {
                    if (entity.getEntityId() == entityId) {
                        target = entity;
                        break;
                    }
                }
            }

            d.setLastPacketInteract(entityId, attack);
            if (attack) {
                d.badPackets().noteAttack(entityId);
            } else {
                d.badPackets().noteInteractAttempt(entityId);
            }

            double dist = 0.0;
            Location attackEye = com.colin.vezanticheat.utils.HitboxUtil.buildPacketSyncedEye(p, d);
            if (target != null) {
                dist = CombatUtil.distanceToHitbox(attackEye, target);
            }

            long swingDelta = d.getLastArmSwingPacket() <= 0L
                    ? Long.MAX_VALUE
                    : (now - d.getLastArmSwingPacket());

            d.setLastUseEntity(target, attack, dist, attackEye, swingDelta, now);
            if (attack) {
                com.colin.vezanticheat.utils.UseItemTracker.reconcileMeleeAttack(p, d, now);
            }
            tierChecks.onInteractEntity(p, d, entityId, attack, target);

            // Grim-style combat engine: lag-compensated reach against the packet-synced entity
            // position the client actually saw (transaction-bracketed), with legacy fallback.
            final Entity combatTarget = target;
            final int combatEntityId = entityId;
            if (attack && plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
                safeStage("combat-engine", () -> {
                    PlayerData targetData = (combatTarget instanceof Player)
                            ? plugin.data().get((Player) combatTarget) : null;
                    com.colin.vezanticheat.engine.CombatResult combat = com.colin.vezanticheat.engine.CombatRewind.compute(
                            plugin, p, d, attackEye, combatEntityId, combatTarget, targetData, now);
                    d.setLastCombatResult(combat);
                });
            } else {
                d.setLastCombatResult(null);
            }

            if (attack) {
                if (combatHandler != null && target instanceof Player
                        && plugin.isCombatAnalysisEnabled()) {
                    final Player combatTargetPlayer = (Player) target;
                    final boolean[] cancelCombat = {false};
                    safeStage("combat-analysis", () -> {
                        cancelCombat[0] = combatHandler.onAttack(p, d, combatTargetPlayer, now);
                    });
                    if (cancelCombat[0]) {
                        event.setCancelled(true);
                        d.clearCurrentAttackBlock();
                        return;
                    }
                }

                LagProfileUtil.handleAttack(plugin, p, d, now);
                if (target != null) {
                    d.captureAttackRayContext(p, target, attackEye, now);
                }
                tierChecks.onAttack(p, d);
                if (d.isBlockCurrentAttackPacket()) {
                    event.setCancelled(true);
                    d.clearCurrentAttackBlock();
                    return;
                }
            }
            d.clearCurrentAttackBlock();
            return;
        }

        // --- BLOCK_DIG ---
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            d.clearCurrentDigBlock();
            d.setLastBlockDig(now);
            WrapperPlayClientPlayerDigging wrapper = new WrapperPlayClientPlayerDigging(event);
            DiggingAction action = wrapper.getAction();
            com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType digType =
                    com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.NONE;
            if (action == DiggingAction.START_DIGGING) digType = com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.START;
            else if (action == DiggingAction.CANCELLED_DIGGING) digType = com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.ABORT;
            else if (action == DiggingAction.FINISHED_DIGGING) digType = com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.FINISH;
            else if (action == DiggingAction.DROP_ITEM) digType = com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.DROP;
            else if (action == DiggingAction.RELEASE_USE_ITEM) digType = com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType.RELEASE;
            d.badPackets().noteDigAction(digType);
            if (action == DiggingAction.RELEASE_USE_ITEM) {
                com.colin.vezanticheat.utils.UseItemTracker.noteReleaseUseItem(d, now);
            }

            Block block = null;
            com.github.retrooper.packetevents.util.Vector3i pos = wrapper.getBlockPosition();
            if (pos != null && p.getWorld() != null) {
                block = p.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            }

            tierChecks.onDigging(p, d, digType, block);
            if (d.isBlockCurrentDigPacket()) {
                event.setCancelled(true);
                d.clearCurrentDigBlock();
                return;
            }

            if (action == DiggingAction.START_DIGGING && block != null && block.getType() != Material.AIR) {
                d.recordDigStartSample(now);
                tierChecks.onDigStart(p, d, block);
                if (d.isBlockCurrentDigPacket()) {
                    event.setCancelled(true);
                    d.clearCurrentDigBlock();
                    return;
                }
            }
            return;
        }

        // --- BLOCK_PLACE ---
        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            d.clearCurrentPlaceBlock();
            WrapperPlayClientPlayerBlockPlacement wrapper = new WrapperPlayClientPlayerBlockPlacement(event);
            int faceId = wrapper.getFace().ordinal();
            float cursorX = Float.NaN;
            float cursorY = Float.NaN;
            float cursorZ = Float.NaN;
            Location againstLoc = null;
            Block againstBlock = null;

            com.github.retrooper.packetevents.util.Vector3i pos = wrapper.getBlockPosition();
            if (pos != null && p.getWorld() != null) {
                againstLoc = new Location(p.getWorld(), pos.getX(), pos.getY(), pos.getZ());
                againstBlock = p.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
            }

            try {
                com.github.retrooper.packetevents.util.Vector3f cursor = wrapper.getCursorPosition();
                if (cursor != null) {
                    cursorX = cursor.getX();
                    cursorY = cursor.getY();
                    cursorZ = cursor.getZ();
                }
            } catch (Exception ex) {
                // Cursor is optional on some protocol versions; never crash the Netty thread over it.
                logThrottled("block-place-cursor", ex);
            }

            Location packetLoc = d.getLastLoc() == null ? p.getLocation().clone() : d.getLastLoc().clone();
            d.setLastBlockPlacePacket(
                    againstLoc,
                    faceId,
                    cursorX,
                    cursorY,
                    cursorZ,
                    packetLoc.getYaw(),
                    packetLoc.getPitch(),
                    packetLoc,
                    now
            );
            com.colin.vezanticheat.utils.ScaffoldUtil.applyPacketPlaceContext(
                    plugin, p, d, againstLoc, faceId,
                    packetLoc.getYaw(), packetLoc.getPitch(), packetLoc, now
            );
            d.markBlockStateExempt(plugin.cfg().blockStateExemptMs());
            d.badPackets().notePlace();
            tierChecks.onBlockPlacePacket(p, d, againstBlock, faceId, cursorX, cursorY, cursorZ);
            if (d.isBlockCurrentPlacePacket()) {
                event.setCancelled(true);
                d.clearCurrentPlaceBlock();
                return;
            }

            // Detect sword-blocking or eating: face=255/-1 with position (-1,-1,-1) indicates item use
            if (faceId == 255 || faceId == -1 || (pos != null && pos.getX() == -1 && pos.getY() == -1 && pos.getZ() == -1)) {
                org.bukkit.inventory.ItemStack hand = p.getItemInHand();
                if (com.colin.vezanticheat.utils.ItemUseMovementUtil.isEdible(hand)) {
                    com.colin.vezanticheat.utils.ItemUseMovementUtil.beginEating(p, d, now);
                } else if (hand != null && hand.getType().name().endsWith("_SWORD")) {
                    d.setAutoBlockALastBlockStartMs(now);
                }
            }
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            WrapperPlayClientEntityAction wrapper = new WrapperPlayClientEntityAction(event);
            d.badPackets().noteEntityAction();
            tierChecks.onEntityAction(p, d, wrapper.getAction().name());
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange wrapper = new WrapperPlayClientHeldItemChange(event);
            int slot = wrapper.getSlot();
            d.badPackets().noteSlotChange();
            tierChecks.onHeldItemChange(p, d, slot);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            d.badPackets().noteUseItem();
            org.bukkit.inventory.ItemStack hand = p.getItemInHand();
            if (com.colin.vezanticheat.utils.ItemUseMovementUtil.isEdible(hand)) {
                com.colin.vezanticheat.utils.ItemUseMovementUtil.beginEating(p, d, now);
            } else {
                com.colin.vezanticheat.utils.UseItemTracker.noteUseItem(d, now);
            }
            tierChecks.onUseItem(p, d);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLOSE_WINDOW) {
            tierChecks.onCloseInventory(p, d);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
            d.clearCurrentWindowBlock();
            WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
            tierChecks.onWindowClick(p, d, wrapper.getWindowId(), wrapper.getSlot());
            if (d.isBlockCurrentWindowPacket()) {
                event.setCancelled(true);
                d.clearCurrentWindowBlock();
            }
            return;
        }
    }

    private void safeStage(String stage, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            logThrottled(stage, t);
        }
    }

    /**
     * Log a swallowed packet-thread exception at WARNING, throttled per stage so a recurring fault
     * cannot spam the console or stall the Netty thread. The catch is always kept — we never
     * propagate from the packet path.
     */
    private void logThrottled(String stage, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = lastListenerExceptionLogMs.get(stage);
        if (last != null && now - last < EXCEPTION_LOG_INTERVAL_MS) {
            return;
        }
        lastListenerExceptionLogMs.put(stage, now);
        plugin.getLogger().log(Level.WARNING,
                "PacketListener " + stage + " failed: "
                        + t.getClass().getSimpleName() + " - " + t.getMessage()
                        + " (further errors throttled for 30s)",
                t);
    }

    private float wrapAngleTo180(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    /**
     * Positionless streaks should only track suspicious movement without coordinates (e.g. blink in air).
     * Rotation-only and ground keepalive packets while stationary are normal client behavior.
     */
    private boolean shouldCountPositionlessStreak(PlayerData d, Player p, boolean isLook, boolean isFlying, long now) {
        if (isLook) return false;
        if (!isFlying) return false;
        if (p.isOnGround()) {
            long sinceMove = d.getLastMoveMillis() > 0L ? now - d.getLastMoveMillis() : Long.MAX_VALUE;
            return sinceMove < 100L;
        }
        return true;
    }
}
