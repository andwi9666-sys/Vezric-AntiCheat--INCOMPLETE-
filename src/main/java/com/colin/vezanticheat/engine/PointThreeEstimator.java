package com.colin.vezanticheat.engine;

/**
 * PointThreeEstimator — estimates whether the client could have legally withheld a position
 * packet because its movement was below the 0.03 threshold.
 *
 * In 1.8-1.18.1 the client only sends a full position when it has moved at least 0.03 blocks
 * on some axis since the last sent position; otherwise it sends a position-less flying packet.
 * That means a real player can appear to "teleport" up to ~0.03 per skipped tick. The engine
 * must allow this, otherwise slow/near-stationary movement false-flags.
 *
 * This is a pragmatic estimator: it flags couldSkipTick when the carried velocity is small
 * enough that a sub-threshold tick is plausible, or when no position was included in the
 * current packet.
 */
public final class PointThreeEstimator {

    public static final double MOVEMENT_THRESHOLD = 0.03D;

    private final MovementPlayer player;

    public PointThreeEstimator(MovementPlayer player) {
        this.player = player;
    }

    /**
     * @param positionIncluded whether the current packet actually carried a position
     * @return true if the client could legitimately have skipped a sub-threshold tick
     */
    public boolean determineCanSkipTick(boolean positionIncluded) {
        if (!positionIncluded) {
            return true;
        }
        // Near-stationary carried velocity: a skipped sub-0.03 tick is plausible.
        double horizontal = Math.hypot(player.clientVelocity.getX(), player.clientVelocity.getZ());
        double vertical = Math.abs(player.clientVelocity.getY());
        if (horizontal < MOVEMENT_THRESHOLD && vertical < MOVEMENT_THRESHOLD) {
            return true;
        }
        // Fluids, climbables, webs, slime and bouncy surfaces routinely produce sub-threshold motion.
        if (player.inWater || player.inLava || player.onClimbable || player.inWeb) {
            return true;
        }
        if (player.onSlime || player.uncertaintyHandler.influencedByBouncyBlock) {
            return true;
        }
        if (player.uncertaintyHandler.stepUpTick || player.uncertaintyHandler.slabEdgeTick) {
            return true;
        }
        if (player.uncertaintyHandler.blockChangeTicks > 0) {
            return true;
        }
        // Recent teleport: client may resync with a large position jump split across skipped ticks.
        if (player.uncertaintyHandler.lastTeleportTicks <= 2) {
            return true;
        }
        // Head-hitter / piston-like vertical bounce: small carried Y with horizontal motion.
        if (vertical < MOVEMENT_THRESHOLD * 2.0D && player.uncertaintyHandler.collidedHorizontally) {
            return true;
        }
        return false;
    }

    /** Whether a zero-displacement candidate should be injected into prediction this tick. */
    public boolean shouldInjectZeroMovement() {
        return determineCanSkipTick(true);
    }
}
