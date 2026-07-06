package com.colin.vezanticheat.banwave;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.testutil.PluginTestSupport;
import com.colin.vezanticheat.utils.ConfigManager;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

public class BanwaveSchedulerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private VezAntiCheat plugin;
    private ConfigManager cfg;
    private BanwaveManager banwave;

    @Before
    public void setUp() throws Exception {
        BukkitTestHarness.install();
        BukkitTestHarness.setPrimaryThread(true);
        BukkitTestHarness.drainTasks();

        plugin = PluginTestSupport.pluginWithDataFolder(temp.getRoot());
        cfg = Mockito.mock(ConfigManager.class);
        Mockito.when(plugin.cfg()).thenReturn(cfg);
        Mockito.when(cfg.watchdogReason(Mockito.anyString())).thenReturn("Cheating");
        banwave = new BanwaveManager(plugin);
    }

    private Player player(UUID uuid, String name) {
        Player p = Mockito.mock(Player.class);
        Mockito.when(p.getUniqueId()).thenReturn(uuid);
        Mockito.when(p.getName()).thenReturn(name);
        return p;
    }

    @Test
    public void queueAutoAddsEntry() {
        UUID uuid = UUID.randomUUID();
        Assert.assertTrue(banwave.queueAuto(player(uuid, "A"), "PrismReachA", "REACH", "Cheating", 60000L));
        Assert.assertTrue(banwave.isQueued(uuid));
        Assert.assertEquals(1, banwave.size());
    }

    @Test
    public void duplicateQueueKeepsOneEntryAndMovesExecutionEarlier() {
        UUID uuid = UUID.randomUUID();
        Player p = player(uuid, "A");
        banwave.queueAuto(p, "PrismReachA", "REACH", "Cheating", 600000L);
        long firstExecuteAt = banwave.snapshotEntries().get(0).getExecuteAt();
        Assert.assertFalse("duplicate upsert returns false", banwave.queueAuto(p, "PrismReachA", "REACH", "Cheating", 1000L));
        Assert.assertEquals(1, banwave.size());
        long secondExecuteAt = banwave.snapshotEntries().get(0).getExecuteAt();
        Assert.assertTrue("smaller delay moves executeAt earlier", secondExecuteAt < firstExecuteAt);
    }

    @Test
    public void removeUuidRemoves() {
        UUID uuid = UUID.randomUUID();
        banwave.queueAuto(player(uuid, "A"), "X", "MISC", "Cheating", 1000L);
        Assert.assertTrue(banwave.removeUuid(uuid));
        Assert.assertFalse(banwave.isQueued(uuid));
        Assert.assertFalse("second remove is a no-op", banwave.removeUuid(uuid));
    }

    @Test
    public void requeueWithDelaySchedulesInTheFuture() {
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        BanwaveManager.Entry entry = new BanwaveManager.Entry(
                uuid, "A", "PrismReachA", "REACH", "Cheating", now, now, false);
        banwave.requeueWithDelay(entry, 120000L);
        Assert.assertTrue(banwave.isQueued(uuid));
        List<BanwaveManager.Entry> entries = banwave.snapshotEntries();
        Assert.assertEquals(1, entries.size());
        Assert.assertTrue("executeAt pushed into the future",
                entries.get(0).getExecuteAt() >= now + 100000L);
    }

    @Test
    public void nextAutoDelayStaysWithinConfiguredBounds() {
        Mockito.when(cfg.autoBanwaveMinDelaySeconds()).thenReturn(10L);
        Mockito.when(cfg.autoBanwaveMaxDelaySeconds()).thenReturn(20L);
        for (int i = 0; i < 50; i++) {
            long delay = banwave.nextAutoDelayMs();
            Assert.assertTrue("delay " + delay + " >= 10s", delay >= 10000L);
            Assert.assertTrue("delay " + delay + " <= 20s", delay <= 20000L);
        }
    }

    @Test
    public void entriesPersistAcrossReload() {
        UUID uuid = UUID.randomUUID();
        banwave.queueAuto(player(uuid, "Persist"), "PrismReachA", "REACH", "Cheating", 60000L);
        BanwaveManager reloaded = new BanwaveManager(plugin);
        Assert.assertTrue("entry survives reload from banwave.yml", reloaded.isQueued(uuid));
        Assert.assertEquals("Persist", reloaded.nameOf(uuid));
    }
}
