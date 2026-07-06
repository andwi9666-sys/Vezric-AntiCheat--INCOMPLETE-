package com.colin.vezanticheat.utils;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Active potion effect helpers (not the short post-splash exempt window on PlayerData).
 */
public final class PotionUtil {

    private PotionUtil() {}

    /** Potion level: Speed I = 1, Speed II = 2, etc. 0 = none. */
    public static int speedLevel(Player player) {
        return effectLevel(player, PotionEffectType.SPEED);
    }

    /** Speed level capped at II for server policy and grace allowances. */
    public static int effectiveSpeedLevel(Player player) {
        return Math.min(2, speedLevel(player));
    }

    /**
     * Vanilla land movement speed (internal 0.1 base). Uses walk speed when elevated by
     * plugins; otherwise applies Speed I/II potion multiplier without double-counting.
     */
    public static float effectiveLandMovementSpeed(Player player) {
        if (player == null) return 0.1F;
        float fromWalk = Math.max(0.01F, player.getWalkSpeed() / 2.0F);
        int level = effectiveSpeedLevel(player);
        if (level <= 0) return fromWalk;
        float fromPotion = 0.1F * (1.0F + 0.2F * level);
        return Math.max(fromWalk, fromPotion);
    }

    public static int slownessLevel(Player player) {
        return effectLevel(player, PotionEffectType.SLOW);
    }

    public static int jumpBoostLevel(Player player) {
        return effectLevel(player, PotionEffectType.JUMP);
    }

    public static boolean hasSpeed(Player player) {
        return speedLevel(player) > 0;
    }

    public static boolean hasJumpBoost(Player player) {
        return jumpBoostLevel(player) > 0;
    }

    /**
     * Small residual horizontal-offset cushion while a Speed effect is active.
     *
     * <p>The movement simulator now models the Speed potion directly through the movement-speed attribute
     * (`0.1 * (1 + 0.2 * level)`), so predicted motion already accounts for the boost. This is only a tiny
     * float/rounding cushion — not the bulk speed compensation it used to be (which double-counted the
     * effect and let speed cheats hide underneath it).</p>
     */
    public static double speedOffsetAllowance(Player player) {
        int level = effectiveSpeedLevel(player);
        if (level <= 0) return 0.0D;
        return 0.012D * level;
    }

    /** Allowance when walk speed is above vanilla (lobby /speed, attribute plugins). */
    public static double walkSpeedOffsetAllowance(Player player) {
        if (player == null) return 0.0D;
        float walk = player.getWalkSpeed();
        if (walk <= 0.205F) return 0.0D;
        double ratio = walk / 0.2D;
        double extra = (ratio - 1.0D) * 0.16D;
        if (player.isSprinting()) extra += (ratio - 1.0D) * 0.10D;
        if (player.isOnGround()) extra += (ratio - 1.0D) * 0.04D;
        return Math.min(0.30D, extra);
    }

    public static double combinedSpeedOffsetAllowance(Player player) {
        return speedOffsetAllowance(player) + walkSpeedOffsetAllowance(player);
    }

    /** Speed potion or elevated walk speed from server plugins. */
    public static boolean hasSpeedBoost(Player player) {
        return hasSpeed(player) || (player != null && player.getWalkSpeed() > 0.205F);
    }

    /** Extra jump-arc offset allowance when speed/jump boost is active. */
    public static double jumpArcOffsetAllowance(Player player) {
        int speed = effectiveSpeedLevel(player);
        int jump = jumpBoostLevel(player);
        return (speed * 0.04D) + (jump * 0.025D);
    }

    private static int effectLevel(Player player, PotionEffectType type) {
        if (player == null || type == null) return 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect != null && effect.getType() == type) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }
}
