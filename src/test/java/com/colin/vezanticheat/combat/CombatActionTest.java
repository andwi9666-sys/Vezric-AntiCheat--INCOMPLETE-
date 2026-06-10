package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class CombatActionTest {

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
    public void impossibleHitCancelsImmediately() {
        CombatHitResult result = result(CombatHitClassification.IMPOSSIBLE, 5.0D);
        Assert.assertEquals(CombatAction.CANCEL, analyzer.getRecommendedAction(result, null));
    }

    @Test
    public void badHitCancelsWhenBufferHigh() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(8.0D);

        CombatHitResult result = result(CombatHitClassification.BAD, 3.0D);
        Assert.assertEquals(CombatAction.CANCEL, analyzer.getRecommendedAction(result, evidence));
    }

    @Test
    public void cleanHitAlertsWhenBufferHigh() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(20.0D);

        CombatHitResult result = result(CombatHitClassification.CLEAN, 0.0D);
        Assert.assertEquals(CombatAction.ALERT, analyzer.getRecommendedAction(result, evidence));
    }

    @Test
    public void veryLenientHitCancelsAtAlertBuffer() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(15.0D);

        CombatHitResult result = result(CombatHitClassification.VERY_LENIENT, 1.5D);
        Assert.assertEquals(CombatAction.CANCEL, analyzer.getRecommendedAction(result, evidence));
        Assert.assertTrue(analyzer.shouldCancelHit(ATTACKER, result));

        CombatCancelDecision decision = analyzer.resolveCancellation(result, evidence);
        Assert.assertEquals(CombatCancelDecision.REASON_HIGH_COMBAT_BUFFER, decision.getReason());
    }

    @Test
    public void highBufferReturnsPunishWhenNotCancelled() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(26.0D);

        CombatHitResult result = result(CombatHitClassification.CLEAN, 0.0D);
        Assert.assertEquals(CombatAction.PUNISH, analyzer.getRecommendedAction(result, evidence));
        Assert.assertFalse(analyzer.shouldCancelHit(ATTACKER, result));
    }

    @Test
    public void lowBufferAllowsCleanHit() {
        CombatEvidence evidence = analyzer.getOrCreateEvidence(ATTACKER);
        evidence.setBuffer(2.0D);

        CombatHitResult result = result(CombatHitClassification.CLEAN, 0.0D);
        Assert.assertEquals(CombatAction.ALLOW, analyzer.getRecommendedAction(result, evidence));
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
