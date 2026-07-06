package com.colin.vezanticheat.tier;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class TierCheckRegistryTest {

    @Test
    public void prismTierHasNoAutoClickOrCharPatternChecks() {
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

        Assert.assertFalse(hasPrismAutoClick);
        Assert.assertFalse(hasCharAutoClick);
        Assert.assertFalse(hasCharScaffold);
        Assert.assertTrue(hasPrismInteraction);
        Assert.assertFalse(hasStandaloneHitbox);
        Assert.assertFalse(hasStandaloneBackTrack);
        Assert.assertFalse(hasStandaloneLagRange);
    }

    @Test
    public void registersBadPacketsGThroughMAndMultiActionsCThroughG() {
        TierCheckRegistry registry = new TierCheckRegistry(null);
        Set<String> names = new HashSet<String>();
        for (TierCheck check : registry.all()) {
            names.add(check.name());
        }
        String[] expected = {
                "PrismBadPacketsG", "PrismBadPacketsH", "PrismBadPacketsI", "PrismBadPacketsJ", "PrismBadPacketsM",
                "PrismMultiActionsC", "PrismMultiActionsD", "PrismMultiActionsE", "PrismMultiActionsF",
                "PrismMultiActionsG"
        };
        for (String name : expected) {
            Assert.assertTrue("Missing registration: " + name, names.contains(name));
        }
    }

    @Test
    public void registersPacketNukerCoverage() {
        TierCheckRegistry registry = new TierCheckRegistry(null);
        Set<String> names = new HashSet<String>();
        for (TierCheck check : registry.all()) {
            names.add(check.name());
        }

        Assert.assertTrue("Missing registration: PrismNukerA", names.contains("PrismNukerA"));
    }

    @Test
    public void registryCountAtLeast100AfterGhostReconcile() {
        TierCheckRegistry registry = new TierCheckRegistry(null);
        Assert.assertTrue("Expected >= 100 registered checks", registry.count() >= 100);
    }

    @Test
    public void everyEnabledPrismYamlKeyIsRegistered() throws Exception {
        YamlConfiguration prism = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/tiers/prism.yml"), StandardCharsets.UTF_8));
        TierCheckRegistry registry = new TierCheckRegistry(null);
        Set<String> registered = new HashSet<String>();
        for (TierCheck check : registry.all()) {
            registered.add(check.name());
        }

        for (String key : prism.getKeys(false)) {
            if ("defaults".equals(key) || key.endsWith("-version")) continue;
            if (!prism.getBoolean(key + ".enabled", true)) continue;
            Assert.assertTrue("prism.yml enabled check not registered: " + key, registered.contains(key));
        }
    }
}
