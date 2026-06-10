package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class NukerUtil {
    private NukerUtil() {}

    public static boolean isBed(Block block) {
        if (block == null) return false;
        String name = block.getType().name();
        return "BED_BLOCK".equals(name) || "BED".equals(name);
    }

    public static double distanceToBlock(Location eye, Block block) {
        if (eye == null || block == null) return 0.0;

        double bx = block.getX();
        double by = block.getY();
        double bz = block.getZ();

        double cx = clamp(eye.getX(), bx, bx + 1.0);
        double cy = clamp(eye.getY(), by, by + 1.0);
        double cz = clamp(eye.getZ(), bz, bz + 1.0);

        double dx = eye.getX() - cx;
        double dy = eye.getY() - cy;
        double dz = eye.getZ() - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean hasObstruction(Player player, Block target) {
        if (player == null || target == null) return false;

        Location eye = player.getEyeLocation();
        Location base = target.getLocation();
        if (eye.getWorld() == null || base.getWorld() == null || !eye.getWorld().equals(base.getWorld())) {
            return true;
        }

        for (Location point : visibleSamplePoints(target)) {
            if (!isRayObstructed(eye, point, target)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isFullyCovered(Block target) {
        if (target == null) return false;
        return solidCover(target.getRelative(1, 0, 0))
                && solidCover(target.getRelative(-1, 0, 0))
                && solidCover(target.getRelative(0, 1, 0))
                && solidCover(target.getRelative(0, -1, 0))
                && solidCover(target.getRelative(0, 0, 1))
                && solidCover(target.getRelative(0, 0, -1));
    }

    private static boolean isRayObstructed(Location eye, Location targetPoint, Block target) {
        Vector ray = targetPoint.toVector().subtract(eye.toVector());
        double distance = ray.length();
        if (distance <= 0.001) return false;

        Vector step = ray.normalize().multiply(0.16);
        Location sample = eye.clone();
        for (double traveled = 0.16; traveled < distance - 0.18; traveled += 0.16) {
            sample.add(step);
            Block hit = sample.getBlock();
            if (hit == null) continue;
            if (hit.getX() == target.getX() && hit.getY() == target.getY() && hit.getZ() == target.getZ()) {
                continue;
            }
            if (isBed(hit)) continue;

            Material type = hit.getType();
            if (type != null && type.isSolid()) {
                return true;
            }
        }
        return false;
    }

    private static List<Location> visibleSamplePoints(Block target) {
        Location base = target.getLocation();
        List<Location> points = new ArrayList<Location>(5);
        points.add(base.clone().add(0.5, 0.88, 0.5));
        points.add(base.clone().add(0.22, 0.88, 0.22));
        points.add(base.clone().add(0.78, 0.88, 0.22));
        points.add(base.clone().add(0.22, 0.88, 0.78));
        points.add(base.clone().add(0.78, 0.88, 0.78));
        return points;
    }

    private static boolean solidCover(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type != null && type != Material.AIR && type.isSolid() && !isBed(block);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
