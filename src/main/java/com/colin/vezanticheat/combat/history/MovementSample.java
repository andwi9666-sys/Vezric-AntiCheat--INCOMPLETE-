package com.colin.vezanticheat.combat.history;

import org.bukkit.Location;

/**
 * One movement tick in rolling combat history.
 */
public final class MovementSample {

    private final Location location;
    private final long timestamp;
    private final double deltaX;
    private final double deltaY;
    private final double deltaZ;
    private final boolean onGround;

    public MovementSample(Location location, long timestamp, double deltaX, double deltaY,
                          double deltaZ, boolean onGround) {
        this.location = location == null ? null : location.clone();
        this.timestamp = timestamp;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.deltaZ = deltaZ;
        this.onGround = onGround;
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getDeltaX() {
        return deltaX;
    }

    public double getDeltaY() {
        return deltaY;
    }

    public double getDeltaZ() {
        return deltaZ;
    }

    public boolean isOnGround() {
        return onGround;
    }
}
