package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.tier.CheckTier;
import org.junit.Assert;
import org.junit.Test;

public class PrismCheckLabelsTest {

    @Test
    public void autoClickMapsToPolarName() {
        Assert.assertEquals("Auto Clicker",
                PrismCheckLabels.polarCheckName("PrismAutoClickD", CheckTier.PRISM));
        Assert.assertEquals("PrismAutoClick",
                PrismCheckLabels.vlPoolName("PrismAutoClickA"));
    }

    @Test
    public void prismTierLabel() {
        Assert.assertEquals("Prism", PrismCheckLabels.polarDataTier(CheckTier.PRISM));
    }

    @Test
    public void autoClickerReasonPrefix() {
        String reason = PrismCheckLabels.autoClickerReason("cps=11.2 bandOcc=0.8");
        Assert.assertTrue(reason.startsWith("clicking_suspiciously"));
    }
}
