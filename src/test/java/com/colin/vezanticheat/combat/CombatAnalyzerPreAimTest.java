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

public class CombatAnalyzerPreAimTest {

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
    public void analyzeHitMergesPreAimScoreAndResult() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(-65.0F, 0.0F, 100L + i));
        }

        CombatSample sample = facingTargetSample(2.5D, -90.0F, 0.0F, rotations);
        CombatHitResult result = analyzer.analyzeHit(sample);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getPreAimResult());
        Assert.assertEquals(CombatHitClassification.CLEAN, result.getClassification());
        Assert.assertEquals(4.5D, result.getSuspiciousScore(), 0.001D);
        Assert.assertNotNull(result.getAccuracySpikeResult());
        Assert.assertTrue(result.getAccuracySpikeResult().isSpikeDetected());
        Assert.assertTrue(result.getReasons().contains("No pre-aim samples near target"));
        Assert.assertTrue(result.getReasons().contains("Pre-aim never near target but hit classified lenient"));
    }

    @Test
    public void analyzePreAimDelegatesToAnalyzer() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 500L));

        PreAimResult result = analyzer.analyzePreAim(facingTargetSample(2.5D, -90.0F, 0.0F, rotations));
        Assert.assertTrue(result.hadPreAim());
        Assert.assertEquals(1, result.getSamplesNearTarget());
    }

    @Test
    public void cleanHitWithPreAimHistoryHasNoExtraScore() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 3; i++) {
            rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 100L + i));
        }

        CombatHitResult result = analyzer.analyzeHit(facingTargetSample(2.5D, -90.0F, 0.0F, rotations));
        Assert.assertNotNull(result);
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getPreAimResult().hadPreAim());
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
