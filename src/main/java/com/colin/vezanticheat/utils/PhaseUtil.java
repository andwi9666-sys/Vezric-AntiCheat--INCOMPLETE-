package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class PhaseUtil {
    private PhaseUtil() {}

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return null;
        if (from.getWorld() == null || to.getWorld() == null) return null;
        if (!from.getWorld().equals(to.getWorld())) return null;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double hDist = Math.hypot(dx, dz);

        long intervalMs = data.getLastFlyingIntervalMs();
        double ticks = Math.max(1.0, Math.min(4.0, intervalMs <= 0L ? 1.0 : intervalMs / 50.0));

        boolean inLiquid = isLiquid(to) || isLiquid(from);
        boolean climbable = isClimbable(to) || isClimbable(from);
        boolean inWeb = isWeb(to) || isWeb(from);
        boolean onSlime = isSlime(to) || isSlime(from);
        boolean weirdSurface = isWeirdSurface(to) || isWeirdSurface(from);

        int fromSolid = countSolidOverlaps(from);
        int toSolid = countSolidOverlaps(to);
        int pathSolid = countPathSolids(from, to, dist);

        return new Context(from, to, dx, dy, dz, dist, hDist, ticks, inLiquid, climbable, inWeb,
                onSlime, weirdSurface, fromSolid, toSolid, pathSolid);
    }

    public static boolean shouldSkip(Player p, PlayerData data) {
        if (p == null || data == null) return true;
        if (p.isFlying() || p.getAllowFlight()) return true;
        if (p.isInsideVehicle()) return true;
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) return true;
        return false;
    }

    public static int countSolidOverlaps(Location loc) {
        if (loc == null || loc.getWorld() == null) return 0;
        Set<String> seen = new HashSet<String>();
        int count = 0;

        double minX = loc.getX() - 0.3;
        double maxX = loc.getX() + 0.3;
        double minY = loc.getY();
        double maxY = loc.getY() + 1.8;
        double minZ = loc.getZ() - 0.3;
        double maxZ = loc.getZ() + 0.3;

        for (double x = minX; x <= maxX; x += 0.3) {
            for (double y = minY; y <= maxY; y += 0.6) {
                for (double z = minZ; z <= maxZ; z += 0.3) {
                    Block b = loc.getWorld().getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                    if (b == null) continue;
                    Material m = b.getType();
                    if (!isBlocking(m)) continue;
                    String key = b.getX() + ":" + b.getY() + ":" + b.getZ();
                    if (seen.add(key)) count++;
                }
            }
        }

        return count;
    }

    public static int countPathSolids(Location from, Location to, double dist) {
        if (from == null || to == null || from.getWorld() == null) return 0;
        if (dist < 0.05) return 0;

        double step = 0.2;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        Set<String> seen = new HashSet<String>();
        int count = 0;

        for (double t = step; t < dist; t += step) {
            double pct = t / dist;
            Location sample = from.clone().add(dx * pct, dy * pct, dz * pct);
            for (double yOff = 0.2; yOff <= 1.6; yOff += 0.7) {
                Block b = sample.clone().add(0, yOff, 0).getBlock();
                if (b == null) continue;
                Material m = b.getType();
                if (!isBlocking(m)) continue;
                String key = b.getX() + ":" + b.getY() + ":" + b.getZ();
                if (seen.add(key)) count++;
            }
        }

        return count;
    }

    public static boolean isBlocking(Material m) {
        if (m == null || m == Material.AIR) return false;
        if (!m.isSolid()) return false;
        String name = m.name();
        if (name.contains("SIGN") || name.contains("BUTTON") || name.contains("PLATE")
                || name.contains("LEVER") || name.contains("TORCH") || name.contains("FLOWER")
                || name.contains("MUSHROOM") || name.contains("SAPLING") || name.contains("BANNER")
                || name.contains("CARPET") || name.contains("DOOR") || name.contains("FENCE_GATE")
                || name.contains("TRAP_DOOR")) {
            return false;
        }
        return m != Material.SNOW && m != Material.SKULL;
    }

    private static boolean isLiquid(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.WATER || t == Material.STATIONARY_WATER
                || t == Material.LAVA || t == Material.STATIONARY_LAVA;
    }

    private static boolean isClimbable(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.LADDER || t == Material.VINE;
    }

    private static boolean isWeb(Location loc) {
        return loc.getBlock().getType() == Material.WEB;
    }

    private static boolean isSlime(Location loc) {
        return loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.SLIME_BLOCK;
    }

    private static boolean isWeirdSurface(Location loc) {
        Material t = loc.clone().subtract(0, 1, 0).getBlock().getType();
        String name = t.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS");
    }

    public static final class Context {
        public final Location from;
        public final Location to;
        public final double dx;
        public final double dy;
        public final double dz;
        public final double dist;
        public final double hDist;
        public final double ticks;
        public final boolean inLiquid;
        public final boolean climbable;
        public final boolean inWeb;
        public final boolean onSlime;
        public final boolean weirdSurface;
        public final int fromSolid;
        public final int toSolid;
        public final int pathSolid;

        public Context(Location from, Location to, double dx, double dy, double dz, double dist, double hDist,
                       double ticks, boolean inLiquid, boolean climbable, boolean inWeb, boolean onSlime,
                       boolean weirdSurface, int fromSolid, int toSolid, int pathSolid) {
            this.from = from;
            this.to = to;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.dist = dist;
            this.hDist = hDist;
            this.ticks = ticks;
            this.inLiquid = inLiquid;
            this.climbable = climbable;
            this.inWeb = inWeb;
            this.onSlime = onSlime;
            this.weirdSurface = weirdSurface;
            this.fromSolid = fromSolid;
            this.toSolid = toSolid;
            this.pathSolid = pathSolid;
        }
    }
}
