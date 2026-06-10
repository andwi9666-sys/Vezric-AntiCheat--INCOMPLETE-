package com.colin.vezanticheat.combat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class CombatConfigLoadTest {

    @Test
    public void loadFromNestedYamlOverridesDefaults() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.reach.max-normal-reach", 3.10D);
        yaml.set("combat-analysis.buffers.cancel-buffer", 9.0D);
        yaml.set("combat-analysis.buffers.alert-buffer", 16.0D);
        yaml.set("combat-analysis.pre-aim.near-yaw-error", 10.0D);
        yaml.set("combat-analysis.leniency.recent-knockback-suspicion-multiplier", 0.60D);
        yaml.set("combat-analysis.scoring.lenient-score", 0.75D);

        CombatConfig config = CombatConfig.fromConfig(yaml);

        Assert.assertEquals(3.10D, config.getMaxNormalReach(), 0.001D);
        Assert.assertEquals(9.0D, config.getCancelHitBuffer(), 0.001D);
        Assert.assertEquals(16.0D, config.getFlagBuffer(), 0.001D);
        Assert.assertEquals(10.0D, config.getPreAimNearYawError(), 0.001D);
        Assert.assertEquals(0.60D, config.getKnockbackBehaviorMultiplier(), 0.001D);
        Assert.assertEquals(0.75D, config.getLenientScore(), 0.001D);
    }

    @Test
    public void missingKeysKeepDefaults() {
        CombatConfig config = CombatConfig.fromConfig(new YamlConfiguration());

        Assert.assertEquals(3.05D, config.getMaxNormalReach(), 0.001D);
        Assert.assertEquals(8.0D, config.getCancelHitBuffer(), 0.001D);
        Assert.assertEquals(15.0D, config.getFlagBuffer(), 0.001D);
        Assert.assertEquals(0.75D, config.getKnockbackBehaviorMultiplier(), 0.001D);
        Assert.assertTrue(config.isAccuracySpikeEnabled());
        Assert.assertTrue(config.isTargetSwitchEnabled());
    }

    @Test
    public void legacyFlatBufferKeysFallback() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.cancel-buffer", 7.5D);
        yaml.set("combat-analysis.alert-buffer", 14.0D);

        CombatConfig config = CombatConfig.fromConfig(yaml);

        Assert.assertEquals(7.5D, config.getCancelHitBuffer(), 0.001D);
        Assert.assertEquals(14.0D, config.getFlagBuffer(), 0.001D);
    }

    @Test
    public void loadFromMutatesExistingInstanceInPlace() {
        CombatConfig config = CombatConfig.defaults();
        CombatConfig sameRef = config;

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.reach.max-bad-reach", 3.80D);
        config.loadFrom(yaml);

        Assert.assertSame(sameRef, config);
        Assert.assertEquals(3.80D, config.getMaxBadReach(), 0.001D);
    }

    @Test
    public void reloadThroughAnalyzerPreservesConfigReference() {
        CombatAnalyzer analyzer = new CombatAnalyzer();
        CombatConfig original = analyzer.getConfig();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combat-analysis.buffers.max-buffer", 35.0D);

        CombatAnalysisSettings settings = new CombatAnalysisSettings();
        settings.applyTo(analyzer, yaml);

        Assert.assertSame(original, analyzer.getConfig());
        Assert.assertEquals(35.0D, analyzer.getConfig().getMaxBuffer(), 0.001D);
    }
}
