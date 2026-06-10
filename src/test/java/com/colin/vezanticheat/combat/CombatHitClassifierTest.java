package com.colin.vezanticheat.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class CombatHitClassifierTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");

    private World world;
    private CombatConfig config;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        config = CombatConfig.defaults();
    }

    @Test
    public void closeRangeFacingHitIsClean() {
        CombatSample sample = sampleFacingTarget(2.5D, -90.0F, 0.0F, 30);
        CombatHitResult result = CombatHitClassifier.classify(sample, config);
        Assert.assertNotNull(result);
        Assert.assertEquals(CombatHitClassification.CLEAN, result.getClassification());
        Assert.assertTrue(result.getReachDistance() <= config.getMaxNormalReach());
        Assert.assertTrue(result.isNormalHitboxHit());
        Assert.assertEquals(HitboxExpansionTier.NORMAL, result.getExpansionTier());
        Assert.assertFalse(result.isPingCompensated());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertNotNull(result.getHitPoint());
        Assert.assertNotNull(result.getHitPointData());
        Assert.assertFalse(result.getHitPointData().isExpansionShellHit());
    }

    @Test
    public void farRangeMissIsImpossible() {
        CombatSample sample = sampleFacingTarget(-5.0D, -90.0F, 0.0F, 30);
        CombatHitResult result = CombatHitClassifier.classify(sample, config);
        Assert.assertNotNull(result);
        Assert.assertEquals(CombatHitClassification.IMPOSSIBLE, result.getClassification());
        Assert.assertEquals(5.0D, result.getFinalScore(), 0.001D);
        Assert.assertEquals(HitboxExpansionTier.MISS, result.getExpansionTier());
        Assert.assertFalse(result.isNormalHitboxHit());
        Assert.assertTrue(result.getReachDistance() > config.getMaxBadReach());
        Assert.assertNull(result.getHitPoint());
        Assert.assertNull(result.getHitPointData());
    }

    @Test
    public void blockedLineOfSightDoesNotForceImpossible() {
        Player attacker = Mockito.mock(Player.class);
        Player target = Mockito.mock(Player.class);
        Mockito.when(attacker.getWorld()).thenReturn(world);
        Mockito.when(target.getWorld()).thenReturn(world);
        Mockito.when(attacker.hasLineOfSight(target)).thenReturn(false);

        CombatSample sample = CombatSample.builder()
                .attacker(attacker)
                .target(target)
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(eyeFacing(3.5D))
                .targetLocation(new Location(world, 3.5D, 0.0D, 0.0D))
                .attackerYaw(-90.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(30)
                .timestampMs(100L)
                .build();

        CombatHitResult result = CombatHitClassifier.classify(sample, config);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isLineOfSightValid());
        Assert.assertNotEquals(CombatHitClassification.IMPOSSIBLE, result.getClassification());
        Assert.assertEquals(CombatHitClassification.LENIENT, result.getClassification());
    }

    @Test
    public void onlyFullExpansionHitIsVeryLenientWithLowPingSuspicion() {
        CombatSample sample = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.6D, 0.0D, 0.0D))
                .attackerYaw(-83.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(30)
                .timestampMs(50L)
                .build();

        CombatHitResult result = CombatHitClassifier.classify(sample, config);
        Assert.assertNotNull(result);
        Assert.assertFalse(result.isNormalHitboxHit());
        Assert.assertFalse(result.isSmallExpansionHit());
        Assert.assertTrue(result.isFullExpansionHit());
        Assert.assertEquals(HitboxExpansionTier.FULL, result.getExpansionTier());
        Assert.assertEquals(CombatHitClassification.VERY_LENIENT, result.getClassification());
        Assert.assertEquals(2.5D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isPingCompensated());
        Assert.assertTrue(result.getReasons().contains("Low ping player required expanded hitbox"));
        Assert.assertTrue(result.getReasons().contains("Hit only connected inside full +0.10 expansion"));
        Assert.assertNotNull(result.getHitPointData());
        Assert.assertTrue(result.getHitPointData().isExpansionShellHit());
        Assert.assertTrue(result.getReasons().contains("hitPoint=expansionShell"));
    }

    @Test
    public void highPingFullExpansionHitGetsLeniencyDiscount() {
        CombatSample sample = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.6D, 0.0D, 0.0D))
                .attackerYaw(-83.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(200)
                .timestampMs(50L)
                .build();

        CombatHitResult result = CombatHitClassifier.classify(sample, config);
        Assert.assertNotNull(result);
        Assert.assertEquals(HitboxExpansionTier.FULL, result.getExpansionTier());
        Assert.assertEquals(CombatHitClassification.VERY_LENIENT, result.getClassification());
        Assert.assertEquals(1.5D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.isPingCompensated());
        Assert.assertTrue(result.getReasons().contains("High ping expansion leniency applied (-30% suspicion)"));
        Assert.assertTrue(result.getReasons().contains("Hit accepted within ping-allowed expansion"));
    }

    @Test
    public void pingRemappedTierWhenAllowedExpansionExceedsFixedFull() {
        CombatConfig expandedConfig = CombatConfig.defaults();
        expandedConfig.setPingExpansionMid(0.11D);
        expandedConfig.setPingThresholdMid(200);

        CombatSample sample = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.62D, 0.0D, 0.0D))
                .rewoundValid(true)
                .attackerYaw(-83.5F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(80)
                .timestampMs(50L)
                .build();

        CombatHitResult result = CombatHitClassifier.classify(sample, expandedConfig);
        Assert.assertNotNull(result);
        Assert.assertEquals(0.11D, result.getAllowedExpansion(), 0.001D);
        Assert.assertEquals(HitboxExpansionTier.FULL, result.getExpansionTier());
        Assert.assertTrue(result.isPingCompensated());
        Assert.assertTrue(result.getReasons().contains("Hit accepted within ping-allowed expansion"));
    }

    @Test
    public void invalidRewindAddsLiveFallbackExpansionBonus() {
        CombatSample liveFallback = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.15D, 0.0D, 0.0D))
                .rewoundValid(false)
                .attackerYaw(-88.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(80)
                .timestampMs(50L)
                .build();
        CombatSample rewound = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.15D, 0.0D, 0.0D))
                .rewoundTargetLocation(new Location(world, 3.15D, 0.0D, 0.0D))
                .rewoundValid(true)
                .attackerYaw(-88.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(80)
                .timestampMs(50L)
                .build();

        CombatHitResult live = CombatHitClassifier.classify(liveFallback, config);
        CombatHitResult rewind = CombatHitClassifier.classify(rewound, config);
        Assert.assertNotNull(live);
        Assert.assertNotNull(rewind);
        Assert.assertTrue(live.getAllowedExpansion() > rewind.getAllowedExpansion());
        Assert.assertEquals(config.getLivePositionFallbackExpansionBonus(),
                live.getAllowedExpansion() - rewind.getAllowedExpansion(), 0.001D);
    }

    @Test
    public void analyzerCancelOnImpossibleAndBuffer() {
        CombatAnalyzer analyzer = new CombatAnalyzer();
        CombatSample sample = sampleFacingTarget(-5.0D, -90.0F, 0.0F, 30);
        CombatHitResult result = analyzer.analyzeHit(sample);
        Assert.assertNotNull(result);
        Assert.assertEquals(CombatHitClassification.IMPOSSIBLE, result.getClassification());
        Assert.assertTrue(analyzer.shouldCancelHit(ATTACKER, result));

        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        for (int i = 0; i < 5; i++) {
            analyzer.recordResult(CombatHitResult.builder()
                    .attackerUuid(ATTACKER)
                    .targetUuid(TARGET)
                    .classification(CombatHitClassification.BAD)
                    .suspiciousScore(2.0D)
                    .build());
        }
        CombatHitResult badHit = CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.BAD)
                .finalScore(3.0D)
                .build();
        Assert.assertTrue(evidence.shouldCancelHit(badHit, true));
        Assert.assertTrue(analyzer.shouldCancelHit(ATTACKER, badHit));
    }

    private CombatSample sampleFacingTarget(double targetX, float yaw, float pitch, int ping) {
        return CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(eyeFacing(targetX))
                .targetLocation(new Location(world, targetX, 0.0D, 0.0D))
                .attackerYaw(yaw)
                .attackerPitch(pitch)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(ping)
                .timestampMs(50L)
                .build();
    }

    private Location eyeFacing(double targetX) {
        Location eye = new Location(world, 0.0D, 1.62D, 0.0D);
        eye.setYaw(-90.0F);
        eye.setPitch(0.0F);
        return eye;
    }
}
