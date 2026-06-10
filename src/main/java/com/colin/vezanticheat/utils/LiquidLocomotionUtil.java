package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CompensatedWorld;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Water surface / ladder context helpers for Jesus and fast-ladder checks.
 */
public final class LiquidLocomotionUtil {

    public static final double VANILLA_LADDER_CLIMB_DY = 0.117D;
    public static final double VANILLA_LADDER_PULLUP_DY = 0.20D;
    public static final double DEFAULT_LADDER_LEGIT_MAX_DY = 0.22D;
    public static final double DEFAULT_LADDER_LEGIT_MAX_VOFF = 0.12D;
    public static final double DEFAULT_LADDER_BLATANT_DY = 0.28D;

    private static final double PLAYER_HALF_WIDTH = 0.31D;
    private static final double PLAYER_HEIGHT = 1.81D;

    private LiquidLocomotionUtil() {}

    public static boolean isWaterMaterial(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER;
    }

    public static boolean isClimbableMaterial(Material material) {
        return material == Material.LADDER || material == Material.VINE;
    }

    /** @deprecated use {@link #isOnClimbable(CompensatedWorld, double, double, double)} */
    @Deprecated
    public static boolean isOnClimbable(CompensatedWorld world, int fx, int fy, int fz) {
        return isOnClimbable(world, fx + 0.5D, fy, fz + 0.5D);
    }

    /**
     * True when the player AABB intersects a ladder/vine block (not merely near one).
     */
    public static boolean isOnClimbable(CompensatedWorld world, double x, double y, double z) {
        if (world == null) return false;

        int minX = floor(x - PLAYER_HALF_WIDTH);
        int maxX = floor(x + PLAYER_HALF_WIDTH);
        int minY = floor(y);
        int maxY = floor(y + PLAYER_HEIGHT);
        int minZ = floor(z - PLAYER_HALF_WIDTH);
        int maxZ = floor(z + PLAYER_HALF_WIDTH);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (isClimbableMaterial(world.getType(bx, by, bz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double ladderLegitMaxDy(VezAntiCheat plugin) {
        if (plugin == null) return DEFAULT_LADDER_LEGIT_MAX_DY;
        return plugin.getConfig().getDouble("movement-analysis.ladder-legit-max-dy", DEFAULT_LADDER_LEGIT_MAX_DY);
    }

    public static double ladderLegitMaxVOff(VezAntiCheat plugin) {
        if (plugin == null) return DEFAULT_LADDER_LEGIT_MAX_VOFF;
        return plugin.getConfig().getDouble("movement-analysis.ladder-legit-max-voff", DEFAULT_LADDER_LEGIT_MAX_VOFF);
    }

    public static double ladderBlatantDy(VezAntiCheat plugin) {
        if (plugin == null) return DEFAULT_LADDER_BLATANT_DY;
        return plugin.getConfig().getDouble("movement-analysis.ladder-blatant-dy", DEFAULT_LADDER_BLATANT_DY);
    }

    public static boolean isLegitLadderMotion(double dy, double vOff, double maxDy, double maxVOff) {
        return dy <= maxDy && vOff <= maxVOff;
    }

    public static boolean isBlatantFastLadder(double dy, double vOff, double legitMaxDy, double blatantDy, double maxVOff) {
        return dy > blatantDy || (dy > legitMaxDy && vOff > maxVOff * 1.5D);
    }

    /** Walking on water surface without being fully submerged (classic Jesus). */
    public static boolean isWalkingOnWaterSurface(Player p, PlayerData data, EngineResult er) {
        if (p == null || data == null || er == null) return false;
        if (er.inWater && er.clientGround) return true;
        Location loc = data.getLastLoc();
        if (loc == null || loc.getWorld() == null) return false;

        Material below = loc.clone().subtract(0.0, 0.35, 0.0).getBlock().getType();
        if (!isWaterMaterial(below)) return false;

        Material feet = loc.getBlock().getType();
        if (!isWaterMaterial(feet)) return true;

        double fractionalY = loc.getY() - loc.getBlockY();
        return fractionalY < 0.15D && er.clientGround;
    }

    public static boolean isLiquidLocomotionContext(Player p, PlayerData data, EngineResult er) {
        if (er == null) return false;
        return er.inWater || er.onClimbable || isWalkingOnWaterSurface(p, data, er);
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
