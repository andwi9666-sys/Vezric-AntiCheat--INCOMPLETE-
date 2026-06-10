package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class CombatCancelDecisionTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private CombatAnalyzer analyzer;
    private CombatConfig config;

    @Before
    public void setUp() {
        config = CombatConfig.defaults();
        analyzer = new CombatAnalyzer(config);
    }

    @Test
    public void impossibleHitCancelsWithRaytraceReason() {
        CombatHitResult result = result(CombatHitClassification.IMPOSSIBLE, 5.0D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, null);

        Assert.assertTrue(decision.shouldCancel());
        Assert.assertEquals(CombatCancelDecision.REASON_IMPOSSIBLE_RAYTRACE, decision.getReason());
    }

    @Test
    public void impossibleHitAllowedWhenCancelDisabled() {
        analyzer.setCancelImpossibleHits(false);
        CombatHitResult result = result(CombatHitClassification.IMPOSSIBLE, 5.0D);

        CombatCancelDecision decision = analyzer.resolveCancellation(result, null);
        Assert.assertFalse(decision.shouldCancel());
    }

    @Test
    public void impossibleHitCancelsAtHighBufferWhenCancelDisabled() {
        analyzer.setCancelImpossibleHits(false);
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(8.0D);

        CombatHitResult result = result(CombatHitClassification.IMPOSSIBLE, 5.0D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);

        Assert.assertTrue(decision.shouldCancel());
        Assert.assertEquals(CombatCancelDecision.REASON_HIGH_COMBAT_BUFFER, decision.getReason());
    }

    @Test
    public void badHitCancelsAtCancelBuffer() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(8.0D);

        CombatHitResult result = result(CombatHitClassification.BAD, 3.0D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);

        Assert.assertTrue(decision.shouldCancel());
        Assert.assertEquals(CombatCancelDecision.REASON_HIGH_COMBAT_BUFFER, decision.getReason());
    }

    @Test
    public void veryLenientHitCancelsAtAlertBuffer() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(15.0D);

        CombatHitResult result = result(CombatHitClassification.VERY_LENIENT, 1.5D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);

        Assert.assertTrue(decision.shouldCancel());
        Assert.assertEquals(CombatCancelDecision.REASON_HIGH_COMBAT_BUFFER, decision.getReason());
    }

    @Test
    public void cleanHitNeverCancelsEvenAtHighBuffer() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(20.0D);

        CombatHitResult result = result(CombatHitClassification.CLEAN, 0.0D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);

        Assert.assertFalse(decision.shouldCancel());
        Assert.assertEquals(CombatAction.ALERT, analyzer.getRecommendedAction(result, evidence));
    }

    @Test
    public void lenientHitNeverCancelsEvenAtHighBuffer() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(26.0D);

        CombatHitResult result = result(CombatHitClassification.LENIENT, 0.5D);
        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);

        Assert.assertFalse(decision.shouldCancel());
    }

    @Test
    public void withReasonPreservesExistingReasons() {
        CombatHitResult base = CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(CombatHitClassification.IMPOSSIBLE)
                .finalScore(5.0D)
                .addReason("reach=4.0")
                .build();

        CombatHitResult enriched = CombatHitResult.withReason(
                base, CombatCancelDecision.REASON_IMPOSSIBLE_RAYTRACE);

        Assert.assertTrue(enriched.getReasons().contains("reach=4.0"));
        Assert.assertTrue(enriched.getReasons().contains(CombatCancelDecision.REASON_IMPOSSIBLE_RAYTRACE));
    }

    private static CombatHitResult result(CombatHitClassification classification, double finalScore) {
        return CombatHitResult.builder()
                .attackerUuid(ATTACKER)
                .targetUuid(TARGET)
                .classification(classification)
                .finalScore(finalScore)
                .build();
    }
}
