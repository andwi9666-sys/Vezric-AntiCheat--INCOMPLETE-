package com.colin.vezanticheat.utils;

import org.junit.Assert;
import org.junit.Test;

public class PrismInteractionLegalityTest {

    @Test
    public void evaluateInteractionReturnsCleanWithoutAttack() {
        com.colin.vezanticheat.data.PlayerData data = new com.colin.vezanticheat.data.PlayerData(
                java.util.UUID.randomUUID());
        PrismPatternSupport.InteractionLegalityResult result =
                PrismPatternSupport.evaluateInteraction(null, "PrismInteractionLegality", null, data, 0L);
        Assert.assertFalse(result.blatant);
        Assert.assertEquals(0, result.signalCount);
        Assert.assertEquals("clean", result.debug);
    }

    @Test
    public void interactionLegalityResultTracksSignalCount() {
        PrismPatternSupport.InteractionLegalityResult r =
                new PrismPatternSupport.InteractionLegalityResult(
                        true, false, false, false, false, 2, 0, "reach+stale");
        Assert.assertEquals(2, r.signalCount);
        Assert.assertTrue(r.outOfRange);
    }

    @Test
    public void interactionLegalityBlatantReachFlag() {
        PrismPatternSupport.InteractionLegalityResult r =
                new PrismPatternSupport.InteractionLegalityResult(
                        true, false, false, false, true, 3, 1.0D, "blatant-reach");
        Assert.assertTrue(r.blatant);
        Assert.assertTrue(r.signalCount >= 2);
    }

    @Test
    public void interactionLegalityHitboxMissCountsAsSignal() {
        PrismPatternSupport.InteractionLegalityResult r =
                new PrismPatternSupport.InteractionLegalityResult(
                        false, false, false, true, false, 1, 0.5D, "hitbox-miss");
        Assert.assertTrue(r.hitboxMiss);
    }
}
