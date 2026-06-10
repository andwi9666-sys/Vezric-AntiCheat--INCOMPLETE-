package com.colin.vezanticheat.utils;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SetbackRateLimiterTest {

    @Test
    public void allowsUpToCapacityWithinWindow() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(3, 2000L);
        UUID id = UUID.randomUUID();
        long t = 1_000L;
        assertTrue(limiter.tryAcquire(id, t));
        assertTrue(limiter.tryAcquire(id, t));
        assertTrue(limiter.tryAcquire(id, t));
    }

    @Test
    public void deniesWhenBucketExhausted() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(3, 2000L);
        UUID id = UUID.randomUUID();
        long t = 1_000L;
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        assertFalse("4th setback within window must be denied", limiter.tryAcquire(id, t));
    }

    @Test
    public void refillsOverWindow() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(3, 2000L);
        UUID id = UUID.randomUUID();
        long t = 1_000L;
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        assertFalse(limiter.tryAcquire(id, t));

        // After ~1/3 of the window, ~1 token should be back.
        long later = t + 700L;
        assertTrue("one token should refill after ~1/3 window", limiter.tryAcquire(id, later));
        assertFalse("only one token refilled", limiter.tryAcquire(id, later));
    }

    @Test
    public void fullRefillAfterCompleteWindow() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(3, 2000L);
        UUID id = UUID.randomUUID();
        long t = 1_000L;
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        assertFalse(limiter.tryAcquire(id, t));

        long full = t + 2000L;
        assertEquals(3.0D, limiter.availableTokens(id, full), 0.001D);
        assertTrue(limiter.tryAcquire(id, full));
        assertTrue(limiter.tryAcquire(id, full));
        assertTrue(limiter.tryAcquire(id, full));
        assertFalse(limiter.tryAcquire(id, full));
    }

    @Test
    public void doesNotOverfillBeyondCapacity() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(3, 2000L);
        UUID id = UUID.randomUUID();
        limiter.tryAcquire(id, 1_000L);
        // Long idle should cap at capacity, not accumulate unbounded tokens.
        assertEquals(3.0D, limiter.availableTokens(id, 1_000L + 1_000_000L), 0.001D);
    }

    @Test
    public void independentBucketsPerPlayer() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(2, 2000L);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        long t = 500L;
        assertTrue(limiter.tryAcquire(a, t));
        assertTrue(limiter.tryAcquire(a, t));
        assertFalse(limiter.tryAcquire(a, t));
        // b is untouched.
        assertTrue(limiter.tryAcquire(b, t));
        assertTrue(limiter.tryAcquire(b, t));
        assertFalse(limiter.tryAcquire(b, t));
    }

    @Test
    public void resetRestoresFullBucket() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(2, 2000L);
        UUID id = UUID.randomUUID();
        long t = 100L;
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        assertFalse(limiter.tryAcquire(id, t));
        limiter.reset(id);
        assertTrue(limiter.tryAcquire(id, t));
    }

    @Test
    public void clockGoingBackwardsDoesNotGrantTokens() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(2, 2000L);
        UUID id = UUID.randomUUID();
        long t = 5_000L;
        limiter.tryAcquire(id, t);
        limiter.tryAcquire(id, t);
        assertFalse(limiter.tryAcquire(id, t));
        // Earlier timestamp must not refill.
        assertFalse(limiter.tryAcquire(id, t - 1000L));
    }

    @Test
    public void nullIdAlwaysAllowed() {
        SetbackRateLimiter limiter = new SetbackRateLimiter(1, 1000L);
        assertTrue(limiter.tryAcquire(null, 0L));
        assertTrue(limiter.tryAcquire(null, 0L));
    }
}
