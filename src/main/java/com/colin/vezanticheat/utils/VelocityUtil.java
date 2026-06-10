package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public final class VelocityUtil {
    private VelocityUtil() {}

    public static double horizontal(Vector vector) {
        if (vector == null) return 0.0;
        return Math.hypot(vector.getX(), vector.getZ());
    }

    public static Vector horizontalDirection(Vector vector) {
        if (vector == null) return null;

        double h = horizontal(vector);
        if (h <= 1.0E-6) return null;

        return new Vector(vector.getX() / h, 0.0, vector.getZ() / h);
    }

    public static double horizontalDistance(Location from, Location to) {
        if (from == null || to == null) return 0.0;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.hypot(dx, dz);
    }

    public static double projectedHorizontal(Location from, Location to, Vector dir) {
        if (from == null || to == null || dir == null) return 0.0;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (dx * dir.getX()) + (dz * dir.getZ());
    }

    public static boolean isRestrictive(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Material at = loc.getBlock().getType();
        Material below = loc.clone().subtract(0.0, 1.0, 0.0).getBlock().getType();

        return isRestrictiveMaterial(at) || isRestrictiveMaterial(below);
    }

    public static boolean hasHorizontalBlock(Location loc, Vector dir, double distance) {
        if (loc == null || loc.getWorld() == null || dir == null) return false;

        Vector flatDir = dir.clone().setY(0.0);
        if (flatDir.lengthSquared() <= 1.0E-6) return false;
        flatDir.normalize();

        Vector lateral = new Vector(-flatDir.getZ(), 0.0, flatDir.getX());
        double[] side = new double[] { -0.31, 0.0, 0.31 };
        double[] heights = new double[] { 0.1, 0.9, 1.5 };

        for (double traveled = 0.12; traveled <= Math.max(0.20, distance); traveled += 0.18) {
            for (double offset : side) {
                double baseX = loc.getX() + (flatDir.getX() * traveled) + (lateral.getX() * offset);
                double baseZ = loc.getZ() + (flatDir.getZ() * traveled) + (lateral.getZ() * offset);
                for (double height : heights) {
                    if (isSolid(loc.getWorld(), baseX, loc.getY() + height, baseZ)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean hasCeiling(Location loc, double distance) {
        if (loc == null || loc.getWorld() == null) return false;

        double[] side = new double[] { -0.31, 0.0, 0.31 };
        double startY = loc.getY() + 1.85;
        double endY = startY + Math.max(0.35, distance);

        for (double y = startY; y <= endY; y += 0.18) {
            for (double ox : side) {
                for (double oz : side) {
                    if (isSolid(loc.getWorld(), loc.getX() + ox, y, loc.getZ() + oz)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean isSolid(World world, double x, double y, double z) {
        Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (block == null) return false;

        Material material = block.getType();
        if (material == null) return false;
        if (material == Material.AIR) return false;
        if (material == Material.WATER || material == Material.STATIONARY_WATER) return false;
        if (material == Material.LAVA || material == Material.STATIONARY_LAVA) return false;

        return material.isSolid();
    }

    private static boolean isRestrictiveMaterial(Material material) {
        if (material == null) return false;

        if (material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA
                || material == Material.WEB || material == Material.SOUL_SAND
                || material == Material.LADDER || material == Material.VINE) {
            return true;
        }

        String name = material.name();
        return name.contains("STAIRS") || name.contains("STEP") || name.contains("SLAB");
    }
}
