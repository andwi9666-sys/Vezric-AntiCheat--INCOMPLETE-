package com.colin.vezanticheat.combat;

import org.junit.Assert;
import org.junit.Test;

public class CombatConfigTest {

    @Test
    public void allowedExpansionForPingBuckets() {
        CombatConfig config = CombatConfig.defaults();

        Assert.assertEquals(0.035D, config.getAllowedExpansionForPing(0), 0.001D);
        Assert.assertEquals(0.035D, config.getAllowedExpansionForPing(50), 0.001D);
        Assert.assertEquals(0.060D, config.getAllowedExpansionForPing(51), 0.001D);
        Assert.assertEquals(0.060D, config.getAllowedExpansionForPing(100), 0.001D);
        Assert.assertEquals(0.085D, config.getAllowedExpansionForPing(101), 0.001D);
        Assert.assertEquals(0.085D, config.getAllowedExpansionForPing(160), 0.001D);
        Assert.assertEquals(0.100D, config.getAllowedExpansionForPing(161), 0.001D);
        Assert.assertEquals(0.100D, config.getAllowedExpansionForPing(500), 0.001D);
    }

    @Test
    public void tierForAllowedExpansionMapsPingBuckets() {
        CombatConfig config = CombatConfig.defaults();

        Assert.assertEquals(HitboxExpansionTier.SMALL, config.tierForAllowedExpansion(0.035D));
        Assert.assertEquals(HitboxExpansionTier.MEDIUM, config.tierForAllowedExpansion(0.060D));
        Assert.assertEquals(HitboxExpansionTier.FULL, config.tierForAllowedExpansion(0.085D));
        Assert.assertEquals(HitboxExpansionTier.FULL, config.tierForAllowedExpansion(0.100D));
    }
}
