package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.check.AimCorrelationResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CombatAnalyzerAimCorrelationTest {

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
    public void analyzeHitIncludesAimCorrelationResult() {
        CombatSample sample = poorCorrelationSample();
        CombatHitResult result = analyzer.analyzeHit(sample);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getAimCorrelationResult());
        Assert.assertTrue(result.getAimCorrelationResult().enoughData());
    }

    @Test
    public void analyzeHitAddsCorrelationScoreOnCleanHitWithPoorTracking() {
        CombatSample sample = poorCorrelationSample();
        CombatHitResult geometry = CombatHitClassifier.classify(sample, analyzer.getConfig());
        CombatHitResult merged = analyzer.analyzeHit(sample);

        Assert.assertNotNull(merged);
        Assert.assertEquals(CombatHitClassification.CLEAN, merged.getClassification());
        Assert.assertTrue(merged.getAimCorrelationResult().isSuspicious());
        Assert.assertEquals(1.5D, merged.getAimCorrelationResult().getSuspiciousScore(), 0.001D);
        Assert.assertTrue(merged.getReasons().contains(
                "Strong aim correlation miss: large target angular change with minimal yaw follow"));

        double expected = geometry.getSuspiciousScore()
                + merged.getPreAimResult().getSuspiciousScore()
                + merged.getAccuracySpikeResult().getSuspiciousScore()
                + merged.getAimCorrelationResult().getSuspiciousScore();
        Assert.assertEquals(expected, merged.getSuspiciousScore(), 0.001D);
    }

    @Test
    public void analyzeAimCorrelationReturnsMetricsWithoutGating() {
        CombatSample sample = poorCorrelationSample();
        AimCorrelationResult result = analyzer.analyzeAimCorrelation(sample);

        Assert.assertTrue(result.enoughData());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSuspicious());
        Assert.assertTrue(result.getTargetAngularChange() > 18.0D);
        Assert.assertTrue(result.getAttackerYawChange() < 3.0D);
    }

    private CombatSample poorCorrelationSample() {
        List<CombatSample.MovementPoint> attackerMoves = new ArrayList<CombatSample.MovementPoint>();
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 800L));
        attackerMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 0.0D, 900L));

        List<CombatSample.MovementPoint> targetMoves = new ArrayList<CombatSample.MovementPoint>();
        targetMoves.add(new CombatSample.MovementPoint(3.0D, 0.0D, 0.0D, 800L));
        targetMoves.add(new CombatSample.MovementPoint(0.0D, 0.0D, 3.0D, 900L));

        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 800L));
        rotations.add(new CombatSample.RotationPoint(-90.0F, 0.0F, 900L));

        return CombatSample.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 3.0D, 0.0D, 0.0D))
                .attackerYaw(-90.0F)
                .attackerPitch(0.0F)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(30)
                .timestampMs(ATTACK_TIME)
                .recentMovements(attackerMoves)
                .recentTargetMovements(targetMoves)
                .recentRotations(rotations)
                .build();
    }
}
