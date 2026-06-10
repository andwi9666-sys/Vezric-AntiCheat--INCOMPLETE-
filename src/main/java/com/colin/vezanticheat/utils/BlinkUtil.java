package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.World;

/**
 * Utility methods for blink detection.
 */
public final class BlinkUtil {
    private BlinkUtil() {}

    /**
     * Get the Euclidean horizontal distance between two locations.
     */
    public static double getHorizontalDistance(Location from, Location to) {
        if (from == null || to == null) return 0.0;
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.hypot(dx, dz);
    }

    /**
     * Get the full 3D distance between two locations.
     */
    public static double getDistance3D(Location from, Location to) {
        if (from == null || to == null) return 0.0;
        return from.distance(to);
    }

    /**
     * Check if the player is in recent combat (was damaged or attacked recently).
     */
    public static boolean inRecentCombat(PlayerData data, long now, long combatGraceMs) {
        if (data == null) return false;
        return (now - data.getLastDamageTime()) <= combatGraceMs
                || (now - data.getLastUseEntityTime()) <= combatGraceMs;
    }

    /**
     * Maximum legitimate horizontal distance a player could cover in the given number of
     * milliseconds. Used as a sanity check for displacement after a blink freeze.
     * This is intentionally generous — we only want to catch blatant teleportation.
     */
    public static double maxLegitDistance(Player player, long durationMs) {
        if (player == null || durationMs <= 0) return 0.0;

        double ticks = Math.max(1.0, durationMs / 50.0);

        // Base speed per tick (sprint = 0.28, walk = 0.22)
        double perTick = player.isSprinting() ? 0.28 : 0.22;

        // Speed potion
        int speedAmp = PotionUtil.effectiveSpeedLevel(player);
        if (speedAmp > 0) {
            perTick *= (1.0 + 0.20 * speedAmp);
        }

        // Generous margin for network jitter
        int ping = Math.max(0, PingUtil.getPing(player));
        double margin = 0.5 + Math.min(0.5, ping * 0.003);

        return (perTick * ticks) + margin;
    }

    public static double requiredDistance(Player player, long durationMs, double extraBuffer) {
        return Math.max(0.0D, maxLegitDistance(player, durationMs) + Math.max(0.0D, extraBuffer));
    }

    public static BurstTravel sampleBurstTravel(PlayerData data, World world, long startMs, long endMs) {
        return sampleBurstTravel(data, world, startMs, endMs, null, null);
    }

    public static BurstTravel sampleBurstTravel(PlayerData data, World world, long startMs, long endMs,
                                                Location startAnchor, Location endAnchor) {
        if (data == null || world == null || endMs < startMs) {
            return BurstTravel.empty();
        }

        Location first = null;
        Location last = null;
        Location previous = null;
        double totalHorizontal = 0.0D;
        double totalDistance3D = 0.0D;
        double maxStepHorizontal = 0.0D;
        int samples = 0;

        if (matchesWorld(startAnchor, world)) {
            first = startAnchor.clone();
            last = startAnchor.clone();
            previous = startAnchor.clone();
        }

        for (PlayerData.PositionSample sample : data.getPositionHistory()) {
            if (sample == null || !sample.matchesWorld(world)) continue;
            if (sample.getTime() < startMs || sample.getTime() > endMs) continue;

            Location current = sample.toLocation(world);
            if (current == null) continue;

            if (first == null) {
                first = current.clone();
            }
            if (previous != null) {
                double stepHorizontal = getHorizontalDistance(previous, current);
                totalHorizontal += stepHorizontal;
                totalDistance3D += getDistance3D(previous, current);
                maxStepHorizontal = Math.max(maxStepHorizontal, stepHorizontal);
            }

            previous = current;
            last = current.clone();
            samples++;
        }

        if (matchesWorld(endAnchor, world) && !samePoint(previous, endAnchor)) {
            if (first == null) {
                first = endAnchor.clone();
            }
            if (previous != null) {
                double stepHorizontal = getHorizontalDistance(previous, endAnchor);
                totalHorizontal += stepHorizontal;
                totalDistance3D += getDistance3D(previous, endAnchor);
                maxStepHorizontal = Math.max(maxStepHorizontal, stepHorizontal);
            }
            last = endAnchor.clone();
        } else if (matchesWorld(endAnchor, world) && last == null) {
            first = endAnchor.clone();
            last = endAnchor.clone();
        }

        if (first == null || last == null) {
            return BurstTravel.empty();
        }

        return new BurstTravel(
                samples,
                getHorizontalDistance(first, last),
                getDistance3D(first, last),
                totalHorizontal,
                totalDistance3D,
                maxStepHorizontal
        );
    }

    private static boolean matchesWorld(Location loc, World world) {
        return loc != null
                && loc.getWorld() != null
                && loc.getWorld().equals(world);
    }

    private static boolean samePoint(Location first, Location second) {
        return first != null
                && second != null
                && Math.abs(first.getX() - second.getX()) < 1.0E-4
                && Math.abs(first.getY() - second.getY()) < 1.0E-4
                && Math.abs(first.getZ() - second.getZ()) < 1.0E-4;
    }

    public static final class BurstTravel {
        public final int samples;
        public final double directHorizontal;
        public final double directDistance3D;
        public final double totalHorizontal;
        public final double totalDistance3D;
        public final double maxStepHorizontal;

        private BurstTravel(int samples, double directHorizontal, double directDistance3D,
                            double totalHorizontal, double totalDistance3D, double maxStepHorizontal) {
            this.samples = samples;
            this.directHorizontal = directHorizontal;
            this.directDistance3D = directDistance3D;
            this.totalHorizontal = totalHorizontal;
            this.totalDistance3D = totalDistance3D;
            this.maxStepHorizontal = maxStepHorizontal;
        }

        public static BurstTravel empty() {
            return new BurstTravel(0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
