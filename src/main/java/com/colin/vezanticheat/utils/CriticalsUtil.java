package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class CriticalsUtil {
    private CriticalsUtil() {}

    public static boolean isSolidGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        for (double ox = -0.30; ox <= 0.30; ox += 0.30) {
            for (double oz = -0.30; oz <= 0.30; oz += 0.30) {
                Material below = loc.clone().add(ox, -0.10, oz).getBlock().getType();
                if (isGroundMaterial(below)) return true;
            }
        }
        return false;
    }

    public static boolean isInvalidCritEnvironment(Player p) {
        if (p == null) return true;
        if (p.isInsideVehicle()) return true;

        Location loc = p.getLocation();
        if (loc == null || loc.getWorld() == null) return true;

        Material feet = loc.getBlock().getType();
        if (feet == Material.WATER || feet == Material.STATIONARY_WATER
                || feet == Material.LAVA || feet == Material.STATIONARY_LAVA
                || feet == Material.LADDER || feet == Material.VINE
                || feet == Material.WEB) {
            return true;
        }

        Material below = loc.clone().subtract(0.0, 1.0, 0.0).getBlock().getType();
        return below == Material.WATER || below == Material.STATIONARY_WATER
                || below == Material.LAVA || below == Material.STATIONARY_LAVA
                || below == Material.LADDER || below == Material.VINE
                || below == Material.WEB;
    }

    public static boolean hasLowCeiling(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        for (double ox = -0.30; ox <= 0.30; ox += 0.30) {
            for (double oz = -0.30; oz <= 0.30; oz += 0.30) {
                if (isSolid(world, loc.getX() + ox, loc.getY() + 1.90, loc.getZ() + oz)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isNearHalfBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Material below = loc.clone().subtract(0.0, 1.0, 0.0).getBlock().getType();
        String name = below.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS") || below == Material.SOUL_SAND;
    }

    /**
     * Skip crit heuristics during normal PvP techniques (S-tap, standing click spam, trades).
     * Clients often send onGround=false while grounded during these sequences.
     */
    public static boolean shouldExemptCritHeuristic(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (data == null) return true;
        if (CombatContextAnalyzer.isActiveCombatSpam(data, nowMs)) return true;
        if (CombatContextAnalyzer.isLikelyLegitCombatMovement(data, nowMs)) return true;
        if (data.getLastUseEntityTime() > 0L && nowMs - data.getLastUseEntityTime() <= 500L) return true;
        return EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs);
    }

    private static boolean isGroundMaterial(Material material) {
        if (material == null) return false;
        if (!material.isSolid()) return false;
        String name = material.name();
        return !name.contains("FENCE_GATE");
    }

    private static boolean isSolid(World world, double x, double y, double z) {
        Block block = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (block == null) return false;
        Material type = block.getType();
        return type != null && type.isSolid();
    }
}
