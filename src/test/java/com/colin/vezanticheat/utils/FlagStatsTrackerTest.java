package com.colin.vezanticheat.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class FlagStatsTrackerTest {

    @Test
    public void emptyTrackerHasNoData() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        Assert.assertTrue(tracker.summarize().isEmpty());
        Assert.assertEquals(0, tracker.totalFlagsLastHour());
        Assert.assertTrue(tracker.playersFlaggedLastHour().isEmpty());
    }

    @Test
    public void singlePlayerFlagsAggregate() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            tracker.record("PrismReachA", player, 100, 19.5D, false);
        }
        List<FlagStatsTracker.CheckSummary> summaries = tracker.summarize();
        Assert.assertEquals(1, summaries.size());
        FlagStatsTracker.CheckSummary s = summaries.get(0);
        Assert.assertEquals("PrismReachA", s.check);
        Assert.assertEquals(5, s.flags);
        Assert.assertEquals(0, s.shadowFlags);
        Assert.assertEquals(1, s.distinctPlayers);
        Assert.assertEquals(player, s.topPlayer);
        Assert.assertEquals(5, s.topPlayerFlags);
        Assert.assertEquals(5, tracker.totalFlagsLastHour());
    }

    @Test
    public void summariesSortedByFlagsDescending() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        tracker.record("CheckLow", a, -1, -1.0D, false);
        for (int i = 0; i < 3; i++) {
            tracker.record("CheckHigh", a, -1, -1.0D, false);
            tracker.record("CheckHigh", b, -1, -1.0D, false);
        }
        List<FlagStatsTracker.CheckSummary> summaries = tracker.summarize();
        Assert.assertEquals(2, summaries.size());
        Assert.assertEquals("CheckHigh", summaries.get(0).check);
        Assert.assertEquals(6, summaries.get(0).flags);
        Assert.assertEquals(2, summaries.get(0).distinctPlayers);
        Assert.assertEquals("CheckLow", summaries.get(1).check);
    }

    @Test
    public void shadowFlagsCountInBothTotals() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        tracker.record("CharSilentAim", UUID.randomUUID(), -1, -1.0D, true);
        tracker.record("CharSilentAim", UUID.randomUUID(), -1, -1.0D, false);
        FlagStatsTracker.CheckSummary s = tracker.summarize().get(0);
        Assert.assertEquals(2, s.flags);
        Assert.assertEquals(1, s.shadowFlags);
    }

    @Test
    public void pingAverageIgnoresUnknownValues() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        UUID player = UUID.randomUUID();
        tracker.record("PrismReachA", player, 100, -1.0D, false);
        tracker.record("PrismReachA", player, 200, -1.0D, false);
        tracker.record("PrismReachA", player, -1, -1.0D, false); // unknown ping excluded
        FlagStatsTracker.CheckSummary s = tracker.summarize().get(0);
        Assert.assertEquals(150.0D, s.avgPing, 0.001D);
        Assert.assertEquals(-1.0D, s.avgTps, 0.001D); // never provided
    }

    @Test
    public void clearEmptiesEverything() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        tracker.record("X", UUID.randomUUID(), 50, 20.0D, false);
        tracker.clear();
        Assert.assertTrue(tracker.summarize().isEmpty());
        Assert.assertEquals(0, tracker.totalFlagsLastHour());
    }

    @Test
    public void perBucketPlayerTrackingIsBounded() {
        FlagStatsTracker tracker = new FlagStatsTracker();
        for (int i = 0; i < 70; i++) {
            tracker.record("Flood", UUID.randomUUID(), -1, -1.0D, false);
        }
        FlagStatsTracker.CheckSummary s = tracker.summarize().get(0);
        Assert.assertEquals(70, s.flags); // flag count is exact
        Assert.assertTrue("distinct players capped at 64, was " + s.distinctPlayers,
                s.distinctPlayers <= 64);
    }
}
