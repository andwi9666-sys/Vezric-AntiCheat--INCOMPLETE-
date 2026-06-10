package com.colin.vezanticheat.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CombatAnalyzerKnockbackLeniencyTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final long ATTACK_TIME = 1000L;

    private World world;
    private CombatAnalyzer analyzer;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        analyzer = new CombatAnalyzer();
    }

    @Test
    public void attackerKnockbackLeniencyReducesAimScoreOnly() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(-65.0F, 0.0F, 100L + i));
        }

        CombatSample sample = facingTargetSample(2.5D, -90.0F, 0.0F, rotations);
        analyzer.markCombatDamage(ATTACKER, ATTACK_TIME - 100L);

        CombatHitResult geometry = CombatHitClassifier.classify(
                sample, analyzer.getConfig(), null, null);
        CombatHitResult merged = analyzer.analyzeHit(sample);

        Assert.assertNotNull(geometry);
        Assert.assertNotNull(merged);
        Assert.assertEquals(0.0D, geometry.getFinalScore(), 0.001D);
        Assert.assertTrue(merged.getReasons().contains("recent knockback leniency applied"));

        double geometryScore = CombatScoreComposer.computeGeometryScore(
                merged.getExpansionTier(),
                merged.getClassification(),
                merged.getPing(),
                merged.isPingCompensated(),
                merged.isLineOfSightValid());
        double behaviorScore = CombatScoreComposer.sumBehaviorScore(
                merged.getPreAimResult(),
                merged.getAccuracySpikeResult(),
                merged.getAimCorrelationResult(),
                merged.getTargetSwitchResult());
        double expected = CombatScoreComposer.computeFinalScore(
                geometryScore,
                behaviorScore,
                merged.getClassification(),
                merged.getExpansionTier(),
                true,
                analyzer.getConfig());
        Assert.assertEquals(expected, merged.getFinalScore(), 0.001D);
        Assert.assertTrue(merged.getBaseScore() >= merged.getFinalScore());
    }

    @Test
    public void impossibleHitUnchangedByAttackerKnockbackLeniency() {
        analyzer.markCombatDamage(ATTACKER, ATTACK_TIME - 50L);

        CombatHitResult result = analyzer.analyzeHit(facingTargetSample(-5.0D, -90.0F, 0.0F, null));

        Assert.assertNotNull(result);
        Assert.assertEquals(CombatHitClassification.IMPOSSIBLE, result.getClassification());
        Assert.assertEquals(5.0D, result.getFinalScore(), 0.001D);
        Assert.assertFalse(result.getReasons().contains("recent knockback leniency applied"));
    }

    @Test
    public void targetKnockbackLeniencySoftensBorderlineReach() {
        CombatSample sample = facingTargetSample(3.42D, -90.0F, 0.0F, null);
        analyzer.markCombatDamage(TARGET, ATTACK_TIME - 80L);

        CombatHitResult withoutLeniency = CombatHitClassifier.classify(
                sample, analyzer.getConfig(), null, null);
        CombatHitResult withLeniency = CombatHitClassifier.classify(
                sample,
                analyzer.getConfig(),
                null,
                analyzer.getCombatData(TARGET));

        Assert.assertNotNull(withoutLeniency);
        Assert.assertNotNull(withLeniency);
        Assert.assertEquals(CombatHitClassification.LENIENT, withoutLeniency.getClassification());
        Assert.assertEquals(CombatHitClassification.CLEAN, withLeniency.getClassification());
        Assert.assertTrue(withLeniency.getReasons().contains("target knockback leniency applied"));
    }

    @Test
    public void targetKnockbackLeniencyDoesNotAllowImpossibleReach() {
        CombatSample sample = facingTargetSample(-5.0D, -90.0F, 0.0F, null);
        analyzer.markCombatDamage(TARGET, ATTACK_TIME - 80L);

        CombatHitResult result = CombatHitClassifier.classify(
                sample,
                analyzer.getConfig(),
                null,
                analyzer.getCombatData(TARGET));

        Assert.assertNotNull(result);
        Assert.assertEquals(CombatHitClassification.IMPOSSIBLE, result.getClassification());
        Assert.assertTrue(result.getReasons().contains("target knockback leniency applied"));
    }

    private CombatSample facingTargetSample(double targetX, float yaw, float pitch,
                                            List<CombatSample.RotationPoint> rotations) {
        CombatSample.Builder builder = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, targetX, 0.0D, 0.0D))
                .attackerYaw(yaw)
                .attackerPitch(pitch)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(30)
                .timestampMs(ATTACK_TIME);
        if (rotations != null) {
            builder.recentRotations(rotations);
        }
        return builder.build();
    }
}
