package com.colin.vezanticheat.engine;

/**
 * UncertaintyHandler — models the legal "wiggle room" around a prediction.
 *
 * GrimAC expands every candidate velocity into a small box of acceptable values and, after
 * picking the best candidate, subtracts accumulated lenience from the raw offset before it
 * is allowed to flag. This reproduces the important parts of that for 1.8.8:
 *
 *  - per-tick additive horizontal/vertical uncertainty used to widen the candidate search,
 *  - {@link #reduceOffset(double)} which removes lenience for knockback, explosions, 0.03
 *    tick-skips, recent block changes, lag/low-TPS, and bouncy/slippery surfaces.
 *
 * Values are intentionally conservative; the offset threshold in OffsetHandler is what
 * ultimately decides a flag, and this layer only ever makes the engine MORE forgiving.
 */
public final class UncertaintyHandler {

    private final MovementPlayer player;

    // Lenience carried from last tick (giveOffsetLenienceNextTick).
    public double lastHorizontalOffset;
    public double lastVerticalOffset;

    // Counters set by the runner before prediction.
    public int blockChangeTicks;     // nearby block changed within the last few ticks
    public int lastTeleportTicks = 1000;

    /**
     * COMPENSATION BUDGET CAP — the maximum TOTAL stacked lenience {@link #reduceOffset(double)} may
     * subtract from a raw offset (default 0.12 blocks; aggressive profiles use 0.08). Without this a
     * cheater that stacks multiple compensation triggers (block-update + multi-packet + 0.03 +
     * hidden-ground) could buy enough lenience to hide a real violation. Set by MovementCheckRunner
     * from config each tick; 0 or negative means "uncapped" (legacy behaviour) but it is always
     * configured to a positive value in practice. Teleport lenience is exempt from the cap since a
     * fresh teleport legitimately invalidates the whole prediction.
     */
    public double leniencyBudgetCap = 0.12D;

    public boolean collidedHorizontally;
    public boolean stuckOnEdge;
    public boolean influencedByBouncyBlock;
    public boolean stepUpTick;
    public boolean slabEdgeTick;
    public boolean dropTick;
    public boolean knockbackGraceTick;
    public boolean combatMotionTick;
    public boolean nearBoat;
    public boolean pistonPushTick;
    public boolean stuckSpeedTick;

    public UncertaintyHandler(MovementPlayer player) {
        this.player = player;
    }

    public void reset() {
        lastHorizontalOffset = 0.0D;
        lastVerticalOffset = 0.0D;
        blockChangeTicks = 0;
        lastTeleportTicks = 1000;
        collidedHorizontally = false;
        stuckOnEdge = false;
        influencedByBouncyBlock = false;
        stepUpTick = false;
        slabEdgeTick = false;
        dropTick = false;
        knockbackGraceTick = false;
        combatMotionTick = false;
        nearBoat = false;
        pistonPushTick = false;
        stuckSpeedTick = false;
    }

    /** Additive horizontal uncertainty used to widen the candidate search box. */
    public double getHorizontalUncertainty() {
        double u = 0.0D;
        if (player.couldSkipTick) u += 0.062D;             // 0.03 (x2 for both halves)
        if (player.pendingKnockback != null) u += 0.04D;
        if (player.pendingExplosion != null) u += 0.10D;
        if (blockChangeTicks > 0) u += 0.04D;
        if (player.onIce) u += 0.02D;
        if (player.inWeb || stuckSpeedTick) u += 0.15D;
        if (nearBoat) u += 0.04D;
        if (pistonPushTick) u += 0.05D;
        if (lowTps()) u += 0.03D;
        u += pingUncertainty();
        return Math.min(1.0D, u + lastHorizontalOffset);
    }

    /** Additive vertical uncertainty used to widen the candidate search box. */
    public double getVerticalUncertainty() {
        double u = 0.0D;
        if (player.couldSkipTick) u += 0.062D;
        if (player.pendingKnockback != null) u += 0.04D;
        if (player.pendingExplosion != null) u += 0.12D;
        if (blockChangeTicks > 0) u += 0.05D;
        if (player.onSlime) u += 0.04D;
        if (stepUpTick) u += 0.10D;
        if (slabEdgeTick) u += 0.08D;
        if (dropTick) u += 0.07D;
        if (knockbackGraceTick) u += 0.16D;
        if (combatMotionTick) u += 0.10D;
        if (player.inWeb || stuckSpeedTick) u += 0.15D;
        if (nearBoat) u += 0.06D;
        if (pistonPushTick) u += 0.06D;
        if (influencedByBouncyBlock) u += 0.06D;
        if (lowTps()) u += 0.03D;
        u += pingUncertainty() * 0.6D;
        return Math.min(1.0D, u + lastVerticalOffset);
    }

    /**
     * Reduce the raw offset by the lenience that applies this tick. The returned value is
     * what OffsetHandler compares against the flag threshold.
     */
    public double reduceOffset(double offset) {
        // Accumulate every stacked compensation term so the TOTAL can be clamped to the budget cap.
        // Teleport lenience is tracked separately and applied AFTER the clamp (it is exempt: a fresh
        // teleport legitimately invalidates the entire prediction for a tick).
        double lenience = 0.0D;

        if (player.pendingKnockback != null && !player.knockbackVerified) {
            lenience += 0.05D;
        }
        if (player.pendingExplosion != null) {
            lenience += 0.12D;
        }
        if (player.couldSkipTick) {
            lenience += 0.06D;
        }
        if (blockChangeTicks > 0) {
            lenience += 0.05D;
        }
        if (player.onSlime || influencedByBouncyBlock) {
            lenience += 0.08D;
        }
        if (player.onIce) {
            lenience += 0.02D;
        }
        if (player.inWeb || stuckSpeedTick) {
            lenience += 0.15D;
        }
        if (player.onClimbable) {
            lenience += 0.05D;
        }
        if (nearBoat) {
            lenience += 0.04D;
        }
        if (pistonPushTick) {
            lenience += 0.05D;
        }
        if (stepUpTick) {
            lenience += 0.10D;
        }
        if (slabEdgeTick) {
            lenience += 0.08D;
        }
        if (dropTick) {
            lenience += 0.08D;
        }
        if (knockbackGraceTick) {
            lenience += 0.16D;
        }
        if (combatMotionTick) {
            lenience += 0.10D;
        }
        if (stuckOnEdge) {
            lenience += 0.05D;
        }
        if (player.usingItem && player.itemInputScale > 0.25D) {
            lenience += 0.06D + ((player.itemInputScale - 0.2D) * 0.18D);
        }
        if (lowTps()) {
            lenience += 0.05D;
        }
        lenience += pingUncertainty();

        // Clamp the stacked compensation to the configured budget so triggers cannot be farmed
        // together to mask a genuine offset. The web/stuck-speed case alone (0.15) can exceed a
        // 0.12 cap; keep the larger of the cap and the single dominant web term so legit cobweb
        // physics is never under-compensated, while still bounding multi-trigger stacking.
        if (leniencyBudgetCap > 0.0D) {
            double cap = leniencyBudgetCap;
            if (player.inWeb || stuckSpeedTick) {
                cap = Math.max(cap, 0.15D);
            }
            lenience = Math.min(lenience, cap);
        }

        double reduced = offset - lenience;
        if (lastTeleportTicks <= 1) {
            reduced -= 0.20D;
        }
        return Math.max(0.0D, reduced);
    }

    private boolean lowTps() {
        return player.tps > 0.0D && player.tps < 19.0D;
    }

    private double pingUncertainty() {
        if (player.ping <= 100) return 0.0D;
        return Math.min(0.06D, (player.ping - 100) * 0.0004D);
    }
}
