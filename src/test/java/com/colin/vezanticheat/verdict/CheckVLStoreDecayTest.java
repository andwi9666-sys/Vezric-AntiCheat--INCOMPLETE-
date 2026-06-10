package com.colin.vezanticheat.verdict;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CheckVLStoreDecayTest {

    private static final String CHECK = "DecayTestCheck"; // maps to itself (no Prism pool alias)
    private static final long GRACE_MS = 15_000L;

    @Test
    public void noDecayDuringGraceWindow() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 1_000_000L;
        store.addVl(id, CHECK, 10.0D, t0);

        // 1s later, still inside grace — VL must not move.
        double vl = store.tickDecay(id, CHECK, 0.5D, GRACE_MS, t0 + 1_000L);
        assertEquals(10.0D, vl, 0.0001D);
        assertEquals(10.0D, store.getVl(id, CHECK), 0.0001D);
    }

    @Test
    public void decaysAtRateAfterGrace() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 1_000_000L;
        store.addVl(id, CHECK, 10.0D, t0);

        // First tick exactly at grace boundary establishes the decay baseline (no time has elapsed
        // since the change beyond grace boundary, so the decay reference becomes lastChangeMs).
        long tGrace = t0 + GRACE_MS;
        double afterFirst = store.tickDecay(id, CHECK, 0.5D, GRACE_MS, tGrace);
        // since-reference is lastChangeMs (t0); elapsed = 15s -> decays 15 * 0.5 = 7.5
        assertEquals(2.5D, afterFirst, 0.0001D);

        // 2s further at 0.5/s -> 1.0 decay.
        double afterSecond = store.tickDecay(id, CHECK, 0.5D, GRACE_MS, tGrace + 2_000L);
        assertEquals(1.5D, afterSecond, 0.0001D);
    }

    @Test
    public void decayIsTimeBasedBetweenTicks() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 0L;
        store.addVl(id, CHECK, 5.0D, t0);

        long start = t0 + GRACE_MS;
        // Prime the decay baseline at the grace boundary.
        store.tickDecay(id, CHECK, 0.0001D, GRACE_MS, start); // negligible rate to set lastDecayMs
        double primed = store.getVl(id, CHECK);

        // 4s at 0.25/s -> 1.0 decay from primed value.
        double after = store.tickDecay(id, CHECK, 0.25D, GRACE_MS, start + 4_000L);
        assertEquals(primed - 1.0D, after, 0.0005D);
    }

    @Test
    public void floorsAtZeroAndCleansUpEntry() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 0L;
        store.addVl(id, CHECK, 2.0D, t0);

        long t = t0 + GRACE_MS + 100_000L; // huge elapsed -> fully decays past zero
        double vl = store.tickDecay(id, CHECK, 1.0D, GRACE_MS, t);
        assertEquals(0.0D, vl, 0.0001D);
        assertEquals(0.0D, store.getVl(id, CHECK), 0.0001D);

        // Entry should have been removed; a subsequent decay tick on an absent pool returns 0.
        assertEquals(0.0D, store.tickDecay(id, CHECK, 1.0D, GRACE_MS, t + 5_000L), 0.0001D);
    }

    @Test
    public void zeroRateIsNoOp() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 0L;
        store.addVl(id, CHECK, 4.0D, t0);
        double vl = store.tickDecay(id, CHECK, 0.0D, GRACE_MS, t0 + GRACE_MS + 10_000L);
        assertEquals(4.0D, vl, 0.0001D);
    }

    @Test
    public void absentPoolReturnsZero() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        assertEquals(0.0D, store.tickDecay(id, CHECK, 0.5D, GRACE_MS, 50_000L), 0.0001D);
    }

    @Test
    public void newFlagDuringDecayResetsGrace() {
        CheckVLStore store = new CheckVLStore();
        UUID id = UUID.randomUUID();
        long t0 = 0L;
        store.addVl(id, CHECK, 10.0D, t0);

        // Decay past grace once.
        store.tickDecay(id, CHECK, 0.5D, GRACE_MS, t0 + GRACE_MS + 2_000L);
        double afterDecay = store.getVl(id, CHECK);
        assertTrue(afterDecay < 10.0D);

        // A new flag bumps VL and re-stamps lastChangeMs -> grace re-applies.
        long flag2 = t0 + GRACE_MS + 3_000L;
        double bumped = store.addVl(id, CHECK, 3.0D, flag2);
        double held = store.tickDecay(id, CHECK, 0.5D, GRACE_MS, flag2 + 1_000L);
        assertEquals("decay must not run during fresh grace window", bumped, held, 0.0001D);
    }
}
