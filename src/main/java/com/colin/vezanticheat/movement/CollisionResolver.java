package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.engine.BlockProvider;
import com.colin.vezanticheat.engine.Collisions;
import org.bukkit.Material;

public final class CollisionResolver {

    private CollisionResolver() {}

    public static Collisions.Result collide(BlockProvider provider, double x, double y, double z,
                                            double dx, double dy, double dz, boolean onGround) {
        return Collisions.collide(provider, x, y, z, dx, dy, dz, onGround);
    }

    public static boolean isOnGround(BlockProvider provider, double x, double y, double z) {
        return Collisions.isOnGround(provider, x, y, z);
    }

    public static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER;
    }

    public static boolean isLava(Material material) {
        return material == Material.LAVA || material == Material.STATIONARY_LAVA;
    }

    public static boolean isClimbable(Material material) {
        return material == Material.LADDER || material == Material.VINE;
    }
}
