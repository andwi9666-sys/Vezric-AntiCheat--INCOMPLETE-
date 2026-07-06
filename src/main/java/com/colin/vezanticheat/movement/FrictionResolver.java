package com.colin.vezanticheat.movement;

import org.bukkit.Material;

public final class FrictionResolver {

    private FrictionResolver() {}

    public static double slipperiness(Material material, MovementPhysicsConfig cfg) {
        if (material == Material.ICE || material == Material.PACKED_ICE) return cfg.iceSlipperiness;
        if (material == Material.SLIME_BLOCK) return cfg.slimeSlipperiness;
        return cfg.normalSlipperiness;
    }

    public static String surface(Material material, boolean inWater, boolean inLava,
                                 boolean inWeb, boolean onClimbable) {
        if (inWeb) return "web";
        if (inWater) return "water";
        if (inLava) return "lava";
        if (onClimbable) return "climbable";
        if (material == Material.ICE || material == Material.PACKED_ICE) return "ice";
        if (material == Material.SLIME_BLOCK) return "slime";
        return "normal";
    }
}
