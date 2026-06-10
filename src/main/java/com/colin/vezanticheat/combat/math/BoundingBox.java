package com.colin.vezanticheat.combat.math;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Axis-aligned bounding box for 1.8-style player combat hitboxes.
 */
public final class BoundingBox {

    public static final double PLAYER_WIDTH = 0.6D;
    public static final double PLAYER_HEIGHT = 1.8D;
    public static final double PLAYER_HALF_WIDTH = 0.3D;

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public BoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public BoundingBox expand(double amount) {
        return new BoundingBox(
                minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount
        );
    }

    public boolean contains(Vector point) {
        if (point == null) {
            return false;
        }
        return point.getX() >= minX && point.getX() <= maxX
                && point.getY() >= minY && point.getY() <= maxY
                && point.getZ() >= minZ && point.getZ() <= maxZ;
    }

    /**
     * Slab-method ray-AABB intersection. Origin inside the box counts as a hit.
     */
    public boolean intersectsRay(Vector origin, Vector direction, double maxDistance) {
        return rayTrace(origin, direction, maxDistance).isHit();
    }

    /**
     * Slab-method ray-AABB intersection with entry point. Origin inside the box counts as a hit at distance 0.
     */
    public RayTraceResult rayTrace(Vector origin, Vector direction, double maxDistance) {
        if (origin == null || direction == null || maxDistance < 0.0D) {
            return RayTraceResult.miss();
        }

        if (contains(origin)) {
            return RayTraceResult.hit(origin.clone(), 0.0D);
        }

        Vector dir = direction.clone();
        if (dir.lengthSquared() <= 1.0E-8) {
            return RayTraceResult.miss();
        }
        dir.normalize();

        double tMin = 0.0D;
        double tMax = maxDistance;

        if (Math.abs(dir.getX()) < 1.0E-8) {
            if (origin.getX() < minX || origin.getX() > maxX) {
                return RayTraceResult.miss();
            }
        } else {
            double t1 = (minX - origin.getX()) / dir.getX();
            double t2 = (maxX - origin.getX()) / dir.getX();
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return RayTraceResult.miss();
            }
        }

        if (Math.abs(dir.getY()) < 1.0E-8) {
            if (origin.getY() < minY || origin.getY() > maxY) {
                return RayTraceResult.miss();
            }
        } else {
            double t1 = (minY - origin.getY()) / dir.getY();
            double t2 = (maxY - origin.getY()) / dir.getY();
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return RayTraceResult.miss();
            }
        }

        if (Math.abs(dir.getZ()) < 1.0E-8) {
            if (origin.getZ() < minZ || origin.getZ() > maxZ) {
                return RayTraceResult.miss();
            }
        } else {
            double t1 = (minZ - origin.getZ()) / dir.getZ();
            double t2 = (maxZ - origin.getZ()) / dir.getZ();
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return RayTraceResult.miss();
            }
        }

        if (tMin < 0.0D || tMin > maxDistance) {
            return RayTraceResult.miss();
        }

        Vector hitPoint = origin.clone().add(dir.clone().multiply(tMin));
        return RayTraceResult.hit(hitPoint, tMin);
    }

    public double distanceTo(Vector point) {
        if (point == null) {
            return Double.MAX_VALUE;
        }
        return point.distance(closestPoint(point));
    }

    public Vector closestPoint(Vector point) {
        if (point == null) {
            return new Vector();
        }
        return new Vector(
                clamp(point.getX(), minX, maxX),
                clamp(point.getY(), minY, maxY),
                clamp(point.getZ(), minZ, maxZ)
        );
    }

    public static BoundingBox fromPlayer(Player player) {
        if (player == null) {
            return new BoundingBox(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return fromFeet(player.getLocation(), PLAYER_WIDTH, PLAYER_HEIGHT);
    }

    public static BoundingBox fromFeet(Location feet, double width, double height) {
        if (feet == null) {
            return new BoundingBox(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        double half = Math.max(0.1D, width / 2.0D);
        double bodyHeight = Math.max(0.1D, height);
        double x = feet.getX();
        double y = feet.getY();
        double z = feet.getZ();
        return new BoundingBox(
                x - half, y, z - half,
                x + half, y + bodyHeight, z + half
        );
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
