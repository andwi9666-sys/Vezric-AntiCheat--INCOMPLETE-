package com.colin.vezanticheat.engine;

import java.util.concurrent.ConcurrentHashMap;

/**
 * CompensatedEntities — per-viewer map of {@link TrackedEntity}, fed by
 * {@link EntityTrackerListener} from server->client entity packets.
 *
 * This is the combat analogue of {@link CompensatedWorld}: instead of blocks, it tracks the
 * latency-compensated positions of every entity the viewer can see, so reach/hitbox checks can
 * validate against the exact position the client saw rather than the live Bukkit location.
 */
public final class CompensatedEntities {

    private final ConcurrentHashMap<Integer, TrackedEntity> entities = new ConcurrentHashMap<Integer, TrackedEntity>();

    public void addPlayer(int entityId, double x, double y, double z) {
        entities.put(entityId, new TrackedEntity(entityId, true, 0.6D, x, y, z));
    }

    public void addLiving(int entityId, double x, double y, double z) {
        entities.put(entityId, new TrackedEntity(entityId, false, 0.7D, x, y, z));
    }

    public void addGeneric(int entityId, double x, double y, double z) {
        entities.put(entityId, new TrackedEntity(entityId, false, 0.6D, x, y, z));
    }

    public void relativeMove(int entityId, double dx, double dy, double dz) {
        TrackedEntity e = entities.get(entityId);
        if (e != null) e.relativeMove(dx, dy, dz);
    }

    public void teleport(int entityId, double x, double y, double z) {
        TrackedEntity e = entities.get(entityId);
        if (e != null) e.teleport(x, y, z);
    }

    public void setSneaking(int entityId, boolean sneaking) {
        TrackedEntity e = entities.get(entityId);
        if (e != null) e.setSneaking(sneaking);
    }

    public void remove(int entityId) {
        entities.remove(entityId);
    }

    public TrackedEntity get(int entityId) {
        return entities.get(entityId);
    }

    public int size() {
        return entities.size();
    }

    public void snapshotAll(long sequence, long timeMs) {
        for (TrackedEntity e : entities.values()) {
            e.snapshot(sequence, timeMs);
        }
    }

    public void clear() {
        entities.clear();
    }
}
