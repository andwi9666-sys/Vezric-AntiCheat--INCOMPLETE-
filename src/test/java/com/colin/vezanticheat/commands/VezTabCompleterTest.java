package com.colin.vezanticheat.commands;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.tier.TierCheckManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tab-completion coverage for /perplexion. Only paths that avoid Bukkit statics
 * (no online-player completion) and avoid unmockable plugin lookups are exercised.
 *
 * The "tune" first-argument path needs plugin.tierChecks().registry().all();
 * TierCheckManager and TierCheckRegistry are final classes (unmockable with
 * mockito-core 2.x), but every tier-check constructor is a pure field assignment,
 * so a REAL TierCheckManager built around the mocked plugin is used instead.
 */
public class VezTabCompleterTest {

    private VezAntiCheat plugin;
    private CommandSender sender;
    private Command command;
    private VezTabCompleter completer;

    @Before
    public void setUp() {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();

        plugin = mock(VezAntiCheat.class);
        sender = mock(CommandSender.class);
        command = mock(Command.class);
        completer = new VezTabCompleter(plugin);
    }

    private List<String> complete(String... args) {
        return completer.onTabComplete(sender, command, "vez", args);
    }

    @Test
    public void emptyFirstArgListsAllSubcommands() {
        List<String> out = complete("");

        assertEquals("empty prefix must return every subcommand", 18, out.size());
        for (String expected : Arrays.asList(
                "status", "checks", "perf", "recommendations", "movement", "exportdebug", "tune", "profile")) {
            assertTrue("missing subcommand: " + expected, out.contains(expected));
        }
    }

    @Test
    public void rePrefixReturnsOnlyRecommendationsAndReload() {
        List<String> out = complete("re");

        assertEquals(Arrays.asList("recommendations", "reload"), out);
        for (String entry : out) {
            assertTrue("entry does not start with 're': " + entry,
                    entry.toLowerCase(Locale.ROOT).startsWith("re"));
        }
    }

    @Test
    public void profileSecondArgFiltersByPrefix() {
        List<String> out = complete("profile", "a");

        assertEquals(Arrays.asList("aggressive"), out);
    }

    @Test
    public void checksSecondArgListsTierNames() {
        List<String> out = complete("checks", "");

        assertEquals(Arrays.asList("CHARACTERISTICS", "PRISM", "SIMULATION", "PREDICTION"), out);
    }

    @Test
    public void verboseSecondArgOffersOnOff() {
        List<String> out = complete("verbose", "");

        assertEquals(Arrays.asList("on", "off"), out);
    }

    @Test
    public void unknownSubcommandThirdArgReturnsEmptyList() {
        assertTrue("unknown sub must not suggest anything at arg index 2",
                complete("frobnicate", "x", "y").isEmpty());
        // Known sub without third-arg completion behaves the same.
        assertTrue(complete("status", "x", "y").isEmpty());
    }

    @Test
    public void tuneThirdArgSuggestsCommonKeysFilteredByPrefix() {
        assertEquals(Arrays.asList("bufferToFlag"), complete("tune", "ReachA", "b"));

        List<String> all = complete("tune", "ReachA", "");
        assertEquals(Arrays.asList("enabled", "shadow", "bufferToFlag", "punishVl", "decay"), all);
    }

    @Test
    public void tuneSecondArgListsRegisteredCheckNames() {
        // Real registry: tier-check constructors only assign fields, so this is safe
        // with a bare mocked plugin (nothing reads config during construction).
        TierCheckManager manager = new TierCheckManager(plugin);
        when(plugin.tierChecks()).thenReturn(manager);

        List<String> out = complete("tune", "charaimassist");

        assertEquals("expected exactly the three CharAimAssist checks", 3, out.size());
        assertTrue(out.contains("CharAimAssistA"));
        assertTrue(out.contains("CharAimAssistB"));
        assertTrue(out.contains("CharAimAssistC"));
    }

    @Test
    public void tuneSecondArgWithUnmatchedPrefixReturnsEmpty() {
        TierCheckManager manager = new TierCheckManager(plugin);
        when(plugin.tierChecks()).thenReturn(manager);

        assertTrue(complete("tune", "zzz-no-such-check").isEmpty());
    }
}
