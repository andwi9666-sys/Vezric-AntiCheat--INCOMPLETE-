package com.colin.vezanticheat.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class ConfigProfileManagerMergeTest {

    @Test
    public void profileOverridesDefaultsButPreservesMissingKeys() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("license.enabled", false);
        defaults.set("engine.vehicle.buffer-to-flag", 4);
        defaults.set("engine.compensation.leniency-budget-cap", 0.12);

        YamlConfiguration profile = new YamlConfiguration();
        profile.set("engine.compensation.leniency-budget-cap", 0.08);

        ConfigProfileManager.mergeProfileOverrides(defaults, profile);

        Assert.assertEquals(0.08D, defaults.getDouble("engine.compensation.leniency-budget-cap"), 1.0E-9);
        Assert.assertEquals(4, defaults.getInt("engine.vehicle.buffer-to-flag"));
        Assert.assertFalse(defaults.getBoolean("license.enabled"));
    }
}
