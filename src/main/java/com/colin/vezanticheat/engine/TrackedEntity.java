package com.colin.vezanticheat.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * TrackedEntity — packet-synced position history of one entity as seen by one viewer.
 *
 * Positions are updated from server->client entity packets (spawn / relative-move / teleport) and
 * snapshotted each tick, tagged with the transaction sequence in flight at that moment. The rewind
 * layer reads {@link #snapshots} around the client's last acknowledged sequence to reconstruct the
 * positions the client could legitimately have been rendering when it attacked.
 *
 * Hitbox dimensions mirror {@code CombatUtil} (players 0.6 wide / 1.8 tall, 1.65 sneaking; other
 * living entities 0.7 / 1.8) so engine-backed reach stays consistent with the legacy path.
 */
public final class TrackedEntity {

    public static final class PositionSnapshot {
        public final long sequence;
        public final long timeMs;
        public final double x;
        public final double y;
        public final double z;
        public final double width;
        public final double height;

        PositionSnapshot(long sequence, long timeMs, double x, double y, double z, double width, double height) {
            this.sequence = sequence;
            this.timeMs = timeMs;
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }
    }

    private static final int MAX_SNAPSHOTS = 40;

    public final int entityId;
    public final boolean player;
    private final double width;

    private volatile double serverX;
    private volatile double serverY;
    private volatile double serverZ;
    private volatile boolean sneaking;

    private final Deque<PositionSnapshot> snapshots = new ArrayDeque<PositionSnapshot>();

    public TrackedEntity(int entityId, boolean player, double width, double x, double y, double z) {
        this.entityId = entityId;
        this.player = player;
        this.width = width;
        this.serverX = x;
        this.serverY = y;
        this.serverZ = z;
    }

    public synchronized void relativeMove(double dx, double dy, double dz) {
        serverX += dx;
        serverY += dy;
        serverZ += dz;
    }

    public synchronized void teleport(double x, double y, double z) {
        serverX = x;
        serverY = y;
        serverZ = z;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public double getWidth() {
        return width;
    }

    public double currentHeight() {
        if (player && sneaking) return 1.65D;
        return 1.80D;
    }

    public synchronized void snapshot(long sequence, long timeMs) {
        snapshots.addLast(new PositionSnapshot(sequence, timeMs, serverX, serverY, serverZ, width, currentHeight()));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.removeFirst();
        }
    }

    /** Snapshot of the current (latest) server-truth position, not yet tagged into history. */
    public synchronized PositionSnapshot latest() {
        return new PositionSnapshot(Long.MAX_VALUE, System.currentTimeMillis(), serverX, serverY, serverZ, width, currentHeight());
    }

    public synchronized java.util.List<PositionSnapshot> snapshotsCopy() {
        return new java.util.ArrayList<PositionSnapshot>(snapshots);
    }
}
