package com.colin.vezanticheat.punishment;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.banwave.BanwaveManager;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.testutil.PluginTestSupport;
import com.colin.vezanticheat.utils.ConfigManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.util.UUID;

/**
 * Guards the 1.2.0 thread-safety contract: punishment execution invoked from a
 * Netty thread must defer to the main thread (captured as a scheduler task) and
 * never run Bukkit API inline.
 */
public class PunishmentManagerThreadingTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private VezAntiCheat plugin;
    private ConfigManager cfg;
    private PunishmentManager manager;

    @Before
    public void setUp() throws Exception {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();

        plugin = PluginTestSupport.pluginWithDataFolder(temp.getRoot());
        cfg = Mockito.mock(ConfigManager.class);
        Mockito.when(plugin.cfg()).thenReturn(cfg);
        Mockito.when(cfg.punishEnabled()).thenReturn(true);
        manager = new PunishmentManager(plugin);
    }

    private BanwaveManager.Entry entry(UUID uuid) {
        long now = System.currentTimeMillis();
        return new BanwaveManager.Entry(uuid, "Tester", "PrismReachA", "REACH", "Cheating", now, now, false);
    }

    @Test
    public void offThreadExecutionDefersToMainThread() {
        BukkitTestHarness.setPrimaryThread(false);
        boolean scheduled = manager.executeQueuedPunishment(entry(UUID.randomUUID()), null, null, null, false);
        Assert.assertTrue("off-thread call reports scheduled", scheduled);
        Assert.assertEquals("exactly one main-thread task captured", 1, BukkitTestHarness.pendingTasks());
        // Deliberately NOT running the captured task: its body resolves OfflinePlayer
        // and dispatches commands, which the harness does not model. Capture is the contract.
        BukkitTestHarness.drainTasks();
    }

    @Test
    public void punishmentsDisabledShortCircuitsBeforeScheduling() {
        Mockito.when(cfg.punishEnabled()).thenReturn(false);
        BukkitTestHarness.setPrimaryThread(false);
        Assert.assertFalse(manager.executeQueuedPunishment(entry(UUID.randomUUID()), null, null, null, false));
        Assert.assertEquals(0, BukkitTestHarness.pendingTasks());
    }

    @Test
    public void nullEntryIsRejected() {
        Assert.assertFalse(manager.executeQueuedPunishment(null, null, null, null, false));
        Assert.assertEquals(0, BukkitTestHarness.pendingTasks());
    }

    @Test
    public void clearTransientIsIdempotentAndSafeOnUnknownPlayers() {
        UUID unknown = UUID.randomUUID();
        manager.clearTransient(unknown);
        manager.clearTransient(unknown);
        manager.clearTransient(null);
        // No exception = pass; transient maps tolerate absent keys.
    }

    @Test
    public void markPersistenceWritesOffThreadCoalesce() {
        UUID player = UUID.randomUUID();
        BukkitTestHarness.setPrimaryThread(false);
        manager.setMarkTime(player, 1234L);
        manager.setBanCount(player, 2);
        // Both Netty-thread writes coalesce into a single scheduled save.
        Assert.assertEquals(1, BukkitTestHarness.pendingTasks());
        // Reads are served from the in-memory mirrors immediately, before any save runs.
        Assert.assertEquals(1234L, manager.getMarkTime(player));
        Assert.assertEquals(2, manager.getBanCount(player));
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.runAllTasks();
    }
}
