package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class CombatFalsePositiveGuardTest {

    private static final UUID ATTACKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000022");

    private CombatConfig config;
    private PlayerData attackerData;
    private PlayerData targetData;

    @Before
    public void setUp() {
        config = CombatConfig.defaults();
        attackerData = new PlayerData(ATTACKER_ID);
        targetData = new PlayerData(TARGET_ID);
    }

    @Test
    public void joinWithinFiveSecondsSkipsAnalysis() {
        attackerData.markCombatJoin(1000L);
        Assert.assertTrue(CombatFalsePositiveGuard.shouldSkipCombatAnalysis(
                config, attackerData, null, 3000L, 900L));
    }

    @Test
    public void targetJoinWithinFiveSecondsSkipsAnalysis() {
        targetData.markCombatJoin(2000L);
        Assert.assertTrue(CombatFalsePositiveGuard.shouldSkipCombatAnalysis(
                config, null, targetData, 4000L, 900L));
    }

    @Test
    public void recentTeleportSkipsAnalysis() {
        long now = 5000L;
        attackerData.markTeleportExempt(900L);
        Assert.assertTrue(CombatFalsePositiveGuard.shouldSkipCombatAnalysis(
                config, attackerData, null, now, 900L));
    }

    @Test
    public void lowTpsReducesGlobalMultiplier() {
        CombatSample sample = sampleWithDistance(2.5D, 30, 30);
        double multiplier = CombatFalsePositiveGuard.getGlobalMultiplier(
                config, sample, null, null, 1000L, 900L, 18.0D);
        Assert.assertEquals(config.getFpTpsSuspicionMultiplier(), multiplier, 0.001D);
    }

    @Test
    public void highAttackerPingReducesBehaviorMultiplier() {
        CombatSample sample = sampleWithDistance(2.5D, 300, 30);
        double multiplier = CombatFalsePositiveGuard.getBehaviorMultiplier(null, sample);
        Assert.assertEquals(config.getFpAttackerPingAimMultiplier(), multiplier, 0.001D);
    }

    @Test
    public void highTargetPingReducesGeometryMultiplier() {
        CombatSample sample = sampleWithDistance(2.5D, 30, 300);
        double multiplier = CombatFalsePositiveGuard.getGeometryMultiplier(null, sample);
        Assert.assertEquals(config.getFpTargetPingReachMultiplier(), multiplier, 0.001D);
    }

    @Test
    public void closeRangeReducesBehaviorMultiplier() {
        CombatSample sample = sampleWithDistance(1.0D, 30, 30);
        double multiplier = CombatFalsePositiveGuard.getBehaviorMultiplier(null, sample);
        Assert.assertEquals(config.getFpCloseRangeAimMultiplier(), multiplier, 0.001D);
    }

    @Test
    public void lowTpsSuppressesAlerts() {
        Assert.assertTrue(CombatFalsePositiveGuard.shouldSuppressAlerts(17.0D, config));
    }

    @Test
    public void recentTeleportLenientOnImpossibleCancel() {
        long now = 10_000L;
        attackerData.markTeleportExempt(900L);
        CombatHitResult impossible = CombatHitResult.builder()
                .attackerUuid(ATTACKER_ID)
                .targetUuid(TARGET_ID)
                .classification(CombatHitClassification.IMPOSSIBLE)
                .finalScore(5.0D)
                .build();

        CombatCancelDecision decision = CombatCancelDecision.resolve(
                impossible, 0.0D, config, true, true);
        Assert.assertFalse(decision.shouldCancel());
    }

    @Test
    public void highPingDoesNotLenientImpossibleCancel() {
        CombatHitResult impossible = CombatHitResult.builder()
                .attackerUuid(ATTACKER_ID)
                .targetUuid(TARGET_ID)
                .classification(CombatHitClassification.IMPOSSIBLE)
                .finalScore(5.0D)
                .build();

        CombatCancelDecision decision = CombatCancelDecision.resolve(
                impossible, 0.0D, config, true, false);
        Assert.assertTrue(decision.shouldCancel());
    }

    @Test
    public void partialTeleportLeniencyAppliesMultiplier() {
        attackerData.markTeleportExempt(900L);
        long now = attackerData.getTeleportExemptUntilMs() - 100L;
        Assert.assertFalse(CombatFalsePositiveGuard.isWithinRecentTeleport(
                attackerData, now, config.getFpTeleportSkipMs(), 900L));
        Assert.assertTrue(CombatFalsePositiveGuard.isPartialTeleportLeniency(
                attackerData, now, config.getFpTeleportSkipMs(), 900L));
    }

    private static CombatSample sampleWithDistance(double distance, int attackerPing, int targetPing) {
        World world = Mockito.mock(World.class);
        return CombatSample.builder()
                .attackerUuid(ATTACKER_ID)
                .targetUuid(TARGET_ID)
                .attackerLocation(new Location(world, 0.0D, 64.0D, 0.0D))
                .targetLocation(new Location(world, distance, 64.0D, 0.0D))
                .pingEstimate(attackerPing)
                .targetPingEstimate(targetPing)
                .attackDistance(distance)
                .timestampMs(1000L)
                .build();
    }
}
