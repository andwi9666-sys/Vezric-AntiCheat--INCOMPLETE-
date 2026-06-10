package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.math.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class CombatEvidenceTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static CombatHitResult result(CombatHitClassification classification, double finalScore) {
        return CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(classification)
                .finalScore(finalScore)
                .addReason(classification.isSuspicious() ? "test-" + classification.name() : null)
                .build();
    }

    @Test
    public void ringBufferEvictsOldestAndKeepsCountersConsistent() {
        CombatConfig config = CombatConfig.defaults();
        config.setCombatEvidenceWindow(100);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        for (int i = 0; i < 100; i++) {
            evidence.addResult(result(CombatHitClassification.CLEAN, 0.0D));
        }
        Assert.assertEquals(100, evidence.getTotalSamples());
        Assert.assertEquals(100, evidence.getCleanCount());
        Assert.assertEquals(0, evidence.getBadCount());

        evidence.addResult(result(CombatHitClassification.BAD, 1.0D));

        Assert.assertEquals(100, evidence.getTotalSamples());
        Assert.assertEquals(99, evidence.getCleanCount());
        Assert.assertEquals(1, evidence.getBadCount());
        Assert.assertEquals(CombatHitClassification.BAD, evidence.getRecentClassifications().get(99));
    }

    @Test
    public void decayBufferReducesBufferOverElapsedSeconds() {
        CombatConfig config = CombatConfig.defaults();
        config.setBufferDecayPerSecond(0.25D);
        long start = System.currentTimeMillis();
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);
        evidence.setBuffer(2.0D);

        evidence.decayBuffer(start + 4000L);

        Assert.assertEquals(1.0D, evidence.getBuffer(), 0.01D);
    }

    @Test
    public void percentagesReflectKnownSequence() {
        CombatEvidence evidence = new CombatEvidence(ATTACKER, CombatConfig.defaults());
        evidence.addResult(result(CombatHitClassification.CLEAN, 0.0D));
        evidence.addResult(result(CombatHitClassification.CLEAN, 0.0D));
        evidence.addResult(result(CombatHitClassification.BAD, 1.0D));

        Assert.assertEquals(3, evidence.getTotalSamples());
        Assert.assertEquals(66.666D, evidence.getCleanPercentage(), 0.1D);
        Assert.assertEquals(33.333D, evidence.getBadPercentage(), 0.1D);
        Assert.assertEquals(0.0D, evidence.getImpossiblePercentage(), 0.001D);
    }

    @Test
    public void bufferThresholdsRespectConfig() {
        CombatConfig config = CombatConfig.defaults();
        config.setCancelHitBuffer(8.0D);
        config.setFlagBuffer(15.0D);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        CombatHitResult badHit = CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.BAD)
                .finalScore(3.0D)
                .build();
        CombatHitResult cleanHit = CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.CLEAN)
                .finalScore(0.0D)
                .build();

        evidence.setBuffer(7.9D);
        Assert.assertFalse(evidence.shouldCancelHit(badHit, true));
        Assert.assertFalse(evidence.shouldFlag());

        evidence.setBuffer(8.0D);
        Assert.assertTrue(evidence.shouldCancelHit(badHit, true));
        Assert.assertFalse(evidence.shouldCancelHit(cleanHit, true));
        Assert.assertFalse(evidence.shouldFlag());

        evidence.setBuffer(15.0D);
        Assert.assertTrue(evidence.shouldCancelHit(badHit, true));
        Assert.assertTrue(evidence.shouldFlag());
    }

    @Test
    public void cleanHitDecaysBuffer() {
        CombatConfig config = CombatConfig.defaults();
        config.setCleanHitBufferDecay(0.15D);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);
        evidence.setBuffer(5.0D);

        evidence.addResult(result(CombatHitClassification.CLEAN, 0.0D));

        Assert.assertEquals(4.85D, evidence.getBuffer(), 0.001D);
    }

    @Test
    public void bufferCapsAtConfiguredMax() {
        CombatConfig config = CombatConfig.defaults();
        config.setMaxBuffer(30.0D);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        evidence.addResult(CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.BAD)
                .finalScore(40.0D)
                .build());

        Assert.assertEquals(30.0D, evidence.getBuffer(), 0.001D);
    }

    @Test
    public void addResultAccumulatesBufferAndReasons() {
        CombatEvidence evidence = new CombatEvidence(ATTACKER, CombatConfig.defaults());
        evidence.addResult(CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.IMPOSSIBLE)
                .finalScore(2.5D)
                .addReason("no-los")
                .addReason("reach-4.0")
                .build());

        Assert.assertEquals(2.5D, evidence.getBuffer(), 0.001D);
        Assert.assertEquals(2.5D, evidence.getRollingSuspiciousScore(), 0.001D);
        Assert.assertEquals(1, evidence.getImpossibleCount());
        Assert.assertEquals(2, evidence.getLastSuspiciousReasons().size());
    }

    @Test
    public void analyzerRecordsAndDecaysEvidence() {
        CombatConfig config = CombatConfig.defaults();
        config.setBufferDecayPerSecond(0.5D);
        CombatAnalyzer analyzer = new CombatAnalyzer(config);
        long start = System.currentTimeMillis();

        analyzer.recordResult(result(CombatHitClassification.BAD, 3.0D));
        CombatEvidence evidence = analyzer.getEvidence(ATTACKER);
        Assert.assertNotNull(evidence);
        Assert.assertEquals(3.0D, evidence.getBuffer(), 0.001D);

        analyzer.tickDecay(start + 2000L);
        Assert.assertEquals(2.0D, evidence.getBuffer(), 0.001D);

        analyzer.removeEvidence(ATTACKER);
        Assert.assertNull(analyzer.getEvidence(ATTACKER));
    }

    @Test
    public void analyzeHitReturnsNullForNullSample() {
        CombatAnalyzer analyzer = new CombatAnalyzer();
        Assert.assertNull(analyzer.analyzeHit(null));
        Assert.assertNull(analyzer.analyze(null));
    }

    @Test
    public void hitPointHistoryCapsAtConfiguredSize() {
        CombatConfig config = CombatConfig.defaults();
        config.setHitDistributionHistorySize(100);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        for (int i = 0; i < 120; i++) {
            evidence.addResult(resultWithHitPoint(false, 30, i));
        }

        Assert.assertEquals(100, evidence.getHitPointHistorySize());
    }

    @Test
    public void expansionShellRatioUsesLastWindow() {
        CombatConfig config = CombatConfig.defaults();
        config.setHitDistributionRatioWindow(50);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        for (int i = 0; i < 50; i++) {
            evidence.addResult(resultWithHitPoint(false, 30, i));
        }
        for (int i = 0; i < 50; i++) {
            evidence.addResult(resultWithHitPoint(true, 30, 100 + i));
        }

        Assert.assertEquals(50, evidence.getHitPointSampleCount());
        Assert.assertEquals(1.0D, evidence.getExpansionShellHitRatio(), 0.001D);
        Assert.assertEquals(0.0D, evidence.getCenterLikeHitRatio(), 0.001D);
    }

    @Test
    public void distributionBufferNotAppliedBelowMinSamples() {
        CombatConfig config = CombatConfig.defaults();
        config.setHitDistributionMinSamples(20);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        for (int i = 0; i < 10; i++) {
            evidence.addResult(resultWithHitPoint(true, 30, i));
        }

        Assert.assertEquals(0.0D, evidence.getBuffer(), 0.001D);
    }

    @Test
    public void distributionBufferAppliedForHighShellRatio() {
        CombatConfig config = CombatConfig.defaults();
        config.setHitDistributionMinSamples(20);
        config.setHitDistributionRatioWindow(50);
        config.setHitDistributionShellRatioThreshold(0.35D);
        config.setHitDistributionShellBuffer(1.0D);
        config.setHitDistributionLowPingShellRatioThreshold(0.25D);
        config.setHitDistributionLowPingShellBuffer(0.75D);
        config.setHitDistributionLowPingThreshold(50);
        CombatEvidence evidence = new CombatEvidence(ATTACKER, config);

        for (int i = 0; i < 19; i++) {
            evidence.addResult(resultWithHitPoint(true, 30, i));
        }
        Assert.assertEquals(0.0D, evidence.getBuffer(), 0.001D);

        evidence.addResult(resultWithHitPoint(true, 30, 19));

        Assert.assertEquals(1.0D, evidence.getExpansionShellHitRatio(), 0.001D);
        Assert.assertEquals(1.75D, evidence.getBuffer(), 0.001D);
        Assert.assertTrue(evidence.getLastSuspiciousReasons().contains(
                "Hit distribution: high expansion-shell ratio"));
        Assert.assertTrue(evidence.getLastSuspiciousReasons().contains(
                "Hit distribution: low ping with repeated expansion-shell hits"));
    }

    private CombatHitResult resultWithHitPoint(boolean expansionShellHit, int ping, long timestamp) {
        BoundingBox box = new BoundingBox(2.7D, 0.0D, -0.3D, 3.3D, 1.8D, 0.3D);
        Vector hitPoint = expansionShellHit
                ? new Vector(3.35D, 0.9D, 0.0D)
                : new Vector(2.75D, 0.1D, -0.25D);
        HitboxExpansionTier tier = expansionShellHit ? HitboxExpansionTier.FULL : HitboxExpansionTier.NORMAL;
        HitPointData data = HitPointData.fromHit(hitPoint, box, !expansionShellHit, tier, timestamp);
        return CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.CLEAN)
                .finalScore(0.0D)
                .ping(ping)
                .hitPointData(data)
                .build();
    }
}
