package com.colin.vezanticheat.staff;

import com.colin.vezanticheat.tier.CheckTier;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class PolarStaffAlertUtilTest {

    @Test
    public void tooltipMatchesPolarSections() {
        long now = 10_000L;
        PolarFlagRecord record = new PolarFlagRecord(
                UUID.randomUUID(), "Prism", "Auto Clicker", CheckTier.PRISM, "Prism",
                4, "clicking_suspiciously cps=11.2", now - 6000L, 179,
                "Feather Forge", "1.8", 20.0D, "kitpvp-test");
        List<String> lore = PolarStaffAlertUtil.tooltipLore(record, now, false);
        Assert.assertTrue(lore.get(0).contains("Metadata"));
        Assert.assertTrue(lore.stream().anyMatch(line -> line.contains("Auto Clicker")));
        Assert.assertTrue(lore.stream().anyMatch(line -> line.contains("Prism")));
        Assert.assertTrue(lore.stream().anyMatch(line -> line.contains("Feather Forge")));
        Assert.assertTrue(lore.stream().anyMatch(line -> line.contains("6s ago")));
    }
}
