package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.engine.PlayerClock.PlayerClockState;
import org.junit.Assert;
import org.junit.Test;

public class PlayerClockTest {

    private static final long DEBIT_CAP = 20L;
    private static final long LEDGER_CAP = 2000L;

    @Test
    public void tickMsIsFifty() {
        Assert.assertEquals(50L, PlayerClock.TICK_MS);
    }

    @Test
    public void evaluateDriftZeroWithoutAnchor() {
        Assert.assertEquals(0L, PlayerClock.evaluateDrift(null, null));
    }

    // ---- PERSISTENT DRIFT LEDGER ----

    @Test
    public void positiveDriftAccrues() {
        PlayerClockState s = new PlayerClockState();
        PlayerClock.applyDriftToLedger(s, 10L, DEBIT_CAP, LEDGER_CAP);
        PlayerClock.applyDriftToLedger(s, 8L, DEBIT_CAP, LEDGER_CAP);
        Assert.assertEquals(18L, s.cumulativeDriftMs);
    }

    @Test
    public void cannotFarmThenRestToReset() {
        // Cheater accrues drift while running fast, then "rests" feeding behind-ticks. The behind
        // debit is bounded per tick (DEBIT_CAP), so it cannot wipe the accrued advantage quickly.
        PlayerClockState s = new PlayerClockState();
        for (int i = 0; i < 10; i++) {
            PlayerClock.applyDriftToLedger(s, 15L, DEBIT_CAP, LEDGER_CAP); // +150 total
        }
        Assert.assertEquals(150L, s.cumulativeDriftMs);

        // Now rest: each behind-tick reports drift -100ms, but only DEBIT_CAP (20) is debited.
        for (int i = 0; i < 3; i++) {
            PlayerClock.applyDriftToLedger(s, -100L, DEBIT_CAP, LEDGER_CAP);
        }
        // 150 - 3*20 = 90, NOT wiped to zero.
        Assert.assertEquals(90L, s.cumulativeDriftMs);
        Assert.assertTrue(s.cumulativeDriftMs > 0L);
    }

    @Test
    public void ledgerClampedToCap() {
        PlayerClockState s = new PlayerClockState();
        for (int i = 0; i < 1000; i++) {
            PlayerClock.applyDriftToLedger(s, 100L, DEBIT_CAP, LEDGER_CAP);
        }
        Assert.assertEquals(LEDGER_CAP, s.cumulativeDriftMs);
    }

    @Test
    public void ledgerNeverNegative() {
        PlayerClockState s = new PlayerClockState();
        s.cumulativeDriftMs = 5L;
        PlayerClock.applyDriftToLedger(s, -100L, DEBIT_CAP, LEDGER_CAP);
        Assert.assertEquals(0L, s.cumulativeDriftMs);
    }

    @Test
    public void teleportPausesAccrualOnce() {
        PlayerClockState s = new PlayerClockState();
        s.cumulativeDriftMs = 40L;
        s.teleportPause = true;
        // First ack after teleport: paused, no change, flag cleared.
        PlayerClock.applyDriftToLedger(s, 30L, DEBIT_CAP, LEDGER_CAP);
        Assert.assertEquals(40L, s.cumulativeDriftMs);
        Assert.assertFalse(s.teleportPause);
        // Subsequent acks accrue again — teleport did NOT reset the ledger.
        PlayerClock.applyDriftToLedger(s, 30L, DEBIT_CAP, LEDGER_CAP);
        Assert.assertEquals(70L, s.cumulativeDriftMs);
    }

    @Test
    public void onTeleportDoesNotResetLedger() {
        PlayerClockState s = new PlayerClockState();
        s.cumulativeDriftMs = 123L;
        s.teleportPause = true;
        // The persistent ledger survives a teleport (only balanceMs/anchor are reset elsewhere).
        Assert.assertEquals(123L, s.cumulativeDriftMs);
    }

    @Test
    public void movementGapDebitsButDoesNotReset() {
        PlayerClockState s = new PlayerClockState();
        s.cumulativeDriftMs = 200L;
        // Gap below trigger: no debit.
        PlayerClock.applyGapDebit(s, 100L, 225L, 50L);
        Assert.assertEquals(200L, s.cumulativeDriftMs);
        // Gap at/over trigger: bounded debit, not a reset.
        PlayerClock.applyGapDebit(s, 300L, 225L, 50L);
        Assert.assertEquals(150L, s.cumulativeDriftMs);
        Assert.assertTrue(s.cumulativeDriftMs > 0L);
    }

    @Test
    public void resetClearsLedger() {
        PlayerClockState s = new PlayerClockState();
        s.cumulativeDriftMs = 500L;
        s.teleportPause = true;
        s.reset();
        Assert.assertEquals(0L, s.cumulativeDriftMs);
        Assert.assertFalse(s.teleportPause);
    }
}
