package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
import com.colin.vezanticheat.combat.CombatHitClassifier;
import com.colin.vezanticheat.combat.CombatHitResult;
import com.colin.vezanticheat.combat.CombatSample;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AccuracySpikeAnalyzerTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final long ATTACK_TIME = 1000L;

    private World world;
    private CombatConfig config;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        config = CombatConfig.defaults();
    }

    @Test
    public void noHistory() {
        AccuracySpikeResult result = AccuracySpikeAnalyzer.analyze(
                sample(-90.0F, 0.0F, null), null, config);
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSpikeDetected());
        Assert.assertTrue(result.getReasons().contains("accuracySpike=no_history"));
    }

    @Test
    public void consistentTracking() {
        List<CombatSample.RotationPoint> rotations = rotationsFacing(-90.0F, 5);
        AccuracySpikeResult result = AccuracySpikeAnalyzer.analyze(
                sample(-90.0F, 0.0F, rotations), hitResult(-90.0F, 0.0F), config);

        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSpikeDetected());
        Assert.assertTrue(result.getReasons().contains("accuracySpike=consistent_tracking"));
    }

    @Test
    public void moderateSpike() {
        List<CombatSample.RotationPoint> rotations = rotationsFacing(-75.0F, 5);
        CombatHitResult current = hitResult(-90.0F, 0.0F);

        AccuracySpikeResult result = AccuracySpikeAnalyzer.analyze(
                sample(-90.0F, 0.0F, rotations), current, config);

        Assert.assertTrue(result.isSpikeDetected());
        Assert.assertEquals(1.5D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getAverageTrackingYawError() >= 15.0D);
        Assert.assertTrue(result.getAverageTrackingYawError() < 25.0D);
        Assert.assertTrue(result.getAttackYawError() <= 3.0D);
        Assert.assertTrue(result.getReasons().contains(
                "Accuracy spike: poor tracking before attack, perfect on hit"));
    }

    @Test
    public void strongSpike() {
        List<CombatSample.RotationPoint> rotations = rotationsFacing(0.0F, 5);
        CombatHitResult current = hitResult(-90.0F, 0.0F);

        AccuracySpikeResult result = AccuracySpikeAnalyzer.analyze(
                sample(-90.0F, 0.0F, rotations), current, config);

        Assert.assertTrue(result.isSpikeDetected());
        Assert.assertEquals(2.25D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getAverageTrackingYawError() >= 25.0D);
        Assert.assertTrue(result.getAttackYawError() <= 2.0D);
        Assert.assertTrue(result.getReasons().contains(
                "Strong accuracy spike: very poor tracking, near-perfect attack aim"));
    }

    @Test
    public void goodPlayerLowAvg() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        rotations.add(new CombatSample.RotationPoint(-88.0F, 0.0F, 100L));
        rotations.add(new CombatSample.RotationPoint(-89.0F, 0.0F, 200L));
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 300L));
        rotations.add(new CombatSample.RotationPoint(-91.0F, 0.0F, 400L));
        rotations.add(new CombatSample.RotationPoint(-92.0F, 0.0F, 500L));

        AccuracySpikeResult result = AccuracySpikeAnalyzer.analyze(
                sample(-90.0F, 0.0F, rotations), hitResult(-90.0F, 0.0F), config);

        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSpikeDetected());
    }

    private List<CombatSample.RotationPoint> rotationsFacing(float yaw, int count) {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < count; i++) {
            rotations.add(new CombatSample.RotationPoint(yaw, 0.0F, 100L + i));
        }
        return rotations;
    }

    private CombatHitResult hitResult(float yaw, float pitch) {
        CombatSample sample = sample(yaw, pitch, null);
        return CombatHitClassifier.classify(sample, config);
    }

    private CombatSample sample(float attackYaw, float attackPitch,
                                List<CombatSample.RotationPoint> rotations) {
        CombatSample.Builder builder = CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.0D, 0.0D, 0.0D))
                .attackerYaw(attackYaw)
                .attackerPitch(attackPitch)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .timestampMs(ATTACK_TIME);
        if (rotations != null) {
            builder.recentRotations(rotations);
        }
        return builder.build();
    }
}
