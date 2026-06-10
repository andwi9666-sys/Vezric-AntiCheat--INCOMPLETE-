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

public class PreAimAnalyzerTest {

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
    public void noRotationHistory() {
        PreAimResult result = PreAimAnalyzer.analyze(sample(-90.0F, 0.0F, null), config);
        Assert.assertEquals(0, result.getSamplesChecked());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("preAim=no_history"));
    }

    @Test
    public void allRotationsFarFromTarget() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(0.0F, 0.0F, 100L + i));
        }

        PreAimResult result = PreAimAnalyzer.analyze(sample(0.0F, 0.0F, rotations), config);

        Assert.assertEquals(5, result.getSamplesChecked());
        Assert.assertFalse(result.hadPreAim());
        Assert.assertEquals(0, result.getSamplesNearTarget());
        Assert.assertEquals(1.25D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("No pre-aim samples near target"));
    }

    @Test
    public void someNearTarget() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 100L + i));
        }

        PreAimResult result = PreAimAnalyzer.analyze(sample(-90.0F, 0.0F, rotations), config);

        Assert.assertTrue(result.hadPreAim());
        Assert.assertEquals(5, result.getSamplesNearTarget());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
    }

    @Test
    public void snapOnAttack() {
        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(0.0F, 0.0F, 100L + i));
        }

        PreAimResult result = PreAimAnalyzer.analyze(sample(-90.0F, 0.0F, rotations), config);

        Assert.assertEquals(2.75D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("Pre-attack aim far off but attack aim snapped on target"));
    }

    private CombatSample sample(float attackYaw, float attackPitch, List<CombatSample.RotationPoint> rotations) {
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
