package com.colin.vezanticheat.combat.math;

import org.bukkit.util.Vector;

/**
 * Combat ray casting helpers.
 */
public final class RayTraceUtil {

    private RayTraceUtil() {}

    public static Vector getLookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vector(x, y, z).normalize();
    }

    public static RayTraceResult traceRay(Vector origin, Vector direction, BoundingBox box, double maxDistance) {
        if (origin == null || direction == null || box == null) {
            return RayTraceResult.miss();
        }
        Vector dir = direction.clone();
        if (dir.lengthSquared() <= 1.0E-8) {
            return RayTraceResult.miss();
        }
        dir.normalize();
        return box.rayTrace(origin, dir, maxDistance);
    }

    public static boolean rayIntersectsBox(Vector origin, Vector direction, BoundingBox box, double maxDistance) {
        return traceRay(origin, direction, box, maxDistance).isHit();
    }

    public static double distanceEyeToBox(Vector eye, BoundingBox box) {
        if (eye == null || box == null) {
            return Double.MAX_VALUE;
        }
        return box.distanceTo(eye);
    }
}
