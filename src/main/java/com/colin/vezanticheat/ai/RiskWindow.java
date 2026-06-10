package com.colin.vezanticheat.ai;

/**
 * Pure (Bukkit-free) rolling-window math for {@link RiskScoreManager}.
 *
 * The risk store decays a player's accumulated score linearly over time and evicts a player's
 * state once it has both decayed to (effectively) zero AND gone stale, so the store does not
 * accumulate per-player entries forever. Splitting this out keeps the math unit-testable.
 */
public final class RiskWindow {

    /** Scores at/under this are treated as fully decayed (avoids float dust pinning entries alive). */
    public static final double ZERO_EPSILON = 1.0E-4D;

    private RiskWindow() {}

    /**
     * Decay a score linearly toward zero.
     *
     * @param score          current score
     * @param lastUpdateMs   wall-clock ms of the last score update (<= 0 means "never", no decay)
     * @param now            current wall-clock ms
     * @param decayPerSecond points removed per elapsed second (clamped to >= 0)
     * @return the decayed, non-negative score
     */
    public static double decay(double score, long lastUpdateMs, long now, double decayPerSecond) {
        if (lastUpdateMs <= 0L) return Math.max(0.0D, score);
        long elapsed = now - lastUpdateMs;
        if (elapsed <= 0L) return Math.max(0.0D, score);
        double rate = Math.max(0.0D, decayPerSecond);
        double removed = (elapsed / 1000.0D) * rate;
        double next = score - removed;
        return next <= ZERO_EPSILON ? 0.0D : next;
    }

    /**
     * Whether a player's state should be evicted: it has decayed to ~zero and the last update is
     * older than the retention window (so a player who keeps flagging is never evicted mid-stream).
     *
     * @param score        the (already-decayed) score
     * @param lastUpdateMs wall-clock ms of the last update (<= 0 means "never seen" -> evict)
     * @param now          current wall-clock ms
     * @param windowMs     retention window in ms (clamped to >= 0)
     */
    public static boolean shouldEvict(double score, long lastUpdateMs, long now, long windowMs) {
        if (score > ZERO_EPSILON) return false;
        if (lastUpdateMs <= 0L) return true;
        long age = now - lastUpdateMs;
        return age >= Math.max(0L, windowMs);
    }
}
