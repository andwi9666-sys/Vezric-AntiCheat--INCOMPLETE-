package com.colin.vezanticheat.engine;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ConcurrentHashMap;

/**
 * CompensatedWorld — per-player packet-synced block view.
 *
 * GrimAC keeps a full per-player chunk cache deserialized from chunk packets so collision
 * sees exactly what the client saw, immune to ghost blocks and async desync. Reproducing a
 * complete 1.8 chunk-format deserializer is large and high-risk, so this implementation uses
 * the feasible subset that matters most for movement integrity on 1.8.8:
 *
 *  - A block-change OVERLAY fed by BLOCK_CHANGE / MULTI_BLOCK_CHANGE packets (via
 *    {@link com.colin.vezanticheat.engine.PacketWorldReader}). When the server changes a block,
 *    the overlay records the new state so collision reflects it the moment the client is told.
 *  - A live-world FALLBACK for any position with no overlay entry. This keeps behaviour never
 *    worse than the previous engine (which read the live world directly) while the overlay
 *    captures the latency-sensitive ghost-block cases.
 *
 * Entries expire after a short window so the overlay never drifts from the live world.
 */
public final class CompensatedWorld implements BlockProvider {

    private static final long ENTRY_TTL_MS = 6000L;

    private final ConcurrentHashMap<Long, Entry> overlay = new ConcurrentHashMap<Long, Entry>();
    private volatile World world;

    public void setWorld(World world) {
        this.world = world;
    }

    public World getWorld() {
        return world;
    }

    public void updateBlock(int x, int y, int z, Material material, byte data) {
        if (material == null) return;
        overlay.put(key(x, y, z), new Entry(material, data, System.currentTimeMillis()));
    }

    public void clear() {
        overlay.clear();
    }

    public void pruneExpired(long nowMs) {
        if (overlay.isEmpty()) return;
        for (java.util.Map.Entry<Long, Entry> e : overlay.entrySet()) {
            if (nowMs - e.getValue().timeMs > ENTRY_TTL_MS) {
                overlay.remove(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public Material getType(int x, int y, int z) {
        Entry e = overlay.get(key(x, y, z));
        if (e != null && (System.currentTimeMillis() - e.timeMs) <= ENTRY_TTL_MS) {
            return e.material;
        }
        World w = world;
        if (w == null) return Material.AIR;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return Material.AIR;
        try {
            Block b = w.getBlockAt(x, y, z);
            return b == null ? Material.AIR : b.getType();
        } catch (Throwable t) {
            return Material.AIR;
        }
    }

    @Override
    public byte getData(int x, int y, int z) {
        Entry e = overlay.get(key(x, y, z));
        if (e != null && (System.currentTimeMillis() - e.timeMs) <= ENTRY_TTL_MS) {
            return e.data;
        }
        World w = world;
        if (w == null) return 0;
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return 0;
        try {
            Block b = w.getBlockAt(x, y, z);
            return b == null ? 0 : b.getData();
        } catch (Throwable t) {
            return 0;
        }
    }

    @Override
    public boolean isChunkLoaded(int blockX, int blockZ) {
        World w = world;
        if (w == null) return false;
        try {
            return w.isChunkLoaded(blockX >> 4, blockZ >> 4);
        } catch (Throwable t) {
            return false;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static final class Entry {
        final Material material;
        final byte data;
        final long timeMs;

        Entry(Material material, byte data, long timeMs) {
            this.material = material;
            this.data = data;
            this.timeMs = timeMs;
        }
    }
}
