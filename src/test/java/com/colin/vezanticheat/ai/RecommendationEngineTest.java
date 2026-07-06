package com.colin.vezanticheat.ai;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.punishment.PunishmentMode;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.utils.ConfigManager;
import com.colin.vezanticheat.utils.FlagStatsTracker;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

public class RecommendationEngineTest {

    private VezAntiCheat plugin;
    private FlagStatsTracker stats;
    private ConfigManager cfg;
    private YamlConfiguration config;
    private RecommendationEngine engine;

    @Before
    public void setUp() {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();

        plugin = Mockito.mock(VezAntiCheat.class);
        stats = new FlagStatsTracker();
        cfg = Mockito.mock(ConfigManager.class);
        config = new YamlConfiguration();
        config.set("lag.max-ping", 250);
        config.set("lag.min-tps", 18.5D);
        Mockito.when(plugin.flagStats()).thenReturn(stats);
        Mockito.when(plugin.cfg()).thenReturn(cfg);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.perf()).thenReturn(null);
        Mockito.when(cfg.punishSafetyMode()).thenReturn(PunishmentMode.BANWAVE);
        engine = new RecommendationEngine(plugin);
    }

    private static boolean anyContains(List<String> lines, String needle) {
        for (String line : lines) {
            if (line.contains(needle)) return true;
        }
        return false;
    }

    @Test
    public void volumeRuleFiresOnManyPlayersFlagging() {
        for (int i = 0; i < 5; i++) {
            UUID player = UUID.randomUUID();
            for (int j = 0; j < 5; j++) {
                stats.record("PrismScaffoldA", player, 40, 20.0D, false);
            }
        }
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "PrismScaffoldA"));
        Assert.assertTrue(out.toString(), anyContains(out, "bufferToFlag"));
    }

    @Test
    public void highPingRuleFires() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            stats.record("PrismReachA", a, 230, 20.0D, false);
            stats.record("PrismReachA", b, 240, 20.0D, false);
        }
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "high-ping"));
    }

    @Test
    public void lowTpsRuleFires() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            stats.record("PredictionSpeed", a, 40, 17.0D, false);
            stats.record("PredictionSpeed", b, 40, 17.5D, false);
        }
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "low TPS"));
    }

    @Test
    public void dominantPlayerRuleNamesThePlayer() {
        UUID cheater = UUID.randomUUID();
        for (int i = 0; i < 12; i++) {
            stats.record("PrismInteractionLegality", cheater, 40, 20.0D, false);
        }
        stats.record("PrismInteractionLegality", UUID.randomUUID(), 40, 20.0D, false);
        List<String> out = engine.generate();
        // Harness OfflinePlayer mock has no name, so the engine falls back to the uuid string.
        Assert.assertTrue(out.toString(), anyContains(out, "trace"));
    }

    @Test
    public void instantModeWarns() {
        Mockito.when(cfg.punishSafetyMode()).thenReturn(PunishmentMode.INSTANT);
        stats.record("X", UUID.randomUUID(), 40, 20.0D, false);
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "INSTANT"));
    }

    @Test
    public void silentModeNoticeWhenFlagsExist() {
        Mockito.when(cfg.punishSafetyMode()).thenReturn(PunishmentMode.SILENT);
        stats.record("X", UUID.randomUUID(), 40, 20.0D, false);
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "SILENT"));
    }

    @Test
    public void healthyDataYieldsHealthyMessage() {
        UUID player = UUID.randomUUID();
        // A couple of low-volume, low-ping, good-TPS flags: no rule should fire.
        stats.record("PrismReachA", player, 30, 20.0D, false);
        stats.record("PrismReachA", player, 35, 19.9D, false);
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "healthy"));
        Assert.assertFalse(out.toString(), anyContains(out, "bufferToFlag"));
    }

    @Test
    public void zeroFlagsProducesNoFlagNotice() {
        List<String> out = engine.generate();
        Assert.assertTrue(out.toString(), anyContains(out, "No flag"));
    }
}
