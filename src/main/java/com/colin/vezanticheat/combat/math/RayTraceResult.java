package com.colin.vezanticheat.combat.math;

import org.bukkit.util.Vector;

/**
 * Result of a ray-AABB intersection test.
 */
public final class RayTraceResult {

    private final boolean hit;
    private final Vector hitPoint;
    private final double distance;

    private RayTraceResult(boolean hit, Vector hitPoint, double distance) {
        this.hit = hit;
        this.hitPoint = hitPoint;
        this.distance = distance;
    }

    public static RayTraceResult miss() {
        return new RayTraceResult(false, null, Double.MAX_VALUE);
    }

    public static RayTraceResult hit(Vector hitPoint, double distance) {
        return new RayTraceResult(true, hitPoint, distance);
    }

    public boolean isHit() {
        return hit;
    }

    public Vector getHitPoint() {
        return hitPoint;
    }

    public double getDistance() {
        return distance;
    }
}
