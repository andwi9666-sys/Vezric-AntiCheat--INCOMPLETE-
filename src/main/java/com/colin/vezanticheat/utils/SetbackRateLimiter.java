package com.colin.vezanticheat.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player token-bucket rate limiter for setbacks.
 *
 * <p>Pure (Bukkit-free) so it can be unit tested. Each player has a bucket that holds at most
 * {@code capacity} tokens and refills linearly over {@code refillWindowMs}. A setback consumes one
 * token; when the bucket is empty the setback must be denied (the caller should hard-freeze the
 * player via SetbackBlocker instead of teleporting again, which prevents a setback loop).
 *
 * <p>Refill is computed lazily on each {@link #tryAcquire(UUID, long)} call from the elapsed time
 * since the last refill stamp — no background task is required.
 */
public final class SetbackRateLimiter {

    private static final class Bucket {
        double tokens;
        long lastRefillMs;
    }

    private final int capacity;
    private final long refillWindowMs;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<UUID, Bucket>();

    /**
     * @param capacity       maximum setbacks allowed within one full {@code refillWindowMs}
     * @param refillWindowMs window over which the bucket refills from empty to full
     */
    public SetbackRateLimiter(int capacity, long refillWindowMs) {
        this.capacity = Math.max(1, capacity);
        this.refillWindowMs = Math.max(1L, refillWindowMs);
    }

    public int capacity() { return capacity; }

    public long refillWindowMs() { return refillWindowMs; }

    /**
     * Attempt to consume one token for the given player.
     *
     * @return {@code true} if a setback is allowed (token consumed); {@code false} if the bucket is
     *         exhausted and the player should be hard-frozen instead.
     */
    public boolean tryAcquire(UUID id, long nowMs) {
        if (id == null) return true;
        Bucket b = bucketOrCreate(id, nowMs);
        refill(b, nowMs);
        if (b.tokens >= 1.0D) {
            b.tokens -= 1.0D;
            return true;
        }
        return false;
    }

    /** Current (lazily-refilled) token count for diagnostics/tests. */
    public double availableTokens(UUID id, long nowMs) {
        Bucket b = buckets.get(id);
        if (b == null) return capacity;
        refill(b, nowMs);
        return b.tokens;
    }

    public void reset(UUID id) {
        if (id != null) buckets.remove(id);
    }

    public void clear() {
        buckets.clear();
    }

    private Bucket bucketOrCreate(UUID id, long nowMs) {
        Bucket b = buckets.get(id);
        if (b == null) {
            b = new Bucket();
            b.tokens = capacity;
            b.lastRefillMs = nowMs;
            buckets.put(id, b);
        }
        return b;
    }

    private void refill(Bucket b, long nowMs) {
        if (nowMs <= b.lastRefillMs) {
            // Clock went backwards or no time elapsed — only advance the stamp forward.
            if (nowMs > b.lastRefillMs) b.lastRefillMs = nowMs;
            return;
        }
        long elapsed = nowMs - b.lastRefillMs;
        double refillRatePerMs = (double) capacity / (double) refillWindowMs;
        b.tokens = Math.min((double) capacity, b.tokens + elapsed * refillRatePerMs);
        b.lastRefillMs = nowMs;
    }
}
