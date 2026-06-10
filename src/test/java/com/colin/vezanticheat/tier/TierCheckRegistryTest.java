package com.colin.vezanticheat.tier;

import org.junit.Assert;
import org.junit.Test;

public class TierCheckRegistryTest {

    @Test
    public void prismTierHasAutoClickAndNoCharPatternChecks() {
        TierCheckRegistry registry = new TierCheckRegistry(null);
        boolean hasPrismAutoClick = false;
        boolean hasCharAutoClick = false;
        boolean hasCharScaffold = false;
        boolean hasPrismInteraction = false;
        boolean hasStandaloneHitbox = false;
        boolean hasStandaloneBackTrack = false;
        boolean hasStandaloneLagRange = false;

        for (TierCheck check : registry.all()) {
            String name = check.name();
            if (name.startsWith("PrismAutoClick")) hasPrismAutoClick = true;
            if (name.startsWith("CharAutoClick")) hasCharAutoClick = true;
            if (name.startsWith("CharScaffold")) hasCharScaffold = true;
            if ("PrismInteractionLegality".equals(name)) hasPrismInteraction = true;
            if ("PrismHitboxB".equals(name)) hasStandaloneHitbox = true;
            if ("PrismBackTrack".equals(name)) hasStandaloneBackTrack = true;
            if ("PrismLagRange".equals(name)) hasStandaloneLagRange = true;
        }

        Assert.assertTrue(hasPrismAutoClick);
        Assert.assertFalse(hasCharAutoClick);
        Assert.assertFalse(hasCharScaffold);
        Assert.assertTrue(hasPrismInteraction);
        Assert.assertFalse(hasStandaloneHitbox);
        Assert.assertFalse(hasStandaloneBackTrack);
        Assert.assertFalse(hasStandaloneLagRange);
    }
}
