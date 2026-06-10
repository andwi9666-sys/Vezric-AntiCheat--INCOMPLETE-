package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
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

public class AimCorrelationAnalyzerTest {

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
    public void insufficientData() {
        AimCorrelationResult result = AimCorrelationAnalyzer.analyze(
                baseSampleBuilder()
                        .attackerYaw(-90.0F)
                        .attackerPitch(0.0F)
                        .build(),
                config);

        Assert.assertFalse(result.enoughData());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("aimCorrelation=insufficient_data"));
    }

    @Test
    public void goodTracking() {
        List<CombatSample.MovementPoint> attackerMoves = new ArrayList<CombatSample.MovementPoint>();
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 800L));
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 900L));

        List<CombatSample.MovementPoint> targetMoves = new ArrayList<CombatSample.MovementPoint>();
        targetMoves.add(new CombatSample.MovementPoint(3.0D, 0.0D, 0.0D, 800L));
        targetMoves.add(new CombatSample.MovementPoint(2.5D, 0.0D, 0.5D, 900L));

        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 800L));
        rotations.add(new CombatSample.RotationPoint(-78.7F, 0.0F, 900L));

        AimCorrelationResult result = AimCorrelationAnalyzer.analyze(
                baseSampleBuilder()
                        .attackerYaw(-78.7F)
                        .attackerPitch(0.0F)
                        .recentMovements(attackerMoves)
                        .recentTargetMovements(targetMoves)
                        .recentRotations(rotations)
                        .build(),
                config);

        Assert.assertTrue(result.enoughData());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSuspicious());
        Assert.assertTrue(result.getCorrelationScore() >= 0.7D);
        Assert.assertTrue(result.getTargetAngularChange() > 5.0D);
        Assert.assertTrue(result.getAttackerYawChange() > 5.0D);
    }

    @Test
    public void poorTrackingMetrics() {
        List<CombatSample.MovementPoint> attackerMoves = new ArrayList<CombatSample.MovementPoint>();
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 800L));
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 900L));

        List<CombatSample.MovementPoint> targetMoves = new ArrayList<CombatSample.MovementPoint>();
        targetMoves.add(new CombatSample.MovementPoint(3.0D, 0.0D, 0.0D, 800L));
        targetMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 3.0D, 900L));

        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 800L));
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 900L));

        AimCorrelationResult result = AimCorrelationAnalyzer.analyze(
                baseSampleBuilder()
                        .attackerYaw(-90.0F)
                        .attackerPitch(0.0F)
                        .recentMovements(attackerMoves)
                        .recentTargetMovements(targetMoves)
                        .recentRotations(rotations)
                        .build(),
                config);

        Assert.assertTrue(result.enoughData());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getTargetAngularChange() > 80.0D);
        Assert.assertTrue(result.getAttackerYawChange() < 1.0D);
        Assert.assertTrue(result.getCorrelationScore() < 0.05D);
    }

    private CombatSample.Builder baseSampleBuilder() {
        return CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.0D, 0.0D, 0.0D))
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .timestampMs(ATTACK_TIME);
    }
}
