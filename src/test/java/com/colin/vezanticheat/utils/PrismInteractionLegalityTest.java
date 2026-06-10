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
}
