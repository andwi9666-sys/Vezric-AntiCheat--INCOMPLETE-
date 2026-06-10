package com.colin.vezanticheat.engine;

/**
 * SimpleCollisionBox — immutable axis-aligned bounding box with vanilla-style
 * per-axis collision resolution.
 *
 * This is the GrimAC-style AABB primitive used throughout the prediction engine.
 * The collide* methods reproduce net.minecraft.server AxisAlignedBB.calculateX/Y/Z
 * offset behaviour: given a desired movement on one axis, they clamp it so the
 * player box does not pass through this block box.
 */
public final class SimpleCollisionBox {

    public static final double COLLISION_EPSILON = 1.0E-7D;

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public SimpleCollisionBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static SimpleCollisionBox playerBox(double x, double y, double z) {
        return new SimpleCollisionBox(
                x - 0.30D, y, z - 0.30D,
                x + 0.30D, y + 1.80D, z + 0.30D);
    }

    public SimpleCollisionBox offset(double x, double y, double z) {
        return new SimpleCollisionBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    /** Expand in the direction of movement (vanilla addCoord). */
    public SimpleCollisionBox expandTowards(double x, double y, double z) {
        double nMinX = x < 0.0D ? minX + x : minX;
        double nMaxX = x > 0.0D ? maxX + x : maxX;
        double nMinY = y < 0.0D ? minY + y : minY;
        double nMaxY = y > 0.0D ? maxY + y : maxY;
        double nMinZ = z < 0.0D ? minZ + z : minZ;
        double nMaxZ = z > 0.0D ? maxZ + z : maxZ;
        return new SimpleCollisionBox(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
    }

    public SimpleCollisionBox grow(double amount) {
        return new SimpleCollisionBox(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    public boolean intersects(SimpleCollisionBox o) {
        return o.maxX > minX && o.minX < maxX
                && o.maxY > minY && o.minY < maxY
                && o.maxZ > minZ && o.minZ < maxZ;
    }

    public boolean isIntersected(SimpleCollisionBox o) {
        return intersects(o);
    }

    public double collideX(SimpleCollisionBox moving, double dx) {
        if (moving.maxY <= minY || moving.minY >= maxY || moving.maxZ <= minZ || moving.minZ >= maxZ) {
            return dx;
        }
        if (dx > 0.0D && moving.maxX <= minX) {
            double max = minX - moving.maxX - COLLISION_EPSILON;
            if (max < dx) dx = max;
        } else if (dx < 0.0D && moving.minX >= maxX) {
            double min = maxX - moving.minX + COLLISION_EPSILON;
            if (min > dx) dx = min;
        }
        return dx;
    }

    public double collideY(SimpleCollisionBox moving, double dy) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxZ <= minZ || moving.minZ >= maxZ) {
            return dy;
        }
        if (dy > 0.0D && moving.maxY <= minY) {
            double max = minY - moving.maxY - COLLISION_EPSILON;
            if (max < dy) dy = max;
        } else if (dy < 0.0D && moving.minY >= maxY) {
            double min = maxY - moving.minY + COLLISION_EPSILON;
            if (min > dy) dy = min;
        }
        return dy;
    }

    public double collideZ(SimpleCollisionBox moving, double dz) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxY <= minY || moving.minY >= maxY) {
            return dz;
        }
        if (dz > 0.0D && moving.maxZ <= minZ) {
            double max = minZ - moving.maxZ - COLLISION_EPSILON;
            if (max < dz) dz = max;
        } else if (dz < 0.0D && moving.minZ >= maxZ) {
            double min = maxZ - moving.minZ + COLLISION_EPSILON;
            if (min > dz) dz = min;
        }
        return dz;
    }

    public double centerX() { return (minX + maxX) * 0.5D; }
    public double centerZ() { return (minZ + maxZ) * 0.5D; }

    @Override
    public String toString() {
        return "AABB[" + minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ + "]";
    }
}
