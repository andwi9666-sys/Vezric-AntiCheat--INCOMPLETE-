package com.colin.vezanticheat.utils;

import org.bukkit.util.Vector;

/**
 * Axis-aligned combat hitbox with vanilla per-axis expansion applied.
 */
public final class CombatAabb {

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public CombatAabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public Vector closestPoint(Vector point) {
        if (point == null) return new Vector();
        return new Vector(
                clamp(point.getX(), minX, maxX),
                clamp(point.getY(), minY, maxY),
                clamp(point.getZ(), minZ, maxZ)
        );
    }

    public double distanceTo(Vector point) {
        if (point == null) return Double.MAX_VALUE;
        return point.distance(closestPoint(point));
    }

    public double effectiveWidth() {
        return maxX - minX;
    }

    public double effectiveHeight() {
        return maxY - minY;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
