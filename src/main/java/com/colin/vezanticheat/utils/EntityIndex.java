package com.colin.vezanticheat.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity lookup readable from Netty threads.
 *
 * Several detection paths need to resolve entities (attack targets by id, combat
 * targets by UUID, proximity for collision uncertainty). Iterating
 * {@code world.getEntities()} / {@code player.getNearbyEntities()} from a packet
 * thread races the main thread (CME risk) and costs O(world entities) per packet.
 *
 * This index is rebuilt once per tick on the main thread into fresh structures that
 * are published via volatile fields — readers always see a complete, consistent
 * snapshot, never a half-built one. Packet threads do O(1) map reads.
 *
 * Staleness bound: an entity spawned within the current tick may not be indexed yet,
 * in which case lookups return null/empty and checks skip — the lenient, FP-safe
 * direction.
 */
public final class EntityIndex {

    private static volatile EntityIndex active;

    private final Plugin plugin;
    private volatile Map<Integer, Entity> byId = Collections.emptyMap();
    private volatile Map<UUID, Entity> byUuid = Collections.emptyMap();
    private volatile List<Entity> all = Collections.emptyList();
    private BukkitTask task;

    public EntityIndex(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The running index, or null when the plugin is disabled. For static utils without a plugin handle. */
    public static EntityIndex active() {
        return active;
    }

    public void start() {
        stop();
        rebuild();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                rebuild();
            }
        }, 1L, 1L);
        active = this;
    }

    public void stop() {
        if (active == this) active = null;
        if (task != null) {
            task.cancel();
            task = null;
        }
        byId = Collections.emptyMap();
        byUuid = Collections.emptyMap();
        all = Collections.emptyList();
    }

    /** Safe from any thread. May return an entity that died this tick; callers already null/dead-check. */
    public Entity get(int entityId) {
        return byId.get(entityId);
    }

    /** Safe from any thread. */
    public Entity getByUuid(UUID uuid) {
        return uuid == null ? null : byUuid.get(uuid);
    }

    public int size() {
        return all.size();
    }

    /**
     * Entities within a box of ±range around the player, excluding the player —
     * same semantics as {@code player.getNearbyEntities(range, range, range)} but
     * safe off the main thread.
     */
    public List<Entity> nearby(Player player, double range) {
        if (player == null || range <= 0.0D) return Collections.emptyList();
        List<Entity> snapshot = all;
        if (snapshot.isEmpty()) return Collections.emptyList();
        Location loc = player.getLocation();
        World world = player.getWorld();
        List<Entity> out = new ArrayList<Entity>();
        for (Entity entity : snapshot) {
            if (entity == player) continue;
            if (entity.getWorld() != world) continue;
            Location el = entity.getLocation();
            if (Math.abs(el.getX() - loc.getX()) > range) continue;
            if (Math.abs(el.getY() - loc.getY()) > range) continue;
            if (Math.abs(el.getZ() - loc.getZ()) > range) continue;
            out.add(entity);
        }
        return out;
    }

    /** True when any other entity sits within ±range of the player. Safe from any thread. */
    public boolean anyOtherEntityNear(Player player, double range) {
        if (player == null || range <= 0.0D) return false;
        List<Entity> snapshot = all;
        if (snapshot.isEmpty()) return false;
        Location loc = player.getLocation();
        World world = player.getWorld();
        for (Entity entity : snapshot) {
            if (entity == player) continue;
            if (entity.getWorld() != world) continue;
            Location el = entity.getLocation();
            if (Math.abs(el.getX() - loc.getX()) > range) continue;
            if (Math.abs(el.getY() - loc.getY()) > range) continue;
            if (Math.abs(el.getZ() - loc.getZ()) > range) continue;
            return true;
        }
        return false;
    }

    private void rebuild() {
        Map<Integer, Entity> ids = new HashMap<Integer, Entity>();
        Map<UUID, Entity> uuids = new HashMap<UUID, Entity>();
        List<Entity> list = new ArrayList<Entity>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                ids.put(entity.getEntityId(), entity);
                uuids.put(entity.getUniqueId(), entity);
                list.add(entity);
            }
        }
        // Publish complete snapshots; the maps are never mutated after this point.
        byId = ids;
        byUuid = uuids;
        all = list;
    }
}
