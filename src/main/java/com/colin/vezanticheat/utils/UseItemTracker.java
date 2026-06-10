package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks compensated item-use state for NoSlow / MultiActions checks (Grim-style).
 * Detects fake RELEASE_USE_ITEM spam while the client still blocks or eats.
 */
public final class UseItemTracker {

    private static final long MIN_SUSTAINED_BLOCK_MS = 100L;
    private static final long JUMP_COMBAT_WINDOW_MS = 950L;
    private static final long MELEE_SWING_WINDOW_MS = 250L;
    private static final long STALE_USE_ITEM_MS = 4500L;
    private static final long LEGIT_SWORD_BLOCK_SESSION_MS = 3500L;

    private UseItemTracker() {}

    public static boolean isUsingItem(Player player, PlayerData data) {
        if (player == null || data == null) return false;
        if (player.isBlocking()) return true;
        if (data.getBowPullStart() > 0L) return true;
        if (data.getLastEatStart() > 0L) return true;
        return data.isUseItemActive();
    }

    /**
     * Stricter item-use signal for attack/swing MultiActions checks. Ignores same-tick sword
     * block packets from left-click swings and stale useItemActive during jump melee.
     */
    public static boolean isAttackConflictingItemUse(Player player, PlayerData data, long nowMs) {
        if (data == null) return false;
        if (data.getBowPullStart() > 0L) return true;
        if (data.getLastEatStart() > 0L) return true;
        if (isLegitSwordBlockSession(player, data, nowMs)) return false;

        if (player != null && player.isBlocking()) {
            return isSustainedBlocking(player, data, nowMs);
        }

        if (!data.isUseItemActive()) return false;
        long useAge = data.getUseItemStartMs() > 0L ? nowMs - data.getUseItemStartMs() : Long.MAX_VALUE;
        if (useAge > STALE_USE_ITEM_MS) return false;
        return !isLikelyMeleeCombat(player, data, nowMs);
    }

    /**
     * Legit 1.8 sword block-hitting / spam block: attack, swing, and RELEASE_USE_ITEM bursts
     * while holding a sword are normal client behavior, not multi-action abuse.
     */
    public static boolean isLegitSwordBlockSession(Player player, PlayerData data, long nowMs) {
        if (data == null) return false;
        long blockStart = data.getAutoBlockALastBlockStartMs();
        if (blockStart > 0L && nowMs - blockStart <= LEGIT_SWORD_BLOCK_SESSION_MS) {
            return true;
        }
        if (player != null) {
            ItemStack hand = player.getItemInHand();
            if (hand != null && hand.getType().name().endsWith("_SWORD") && player.isBlocking()) {
                return true;
            }
        }
        return player != null
                && data.isUseItemActive()
                && isLikelyMeleeCombat(player, data, nowMs)
                && isHoldingSword(player);
    }

    /** Clear stale compensated-use flags when a melee attack ends client-side item use. */
    public static void reconcileMeleeAttack(Player player, PlayerData data, long nowMs) {
        if (data == null) return;
        if (data.getBowPullStart() > 0L || data.getLastEatStart() > 0L) return;
        if (!isLikelyMeleeCombat(player, data, nowMs)) return;
        if (player == null) {
            noteStopUse(data);
            return;
        }

        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) return;
        if (ItemUseMovementUtil.isEdible(hand) || hand.getType() == Material.BOW) return;

        String name = hand.getType().name();
        if (!name.endsWith("_SWORD") && !name.endsWith("_AXE")) return;

        if (!player.isBlocking() || !isSustainedBlocking(player, data, nowMs)) {
            noteStopUse(data);
        }
    }

    public static boolean isFakeReleaseUse(Player player, PlayerData data, long nowMs) {
        if (data == null) return false;
        if (data.isEatMovementGrace() || ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) return false;
        if (player != null && isUsingItem(player, data)) return false;
        if (isLegitSwordBlockSession(player, data, nowMs)) return false;

        long releaseWindow = 900L;
        if (data.getLastReleaseUseItemMs() <= 0L
                || nowMs - data.getLastReleaseUseItemMs() > releaseWindow) {
            return false;
        }

        if (data.getReleaseUseItemStreak() >= 2) return true;

        return player != null
                && isLikelyBlockingItem(player)
                && data.getReleaseUseItemStreak() >= 1
                && !isLikelyMeleeCombat(player, data, nowMs);
    }

    /**
     * Server-side item use OR cheat-client fake release while still performing slowed actions.
     * Must not drive movement simulation — only NoSlow / MultiActions corroboration.
     */
    public static boolean isCompensatedItemUse(Player player, PlayerData data, long nowMs) {
        if (data == null) return false;
        if (player != null && isUsingItem(player, data)) return true;
        return isFakeReleaseUse(player, data, nowMs);
    }

    public static void noteUseItem(PlayerData data, long nowMs) {
        if (data == null) return;
        data.setUseItemActive(true);
        data.setUseItemStartMs(nowMs);
    }

    public static void noteStopUse(PlayerData data) {
        if (data == null) return;
        data.setUseItemActive(false);
        data.setUseItemStartMs(0L);
    }

    public static void noteReleaseUseItem(PlayerData data, long nowMs) {
        if (data == null) return;
        data.setLastReleaseUseItemMs(nowMs);
        if (nowMs - data.getLastReleaseUseItemTickMs() <= 120L) {
            data.setReleaseUseItemStreak(data.getReleaseUseItemStreak() + 1);
        } else {
            data.setReleaseUseItemStreak(1);
        }
        data.setLastReleaseUseItemTickMs(nowMs);
    }

    public static void noteConsumeComplete(PlayerData data, long nowMs, long graceMs) {
        if (data == null) return;
        data.setLastEatStart(0L);
        data.clearReleaseUseItemState();
        noteStopUse(data);
        ItemUseMovementUtil.resetMovementPredictionDebt(data);
        data.markEatMovementGrace(graceMs);
    }

    public static void decayReleaseStreak(PlayerData data, long nowMs) {
        if (data == null) return;
        if (data.getLastReleaseUseItemMs() > 0L
                && nowMs - data.getLastReleaseUseItemMs() > 250L) {
            data.setReleaseUseItemStreak(Math.max(0, data.getReleaseUseItemStreak() - 1));
        }
    }

    public static void tickDecay(PlayerData data, long nowMs, long maxUseMs) {
        if (data == null || !data.isUseItemActive()) return;
        if (data.getUseItemStartMs() > 0L && (nowMs - data.getUseItemStartMs()) > maxUseMs) {
            noteStopUse(data);
        }
        decayReleaseStreak(data, nowMs);
    }

    private static boolean isLikelyBlockingItem(Player player) {
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getType() == Material.AIR) return false;
        String name = hand.getType().name();
        return name.endsWith("_SWORD") || hand.getType() == Material.BOW;
    }

    private static boolean isHoldingSword(Player player) {
        if (player == null) return false;
        ItemStack hand = player.getItemInHand();
        return hand != null && hand.getType().name().endsWith("_SWORD");
    }

    private static boolean isSustainedBlocking(Player player, PlayerData data, long nowMs) {
        long blockStart = data.getAutoBlockALastBlockStartMs();
        if (blockStart <= 0L) {
            return !isLikelyMeleeCombat(player, data, nowMs);
        }

        long blockAge = nowMs - blockStart;
        if (blockAge < MIN_SUSTAINED_BLOCK_MS) {
            return false;
        }

        if (!player.isOnGround()
                && data.getLastJumpTime() > 0L
                && nowMs - data.getLastJumpTime() <= JUMP_COMBAT_WINDOW_MS
                && blockAge < 180L) {
            return false;
        }
        return true;
    }

    private static boolean isLikelyMeleeCombat(Player player, PlayerData data, long nowMs) {
        if (player != null && !player.isOnGround()
                && data.getLastJumpTime() > 0L
                && nowMs - data.getLastJumpTime() <= JUMP_COMBAT_WINDOW_MS) {
            return true;
        }
        long swingAge = data.getLastArmSwingPacket() > 0L
                ? nowMs - data.getLastArmSwingPacket()
                : Long.MAX_VALUE;
        return swingAge <= MELEE_SWING_WINDOW_MS;
    }
}
