package com.colin.vezanticheat.engine;

import org.bukkit.Material;

/**
 * BlockProvider — abstraction over the source of block data used during collision.
 *
 * Two implementations exist:
 *  - {@link CompensatedWorld}: a packet-synced block-change overlay on top of the live
 *    world. Block changes (BLOCK_CHANGE / MULTI_BLOCK_CHANGE) are applied with latency
 *    compensation so collision sees what the client saw, defeating ghost-block phase.
 *  - the live-world fallback inside CompensatedWorld for chunks/positions with no
 *    pending overlay.
 *
 * Keeping collision behind this interface lets the engine read blocks without caring
 * whether the data came from a packet overlay or the Bukkit world.
 */
public interface BlockProvider {

    Material getType(int x, int y, int z);

    byte getData(int x, int y, int z);

    boolean isChunkLoaded(int blockX, int blockZ);
}
