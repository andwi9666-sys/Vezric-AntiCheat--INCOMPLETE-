package com.colin.vezanticheat.punishment;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers PunishmentManager's pure ladder plumbing:
 *  - parseDuration for every unit suffix plus invalid/empty/null input,
 *  - ban-count round trips (default 0, addBanCount increments, setBanCount explicit),
 *  - mark time set/get/clear,
 *  - punishments.yml persistence across a reload (second manager over the same dir),
 *  - off-thread writes coalescing into a single scheduled save.
 *
 * The constructor's load() touches punishments.yml under getDataFolder(), which is final
 * on Spigot 1.8.8 JavaPlugin and unstubable with mockito-core 2.28.2 — so the private
 * dataFolder field is set reflectively and the real final getter serves the temp dir.
 * scheduleSave() consults Bukkit.isPrimaryThread(): with the harness reporting primary,
 * saves run synchronously; with primary=false they are captured by the mock scheduler.
 */
public class PunishmentLadderTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private VezAntiCheat plugin;
    private PunishmentManager manager;

    @Before
    public void setUp() throws Exception {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();

        plugin = mock(VezAntiCheat.class);
        setDataFolder(plugin, temp.newFolder("data"));
        manager = new PunishmentManager(plugin);
    }

    // ------------------------------------------------------------------
    // parseDuration
    // ------------------------------------------------------------------

    @Test
    public void parseDurationRecognizesDays() {
        assertSpec(manager.parseDuration("30d"), 30, "day");
        assertSpec(manager.parseDuration("90d"), 90, "day");
        assertSpec(manager.parseDuration("365d"), 365, "day");
    }

    @Test
    public void parseDurationRecognizesHoursMinutesWeeks() {
        assertSpec(manager.parseDuration("12h"), 12, "hour");
        assertSpec(manager.parseDuration("45m"), 45, "minute");
        assertSpec(manager.parseDuration("2w"), 2, "week");
    }

    @Test
    public void parseDurationRecognizesMonthsAndYears() {
        assertSpec(manager.parseDuration("6mo"), 6, "month");
        assertSpec(manager.parseDuration("1y"), 1, "year");
    }

    @Test
    public void parseDurationRejectsInvalidInput() {
        assertNull("non-numeric prefix must be rejected", manager.parseDuration("perm-invalid"));
        assertNull("empty string must be rejected", manager.parseDuration(""));
        assertNull("null must be rejected", manager.parseDuration(null));
        assertNull("blank string must be rejected", manager.parseDuration("   "));
        assertNull("unknown unit must be rejected", manager.parseDuration("5fortnights"));
    }

    // ------------------------------------------------------------------
    // Ban-count round trip
    // ------------------------------------------------------------------

    @Test
    public void banCountDefaultsToZero() {
        assertEquals(0, manager.getBanCount(UUID.randomUUID()));
        assertEquals(0, manager.getBanCount(null));
    }

    @Test
    public void addBanCountIncrementsAndReturnsNewValue() {
        UUID uuid = UUID.randomUUID();
        assertEquals(1, manager.addBanCount(uuid));
        assertEquals(2, manager.addBanCount(uuid));
        assertEquals(2, manager.getBanCount(uuid));
    }

    @Test
    public void setBanCountStoresExplicitValue() {
        UUID uuid = UUID.randomUUID();
        manager.setBanCount(uuid, 7);
        assertEquals(7, manager.getBanCount(uuid));
        // addBanCount continues from the explicit value.
        assertEquals(8, manager.addBanCount(uuid));
    }

    // ------------------------------------------------------------------
    // Mark time
    // ------------------------------------------------------------------

    @Test
    public void markTimeSetGetAndClear() {
        UUID uuid = UUID.randomUUID();
        assertEquals(0L, manager.getMarkTime(uuid));
        assertEquals(0L, manager.getMarkTime(null));

        long when = System.currentTimeMillis();
        manager.setMarkTime(uuid, when);
        assertEquals(when, manager.getMarkTime(uuid));

        manager.clearMark(uuid);
        assertEquals(0L, manager.getMarkTime(uuid));
    }

    // ------------------------------------------------------------------
    // Persistence across reload
    // ------------------------------------------------------------------

    @Test
    public void banCountAndMarkTimeSurviveReload() {
        UUID banned = UUID.randomUUID();
        UUID marked = UUID.randomUUID();
        long markedAt = 1234567890123L;

        // Primary thread: scheduleSave() persists synchronously, no tasks queued.
        manager.setBanCount(banned, 3);
        manager.setMarkTime(marked, markedAt);
        assertEquals("primary-thread saves must run inline", 0, BukkitTestHarness.pendingTasks());

        File yml = new File(temp.getRoot(), "data" + File.separator + "punishments.yml");
        assertTrue("punishments.yml must exist after save", yml.isFile());

        PunishmentManager reloaded = new PunishmentManager(plugin);
        assertEquals(3, reloaded.getBanCount(banned));
        assertEquals(markedAt, reloaded.getMarkTime(marked));
        // Untouched players still default after reload.
        assertEquals(0, reloaded.getBanCount(marked));
        assertEquals(0L, reloaded.getMarkTime(banned));
    }

    @Test
    public void offThreadWritesCoalesceIntoOneScheduledSaveAndPersist() {
        UUID uuid = UUID.randomUUID();
        long markedAt = 987654321000L;

        BukkitTestHarness.setPrimaryThread(false);
        manager.setBanCount(uuid, 2);
        manager.setMarkTime(uuid, markedAt);
        assertEquals("netty-thread writes must coalesce into one save task",
                1, BukkitTestHarness.pendingTasks());

        BukkitTestHarness.runAllTasks();
        BukkitTestHarness.setPrimaryThread(true);

        PunishmentManager reloaded = new PunishmentManager(plugin);
        assertEquals(2, reloaded.getBanCount(uuid));
        assertEquals(markedAt, reloaded.getMarkTime(uuid));
    }

    // ------------------------------------------------------------------
    // Plumbing
    // ------------------------------------------------------------------

    private static void assertSpec(PunishmentManager.TimeSpec spec, int expectedTime, String expectedForm) {
        assertNotNull("expected a parsed TimeSpec", spec);
        assertEquals(expectedTime, spec.time);
        assertEquals(expectedForm, spec.timeform);
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
