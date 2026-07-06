package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.testutil.BukkitTestHarness;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Release guard for the shipped lenient/balanced/aggressive profiles.
 *
 * v1.2.0 invariants: every profile must carry punish.safety-mode, the ping-scaling
 * exemption cap, the Bedrock scaffold compat multiplier, Perplexion branding (no
 * stale "VezAC"), the &4{check} flag format, and a non-hypixel appeal URL — plus the
 * documented per-profile divergence (lag.max-ping / punish.ban-vl tiers).
 *
 * Also covers ConfigProfileManager.mergeProfileOverrides: profile leaves override
 * bundled defaults, untouched defaults survive, and sections are never flattened.
 */
public class ConfigProfileKeysTest {

    private static final String PROJECT_ROOT =
            "/Users/colincrisp/Documents/Minecraft Plugin Dev/VezricAnticheat/VezAntiCheat";

    @Before
    public void setUp() {
        // YamlConfiguration only touches Bukkit statics on parse failure, but install
        // the shared harness anyway so a malformed profile reports a clean assertion
        // instead of an NPE from Bukkit.getLogger().
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();
    }

    private static FileConfiguration loadProfile(String name) {
        File file = new File("config-profiles/" + name + ".yml");
        if (!file.exists()) {
            // Surefire usually runs from the project root; fall back to the absolute
            // location when the working directory is elsewhere.
            file = new File(PROJECT_ROOT + "/config-profiles/" + name + ".yml");
        }
        assertTrue("profile file not found: " + file.getAbsolutePath(), file.exists());
        return YamlConfiguration.loadConfiguration(file);
    }

    private static void assertReleaseInvariants(String name, FileConfiguration cfg) {
        String safetyMode = cfg.getString("punish.safety-mode");
        assertNotNull(name + ": punish.safety-mode missing", safetyMode);
        String normalizedMode = safetyMode.toLowerCase(Locale.ROOT);
        assertTrue(name + ": punish.safety-mode must be mitigation or banwave, was " + safetyMode,
                normalizedMode.equals("mitigation") || normalizedMode.equals("banwave"));

        assertTrue(name + ": exempt.ping-scaling.cap-ms must be > 0",
                cfg.getDouble("exempt.ping-scaling.cap-ms", 0.0D) > 0.0D);

        assertTrue(name + ": compat.bedrock.scaffold-buffer-multiplier must be > 0",
                cfg.getDouble("compat.bedrock.scaffold-buffer-multiplier", 0.0D) > 0.0D);

        String prefix = cfg.getString("prefix", "");
        assertTrue(name + ": prefix must carry Perplexion branding (PE), was: " + prefix,
                prefix.contains("PE"));
        assertFalse(name + ": prefix still carries stale VezAC branding: " + prefix,
                prefix.contains("VezAC"));

        String flagFormat = cfg.getString("flags.format", "");
        assertTrue(name + ": flags.format must color the check name with &4{check}, was: " + flagFormat,
                flagFormat.contains("&4{check}"));

        String appealUrl = cfg.getString("punish.announcements.appeal-url");
        assertNotNull(name + ": punish.announcements.appeal-url missing", appealUrl);
        assertFalse(name + ": appeal-url must not point at hypixel: " + appealUrl,
                appealUrl.toLowerCase(Locale.ROOT).contains("hypixel"));

        String broadcast = cfg.getString("punish.watchdog.broadcast");
        if (broadcast != null) {
            assertFalse(name + ": punish.watchdog.broadcast still carries VezAC: " + broadcast,
                    broadcast.contains("VezAC"));
        }
    }

    @Test
    public void lenientCarriesReleaseInvariants() {
        assertReleaseInvariants("lenient", loadProfile("lenient"));
    }

    @Test
    public void balancedCarriesReleaseInvariants() {
        assertReleaseInvariants("balanced", loadProfile("balanced"));
    }

    @Test
    public void aggressiveCarriesReleaseInvariants() {
        assertReleaseInvariants("aggressive", loadProfile("aggressive"));
    }

    @Test
    public void lenientDivergenceSpotChecks() {
        FileConfiguration cfg = loadProfile("lenient");
        assertEquals("lenient lag.max-ping", 200, cfg.getInt("lag.max-ping", -1));
        assertEquals("lenient punish.ban-vl", 35, cfg.getInt("punish.ban-vl", -1));
        String safetyMode = cfg.getString("punish.safety-mode", "");
        assertEquals("lenient punish.safety-mode", "mitigation",
                safetyMode.toLowerCase(Locale.ROOT));
    }

    @Test
    public void balancedDivergenceSpotChecks() {
        FileConfiguration cfg = loadProfile("balanced");
        assertEquals("balanced lag.max-ping", 250, cfg.getInt("lag.max-ping", -1));
        assertEquals("balanced punish.ban-vl", 25, cfg.getInt("punish.ban-vl", -1));
    }

    @Test
    public void aggressiveDivergenceSpotChecks() {
        FileConfiguration cfg = loadProfile("aggressive");
        assertEquals("aggressive lag.max-ping", 350, cfg.getInt("lag.max-ping", -1));
        assertEquals("aggressive punish.ban-vl", 18, cfg.getInt("punish.ban-vl", -1));
        assertEquals("aggressive punish.execution.hybrid.min-flags", 5,
                cfg.getInt("punish.execution.hybrid.min-flags", -1));
    }

    @Test
    public void mergeProfileOverridesAppliesLeavesAndPreservesDefaults() {
        YamlConfiguration base = new YamlConfiguration();
        base.set("punish.ban-vl", 25);
        base.set("punish.safety-mode", "banwave");
        base.set("punish.announcements.appeal-url", "https://example.test/appeal");
        base.set("license.enabled", false);

        YamlConfiguration override = new YamlConfiguration();
        override.set("punish.ban-vl", 35);
        override.set("punish.execution.hybrid.min-flags", 5);

        ConfigProfileManager.mergeProfileOverrides(base, override);

        // Profile leaf wins over the bundled default.
        assertEquals(35, base.getInt("punish.ban-vl", -1));
        // New nested leaf from the profile is created under existing sections.
        assertEquals(5, base.getInt("punish.execution.hybrid.min-flags", -1));
        // Keys the profile omits keep their default values.
        assertEquals("banwave", base.getString("punish.safety-mode"));
        assertEquals("https://example.test/appeal",
                base.getString("punish.announcements.appeal-url"));
        assertTrue(base.contains("license.enabled"));
        assertFalse(base.getBoolean("license.enabled", true));
        // Sections traversed by the override must remain sections, not be nulled out.
        assertTrue("punish must remain a configuration section",
                base.isConfigurationSection("punish"));
        assertNotNull(base.getConfigurationSection("punish"));
        assertNotNull(base.getConfigurationSection("punish.execution.hybrid"));
    }
}
