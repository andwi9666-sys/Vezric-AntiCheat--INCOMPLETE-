package com.colin.vezanticheat.combat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class CombatAnalysisSettingsTest {

    @Test
    public void applyToUpdatesAnalyzerThresholds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.buffers.cancel-buffer", 8.0D);
        yaml.set("combat-analysis.buffers.alert-buffer", 15.0D);
        yaml.set("combat-analysis.punish-buffer", 25.0D);
        yaml.set("combat-analysis.cancel-impossible-hits", true);

        CombatAnalyzer analyzer = new CombatAnalyzer();
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(yaml);
        settings.applyTo(analyzer, yaml);

        Assert.assertEquals(8.0D, analyzer.getConfig().getCancelHitBuffer(), 0.001D);
        Assert.assertEquals(15.0D, analyzer.getConfig().getFlagBuffer(), 0.001D);
        Assert.assertEquals(25.0D, analyzer.getConfig().getPunishBuffer(), 0.001D);
        Assert.assertTrue(analyzer.isCancelImpossibleHits());
    }

    @Test
    public void defaultsMatchExpectedValues() {
        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(null);

        Assert.assertTrue(settings.isEnabled());
        Assert.assertTrue(settings.isCancelImpossibleHits());
        Assert.assertTrue(settings.isAlertsEnabled());
        Assert.assertEquals(3000L, settings.getAlertCooldownMs());
        Assert.assertEquals(3.0D, settings.getAlertBufferSpike(), 0.001D);
        Assert.assertFalse(settings.isDebug());
    }

    @Test
    public void legacyCombatAnalyzerEnabledFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analyzer.enabled", false);

        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(yaml);

        Assert.assertFalse(settings.isEnabled());
    }

    @Test
    public void loadFromNestedAlertsSection() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.enabled", false);
        yaml.set("combat-analysis.alerts.enabled", false);
        yaml.set("combat-analysis.alerts.cooldown-ms", 5000L);
        yaml.set("combat-analysis.alerts.debug", true);

        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.load(yaml);

        Assert.assertFalse(settings.isEnabled());
        Assert.assertFalse(settings.isAlertsEnabled());
        Assert.assertEquals(5000L, settings.getAlertCooldownMs());
        Assert.assertTrue(settings.isDebug());
    }
}
