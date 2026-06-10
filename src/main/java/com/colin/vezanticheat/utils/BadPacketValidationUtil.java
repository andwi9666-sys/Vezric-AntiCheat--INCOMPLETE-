package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Pure validation helpers for BadPackets checks (no Bukkit bootstrap required in tests).
 */
public final class BadPacketValidationUtil {

    private BadPacketValidationUtil() {}

    public static boolean isInvalidFloat(float value) {
        return Float.isNaN(value) || Float.isInfinite(value);
    }

    public static boolean isInvalidDouble(double value) {
        return Double.isNaN(value) || Double.isInfinite(value);
    }

    /**
     * Invalid rotation = non-finite values or pitch outside vanilla bounds.
     * Yaw is cyclic; clients may send unwrapped values (e.g. 720°) while looking around.
     */
    public static boolean isInvalidRotation(float yaw, float pitch, double maxAbsPitch) {
        if (isInvalidFloat(yaw) || isInvalidFloat(pitch)) return true;
        return Math.abs(pitch) > maxAbsPitch;
    }

    public static boolean isInvalidHotbarSlot(int slot) {
        return slot < 0 || slot > 8;
    }

    public static boolean isInvalidBlockFaceId(int faceId) {
        return faceId < 0 || faceId > 5;
    }

    public static boolean isInvalidCursor(float x, float y, float z) {
        if (isInvalidFloat(x) || isInvalidFloat(y) || isInvalidFloat(z)) return true;
        return x < 0.0F || x > 1.0F || y < 0.0F || y > 1.0F || z < 0.0F || z > 1.0F;
    }

    public static boolean isOutOfWorldBounds(Location loc, double minY, double maxY, double maxHorizontal) {
        if (loc == null) return true;
        if (isInvalidDouble(loc.getX()) || isInvalidDouble(loc.getY()) || isInvalidDouble(loc.getZ())) return true;
        if (loc.getY() < minY || loc.getY() > maxY) return true;
        World world = loc.getWorld();
        if (world == null) return false;
        double border = world.getWorldBorder().getSize() * 0.5D + maxHorizontal;
        return Math.abs(loc.getX()) > border || Math.abs(loc.getZ()) > border;
    }

    public static double eyeToBlockDistance(Location eye, Location blockCenter) {
        if (eye == null || blockCenter == null) return Double.MAX_VALUE;
        if (eye.getWorld() == null || blockCenter.getWorld() == null) return Double.MAX_VALUE;
        if (!eye.getWorld().equals(blockCenter.getWorld())) return Double.MAX_VALUE;
        return eye.distance(blockCenter);
    }

    public static double maxReachWithPing(double baseReach, int ping, double pingFactor, double maxAllowance) {
        return baseReach + Math.min(maxAllowance, Math.max(0, ping) * pingFactor);
    }
}
