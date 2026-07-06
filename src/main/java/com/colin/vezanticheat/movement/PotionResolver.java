package com.colin.vezanticheat.movement;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PotionResolver {

    private PotionResolver() {}

    public static int speedLevel(Player player) {
        return level(player, PotionEffectType.SPEED);
    }

    public static int slownessLevel(Player player) {
        return level(player, PotionEffectType.SLOW);
    }

    public static int jumpLevel(Player player) {
        return level(player, PotionEffectType.JUMP);
    }

    public static double movementAttribute(Player player, MovementPhysicsConfig cfg) {
        double base = cfg.baseMoveSpeed;
        if (player != null) {
            base = Math.max(0.01D, player.getWalkSpeed() / 2.0D);
        }
        int speed = speedLevel(player);
        if (speed > 0) {
            base = Math.max(base, cfg.baseMoveSpeed * (1.0D + cfg.speedPotionPerLevel * speed));
        }
        int slow = slownessLevel(player);
        if (slow > 0) {
            base *= Math.max(0.0D, 1.0D - cfg.slownessPerLevel * slow);
        }
        return base;
    }

    public static double jumpVelocity(Player player, MovementPhysicsConfig cfg) {
        return cfg.jumpVelocity + (jumpLevel(player) * cfg.jumpBoostVelocity);
    }

    private static int level(Player player, PotionEffectType type) {
        if (player == null || type == null) return 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect != null && effect.getType() == type) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }
}
