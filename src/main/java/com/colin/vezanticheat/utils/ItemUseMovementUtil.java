package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Eating / item-use movement: progressive input slowdown and grace windows.
 * Vanilla carries sprint momentum for several ticks while item-use input ramps down;
 * the engine must not apply instant 0.2 input scale on the first eat tick.
 */
public final class ItemUseMovementUtil {

    private static final long INPUT_BLEND_MS = 750L;
    private static final long ACTIVE_USE_MS = 4500L;

    private ItemUseMovementUtil() {}

    public static boolean isEdible(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getType().isEdible();
    }

    /** Mark eat/item-use start, reset prediction debt, and track compensated use. */
    public static void beginEating(Player player, PlayerData data, long nowMs) {
        if (data == null) return;
        boolean firstTick = data.getLastEatStart() <= 0L;
        data.setLastEatStart(nowMs);
        UseItemTracker.noteUseItem(data, nowMs);
        if (firstTick) {
            resetMovementPredictionDebt(data);
        }
    }

    public static void resetMovementPredictionDebt(PlayerData data) {
        if (data == null) return;
        data.setEngineOffsetAdvantage(0.0D);
        data.clearPendingSetback();
        data.resetEngineAirState();
        data.setEngineSpeedOvershootTicks(0);
        data.setEngineMicroRatioStreak(0);
        data.setEngineHorizontalGainStreak(0);
    }

    /** True while eating, post-consume grace, or recent compensated item use. */
    public static boolean suppressesMovementFlags(PlayerData data, long nowMs) {
        if (data == null) return false;
        if (data.isEatMovementGrace()) return true;
        if (data.getLastEatStart() > 0L) return true;
        if (data.getAutoBlockALastBlockStartMs() > 0L
                && nowMs - data.getAutoBlockALastBlockStartMs() <= 3500L) {
            return true;
        }
        return data.isUseItemActive()
                && data.getUseItemStartMs() > 0L
                && nowMs - data.getUseItemStartMs() <= ACTIVE_USE_MS;
    }

    /** Blend 1.0 -> 0.2 while eating; full input during post-consume grace. */
    public static double movementInputScale(PlayerData data, long nowMs) {
        if (data == null) return 1.0D;
        if (data.isEatMovementGrace()) return 1.0D;

        long start = itemUseStartMs(data);
        if (start <= 0L) return 1.0D;

        long elapsed = Math.max(0L, nowMs - start);
        if (elapsed >= INPUT_BLEND_MS) return 0.2D;
        double t = elapsed / (double) INPUT_BLEND_MS;
        return 1.0D - (0.80D * t);
    }

    public static boolean isEngineUsingItem(PlayerData data, long nowMs) {
        if (data == null) return false;
        if (data.getLastEatStart() > 0L) return true;
        return data.isUseItemActive()
                && data.getUseItemStartMs() > 0L
                && nowMs - data.getUseItemStartMs() <= ACTIVE_USE_MS;
    }

    public static void applyMovementGrace(PlayerData data) {
        if (data == null) return;
        data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.70D));
        data.clearPendingSetback();
    }

    private static long itemUseStartMs(PlayerData data) {
        if (data.getLastEatStart() > 0L) return data.getLastEatStart();
        if (data.isUseItemActive()) return data.getUseItemStartMs();
        return 0L;
    }
}
