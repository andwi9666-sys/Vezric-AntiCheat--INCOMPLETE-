package com.colin.vezanticheat.punishment;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.utils.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the punish.safety-mode knob:
 *  - PunishmentMode.parse for every spelling plus garbage/null fallback,
 *  - the capability matrix (alerts / mitigation / punishments / immediate) for all 5 modes,
 *  - legacy derivation in ConfigManager.punishSafetyMode() when the key is missing
 *    (punish.enabled=false -> ALERTS_ONLY, execution.type=IMMEDIATE -> INSTANT,
 *    defaults -> BANWAVE, explicit silent wins over IMMEDIATE).
 */
public class PunishmentSafetyModeTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Before
    public void setUp() {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();
    }

    // ------------------------------------------------------------------
    // PunishmentMode.parse
    // ------------------------------------------------------------------

    @Test
    public void parseRecognizesEveryModeInConfigSpelling() {
        assertEquals(PunishmentMode.SILENT, PunishmentMode.parse("silent", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.ALERTS_ONLY, PunishmentMode.parse("alerts-only", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.MITIGATION, PunishmentMode.parse("mitigation", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.BANWAVE, PunishmentMode.parse("banwave", PunishmentMode.SILENT));
        assertEquals(PunishmentMode.INSTANT, PunishmentMode.parse("instant", PunishmentMode.BANWAVE));
    }

    @Test
    public void parseRecognizesEveryModeInEnumSpelling() {
        assertEquals(PunishmentMode.SILENT, PunishmentMode.parse("SILENT", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.ALERTS_ONLY, PunishmentMode.parse("ALERTS_ONLY", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.MITIGATION, PunishmentMode.parse("MITIGATION", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.BANWAVE, PunishmentMode.parse("BANWAVE", PunishmentMode.SILENT));
        assertEquals(PunishmentMode.INSTANT, PunishmentMode.parse("INSTANT", PunishmentMode.BANWAVE));
    }

    @Test
    public void parseIsCaseInsensitiveAndTrims() {
        assertEquals(PunishmentMode.ALERTS_ONLY, PunishmentMode.parse("Alerts-Only", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.INSTANT, PunishmentMode.parse("  instant  ", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.SILENT, PunishmentMode.parse("sIlEnT", PunishmentMode.BANWAVE));
    }

    @Test
    public void parseGarbageFallsBackToProvidedDefault() {
        assertEquals(PunishmentMode.BANWAVE, PunishmentMode.parse("garbage", PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.SILENT, PunishmentMode.parse("not-a-mode", PunishmentMode.SILENT));
        assertEquals(PunishmentMode.MITIGATION, PunishmentMode.parse("", PunishmentMode.MITIGATION));
        assertEquals(PunishmentMode.ALERTS_ONLY, PunishmentMode.parse("alerts only", PunishmentMode.ALERTS_ONLY));
    }

    @Test
    public void parseNullFallsBackToProvidedDefault() {
        assertEquals(PunishmentMode.BANWAVE, PunishmentMode.parse(null, PunishmentMode.BANWAVE));
        assertEquals(PunishmentMode.ALERTS_ONLY, PunishmentMode.parse(null, PunishmentMode.ALERTS_ONLY));
    }

    // ------------------------------------------------------------------
    // Capability matrix
    // ------------------------------------------------------------------

    @Test
    public void alertsEnabledForEveryModeExceptSilent() {
        assertFalse(PunishmentMode.SILENT.alertsEnabled());
        assertTrue(PunishmentMode.ALERTS_ONLY.alertsEnabled());
        assertTrue(PunishmentMode.MITIGATION.alertsEnabled());
        assertTrue(PunishmentMode.BANWAVE.alertsEnabled());
        assertTrue(PunishmentMode.INSTANT.alertsEnabled());
    }

    @Test
    public void mitigationEnabledOnlyFromMitigationUpward() {
        assertFalse(PunishmentMode.SILENT.mitigationEnabled());
        assertFalse(PunishmentMode.ALERTS_ONLY.mitigationEnabled());
        assertTrue(PunishmentMode.MITIGATION.mitigationEnabled());
        assertTrue(PunishmentMode.BANWAVE.mitigationEnabled());
        assertTrue(PunishmentMode.INSTANT.mitigationEnabled());
    }

    @Test
    public void punishmentsEnabledOnlyForBanwaveAndInstant() {
        assertFalse(PunishmentMode.SILENT.punishmentsEnabled());
        assertFalse(PunishmentMode.ALERTS_ONLY.punishmentsEnabled());
        assertFalse(PunishmentMode.MITIGATION.punishmentsEnabled());
        assertTrue(PunishmentMode.BANWAVE.punishmentsEnabled());
        assertTrue(PunishmentMode.INSTANT.punishmentsEnabled());
    }

    @Test
    public void immediateAllowedOnlyForInstant() {
        assertFalse(PunishmentMode.SILENT.immediateAllowed());
        assertFalse(PunishmentMode.ALERTS_ONLY.immediateAllowed());
        assertFalse(PunishmentMode.MITIGATION.immediateAllowed());
        assertFalse(PunishmentMode.BANWAVE.immediateAllowed());
        assertTrue(PunishmentMode.INSTANT.immediateAllowed());
    }

    // ------------------------------------------------------------------
    // Legacy derivation through a real ConfigManager
    // ------------------------------------------------------------------

    @Test
    public void missingSafetyModeWithPunishDisabledDerivesAlertsOnly() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("punish.enabled", false);

        ConfigManager cfg = configManagerFor(config);
        assertEquals(PunishmentMode.ALERTS_ONLY, cfg.punishSafetyMode());
    }

    @Test
    public void missingSafetyModeWithImmediateExecutionDerivesInstant() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("punish.execution.type", "IMMEDIATE");

        ConfigManager cfg = configManagerFor(config);
        assertEquals(PunishmentMode.INSTANT, cfg.punishSafetyMode());
    }

    @Test
    public void missingSafetyModeWithDefaultsDerivesBanwave() throws Exception {
        YamlConfiguration config = new YamlConfiguration();

        ConfigManager cfg = configManagerFor(config);
        assertEquals(PunishmentMode.BANWAVE, cfg.punishSafetyMode());
    }

    @Test
    public void explicitSilentSafetyModeWinsOverImmediateExecutionType() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("punish.safety-mode", "silent");
        config.set("punish.execution.type", "IMMEDIATE");

        ConfigManager cfg = configManagerFor(config);
        assertEquals(PunishmentMode.SILENT, cfg.punishSafetyMode());
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    /**
     * Builds a real ConfigManager over a mocked plugin whose getConfig() returns the
     * supplied YamlConfiguration. The constructor resolves checks.yml.legacy under the
     * data folder, so an empty one is pre-created in a fresh temp dir; that keeps the
     * constructor off plugin.saveResource() entirely.
     */
    private ConfigManager configManagerFor(YamlConfiguration config) throws Exception {
        VezAntiCheat plugin = mock(VezAntiCheat.class);
        when(plugin.getConfig()).thenReturn(config);

        File dataDir = temp.newFolder();
        File checksLegacy = new File(dataDir, "checks.yml.legacy");
        assertTrue("could not pre-create checks.yml.legacy", checksLegacy.createNewFile());
        setDataFolder(plugin, dataDir);

        return new ConfigManager(plugin);
    }

    /**
     * JavaPlugin.getDataFolder() is final on Spigot 1.8.8 and cannot be stubbed by
     * mockito-core 2.28.2, so the private backing field is set instead; the real final
     * getter then returns the temp dir.
     */
    private static void setDataFolder(VezAntiCheat plugin, File folder) throws Exception {
        Field field = JavaPlugin.class.getDeclaredField("dataFolder");
        field.setAccessible(true);
        field.set(plugin, folder);
    }
}
