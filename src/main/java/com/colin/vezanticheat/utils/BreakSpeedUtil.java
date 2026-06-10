package com.colin.vezanticheat.utils;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Calculates expected block break time using MC 1.8 mechanics.
 *
 * Formula:
 * 1. Base destroy speed from tool (1.0 if wrong tool or bare hand)
 * 2. Efficiency enchant: speed += level² + 1
 * 3. Haste: speed *= 1 + 0.2 * level
 * 4. Mining Fatigue: speed *= 0.3^level
 * 5. In water (no Aqua Affinity): speed /= 5
 * 6. Not on ground: speed /= 5
 * 7. damage = speed / hardness / (canHarvest ? 30 : 100)
 * 8. If damage >= 1 → instant break. Otherwise ticks = ceil(1 / damage)
 * 9. Break time ms = ticks * 50
 */
public final class BreakSpeedUtil {

    private BreakSpeedUtil() {}

    /**
     * Calculate the minimum time (ms) a player should need to break this block.
     * Returns 0 for instant-break blocks.
     * Returns -1 for unbreakable blocks (bedrock, barriers, etc).
     */
    public static long expectedBreakMs(Player p, Block block) {
        if (p == null || block == null) return 0;

        Material mat = block.getType();
        float hardness = getHardness(mat);

        if (hardness < 0) return -1;
        if (hardness == 0) return 0;

        ItemStack tool = p.getItemInHand();
        boolean canHarvest = canHarvest(mat, tool);

        double speed = getDestroySpeed(mat, tool);

        // Efficiency enchantment
        if (tool != null && tool.getType() != Material.AIR) {
            int efficiency = tool.getEnchantmentLevel(Enchantment.DIG_SPEED);
            if (efficiency > 0 && speed > 1.0) {
                speed += (efficiency * efficiency) + 1;
            }
        }

        // Haste effect
        int haste = getPotionLevel(p, PotionEffectType.FAST_DIGGING);
        if (haste > 0) {
            speed *= (1.0 + 0.2 * haste);
        }

        // Mining Fatigue effect
        int fatigue = getPotionLevel(p, PotionEffectType.SLOW_DIGGING);
        if (fatigue > 0) {
            speed *= Math.pow(0.3, fatigue);
        }

        // Underwater penalty (no Aqua Affinity)
        if (isInWater(p) && !hasAquaAffinity(p)) {
            speed /= 5.0;
        }

        // Not on ground penalty
        if (!p.isOnGround()) {
            speed /= 5.0;
        }

        double damage = speed / hardness / (canHarvest ? 30.0 : 100.0);

        if (damage >= 1.0) return 0;

        int ticks = (int) Math.ceil(1.0 / damage);
        return ticks * 50L;
    }

    /**
     * Block hardness table for MC 1.8.
     */
    public static float getHardness(Material mat) {
        if (mat == null) return 0;
        String name = mat.name();

        // Unbreakable
        if (mat == Material.BEDROCK || mat == Material.BARRIER
                || name.equals("END_PORTAL_FRAME") || name.equals("END_PORTAL")
                || name.equals("COMMAND") || name.equals("COMMAND_BLOCK")) return -1f;

        // Instant-break (hardness 0)
        if (mat == Material.TORCH || mat == Material.REDSTONE_TORCH_ON || mat == Material.REDSTONE_TORCH_OFF) return 0f;
        if (name.contains("LONG_GRASS") || name.contains("TALL_GRASS") || mat == Material.DEAD_BUSH) return 0f;
        if (mat == Material.CROPS || mat == Material.MELON_STEM || mat == Material.PUMPKIN_STEM
                || mat == Material.CARROT || mat == Material.POTATO) return 0f;
        if (mat == Material.FLOWER_POT || mat == Material.RED_ROSE || mat == Material.YELLOW_FLOWER) return 0f;
        if (mat == Material.SUGAR_CANE_BLOCK || mat == Material.VINE || mat == Material.WATER_LILY) return 0f;
        if (mat == Material.FIRE || mat == Material.TNT || mat == Material.TRIPWIRE || mat == Material.TRIPWIRE_HOOK) return 0f;
        if (mat == Material.REDSTONE_WIRE || mat == Material.LEVER || mat == Material.STONE_BUTTON || mat == Material.WOOD_BUTTON) return 0f;
        if (mat == Material.SNOW) return 0.1f;
        if (mat == Material.SAPLING || mat == Material.BROWN_MUSHROOM || mat == Material.RED_MUSHROOM) return 0f;

        // Very soft
        if (mat == Material.LEAVES || mat == Material.LEAVES_2) return 0.2f;
        if (mat == Material.BED_BLOCK || mat == Material.BED) return 0.2f;
        if (mat == Material.SPONGE) return 0.6f;
        if (mat == Material.GLASS || mat == Material.THIN_GLASS || mat == Material.STAINED_GLASS
                || mat == Material.STAINED_GLASS_PANE || mat == Material.GLOWSTONE) return 0.3f;

        // Dirt, sand, gravel, clay
        if (mat == Material.DIRT || mat == Material.GRASS || mat == Material.MYCEL) return 0.5f;
        if (mat == Material.SAND || mat == Material.SOUL_SAND || mat == Material.GRAVEL) return 0.5f;
        if (mat == Material.CLAY) return 0.6f;

        // Wood
        if (name.contains("LOG") || name.contains("WOOD") || name.equals("BOOKSHELF")) return 2.0f;
        if (name.contains("PLANK") || name.contains("FENCE") || name.contains("SIGN")
                || name.contains("DOOR") && !name.contains("IRON")) return 2.0f;
        if (mat == Material.CHEST || mat == Material.TRAPPED_CHEST || mat == Material.WORKBENCH
                || mat == Material.JUKEBOX || mat == Material.NOTE_BLOCK) return 2.5f;

        // Stone variants
        if (mat == Material.STONE || mat == Material.COBBLESTONE || name.contains("BRICK")
                || name.contains("MOSSY") || name.contains("SMOOTH")) return 1.5f;
        if (mat == Material.SANDSTONE || name.contains("SANDSTONE")) return 0.8f;
        if (mat == Material.NETHERRACK) return 0.4f;
        if (mat == Material.ENDER_STONE) return 3.0f;

        // Ore blocks
        if (mat == Material.COAL_ORE || mat == Material.DIAMOND_ORE || mat == Material.EMERALD_ORE
                || mat == Material.GOLD_ORE || mat == Material.IRON_ORE || mat == Material.LAPIS_ORE
                || mat == Material.REDSTONE_ORE || mat == Material.GLOWING_REDSTONE_ORE
                || mat == Material.QUARTZ_ORE) return 3.0f;

        // Metal / mineral blocks
        if (mat == Material.IRON_BLOCK || mat == Material.GOLD_BLOCK || mat == Material.DIAMOND_BLOCK
                || mat == Material.EMERALD_BLOCK || mat == Material.LAPIS_BLOCK
                || mat == Material.REDSTONE_BLOCK) return 5.0f;

        // Obsidian
        if (mat == Material.OBSIDIAN) return 50.0f;

        // Anvil, enchanting table, iron door
        if (mat == Material.ANVIL) return 5.0f;
        if (mat == Material.ENCHANTMENT_TABLE) return 5.0f;
        if (mat == Material.IRON_DOOR_BLOCK || mat == Material.IRON_DOOR) return 5.0f;

        // Ice
        if (mat == Material.ICE || mat == Material.PACKED_ICE) return 0.5f;

        // Piston
        if (mat == Material.PISTON_BASE || mat == Material.PISTON_STICKY_BASE) return 0.5f;

        // Wool / carpet
        if (name.contains("WOOL") || name.contains("CARPET")) return 0.8f;

        // Default
        return 1.5f;
    }

    /**
     * Check if the tool can harvest the block (get drops).
     * Wrong tool = 3.33x slower via the 100 divisor instead of 30.
     */
    public static boolean canHarvest(Material block, ItemStack tool) {
        if (block == null) return true;
        String name = block.name();
        Material toolType = (tool != null) ? tool.getType() : Material.AIR;
        String toolName = toolType.name();

        boolean needsPickaxe = block == Material.STONE || block == Material.COBBLESTONE
                || name.contains("BRICK") || name.contains("ORE")
                || block == Material.OBSIDIAN || block == Material.ENDER_STONE
                || block == Material.NETHERRACK || name.contains("SANDSTONE")
                || block == Material.IRON_BLOCK || block == Material.GOLD_BLOCK
                || block == Material.DIAMOND_BLOCK || block == Material.EMERALD_BLOCK
                || block == Material.LAPIS_BLOCK || block == Material.REDSTONE_BLOCK
                || block == Material.ANVIL || block == Material.ENCHANTMENT_TABLE
                || block == Material.IRON_DOOR_BLOCK;

        if (needsPickaxe) {
            return toolName.contains("PICKAXE");
        }

        return true;
    }

    /**
     * Get the tool's destroy speed for a given block.
     * Returns 1.0 for bare hand or wrong tool type.
     */
    public static double getDestroySpeed(Material block, ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) return 1.0;

        String toolName = tool.getType().name();
        String blockName = block.name();

        double tierSpeed = 1.0;
        if (toolName.contains("WOOD_") || toolName.contains("WOODEN_")) tierSpeed = 2.0;
        else if (toolName.contains("STONE_")) tierSpeed = 4.0;
        else if (toolName.contains("IRON_")) tierSpeed = 6.0;
        else if (toolName.contains("DIAMOND_")) tierSpeed = 8.0;
        else if (toolName.contains("GOLD_")) tierSpeed = 12.0;

        // Shears
        if (toolName.equals("SHEARS")) {
            if (block == Material.LEAVES || block == Material.LEAVES_2) return 15.0;
            if (blockName.contains("WOOL")) return 5.0;
            if (block == Material.VINE || block == Material.WEB) return 15.0;
        }

        // Sword vs web
        if (toolName.contains("SWORD")) {
            if (block == Material.WEB) return 15.0;
            return 1.0;
        }

        boolean isPickaxe = toolName.contains("PICKAXE");
        boolean isAxe = toolName.contains("_AXE") && !isPickaxe;
        boolean isShovel = toolName.contains("SPADE");

        if (isPickaxe) {
            if (block == Material.STONE || block == Material.COBBLESTONE
                    || blockName.contains("BRICK") || blockName.contains("ORE")
                    || block == Material.OBSIDIAN || block == Material.ENDER_STONE
                    || block == Material.NETHERRACK || blockName.contains("SANDSTONE")
                    || block == Material.IRON_BLOCK || block == Material.GOLD_BLOCK
                    || block == Material.DIAMOND_BLOCK || block == Material.EMERALD_BLOCK
                    || block == Material.LAPIS_BLOCK || block == Material.REDSTONE_BLOCK
                    || block == Material.ANVIL || block == Material.ENCHANTMENT_TABLE
                    || block == Material.ICE || block == Material.PACKED_ICE) {
                return tierSpeed;
            }
        }

        if (isAxe) {
            if (blockName.contains("LOG") || blockName.contains("WOOD") || blockName.contains("PLANK")
                    || blockName.contains("FENCE") || blockName.contains("SIGN")
                    || block == Material.CHEST || block == Material.TRAPPED_CHEST
                    || block == Material.WORKBENCH || block == Material.JUKEBOX
                    || block == Material.BOOKSHELF || block == Material.NOTE_BLOCK
                    || block == Material.PUMPKIN || block == Material.MELON_BLOCK
                    || (blockName.contains("DOOR") && !blockName.contains("IRON"))) {
                return tierSpeed;
            }
        }

        if (isShovel) {
            if (block == Material.DIRT || block == Material.GRASS || block == Material.MYCEL
                    || block == Material.SAND || block == Material.SOUL_SAND
                    || block == Material.GRAVEL || block == Material.CLAY
                    || block == Material.SNOW || block == Material.SNOW_BLOCK) {
                return tierSpeed;
            }
        }

        return 1.0;
    }

    private static int getPotionLevel(Player p, PotionEffectType type) {
        if (p == null || type == null) return 0;
        for (PotionEffect pe : p.getActivePotionEffects()) {
            if (pe != null && pe.getType() == type) return pe.getAmplifier() + 1;
        }
        return 0;
    }

    private static boolean isInWater(Player p) {
        Material at = p.getLocation().getBlock().getType();
        return at == Material.WATER || at == Material.STATIONARY_WATER;
    }

    private static boolean hasAquaAffinity(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        if (helmet == null) return false;
        return helmet.getEnchantmentLevel(Enchantment.WATER_WORKER) > 0;
    }
}
