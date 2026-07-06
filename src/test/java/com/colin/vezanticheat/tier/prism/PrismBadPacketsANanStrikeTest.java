package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.testutil.BukkitTestHarness;
import com.colin.vezanticheat.testutil.PluginTestSupport;
import com.colin.vezanticheat.tier.TierCheckManager;
import com.colin.vezanticheat.utils.TierConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.util.UUID;

/**
 * Guards the 1.2.0 two-strike rule: one corrupt NaN/Infinite rotation packet must
 * never flag alone; a repeat inside the window must. The strike state machine in
 * onRotation runs BEFORE fail(); anticheat.enabled=false makes fail() a no-op so
 * the test observes pure strike accounting without the flag pipeline's deps.
 */
public class PrismBadPacketsANanStrikeTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private VezAntiCheat plugin;
    private PrismBadPacketsA check;
    private Player player;
    private PlayerData data;

    @Before
    public void setUp() throws Exception {
        BukkitTestHarness.install();
        // Defer verboseToStaff delivery so the test never walks online players.
        BukkitTestHarness.setPrimaryThread(false);
        BukkitTestHarness.drainTasks();

        plugin = PluginTestSupport.pluginWithDataFolder(temp.getRoot());
        YamlConfiguration config = new YamlConfiguration();
        config.set("anticheat.enabled", false); // fail() bails after strike logic
        Mockito.when(plugin.getConfig()).thenReturn(config);
        // Real managers: TierConfigManager loads empty tier files (defaults apply);
        // both are final classes, unmockable with mockito-core. Construct BEFORE
        // stubbing — their constructors call methods on the mock, which would break
        // an in-progress when() otherwise.
        TierConfigManager tierCfg = new TierConfigManager(plugin);
        Mockito.when(plugin.tierCfg()).thenReturn(tierCfg);
        TierCheckManager tierChecks = new TierCheckManager(plugin);
        Mockito.when(plugin.tierChecks()).thenReturn(tierChecks);

        check = new PrismBadPacketsA(plugin);
        UUID uuid = UUID.randomUUID();
        player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        Mockito.when(player.getName()).thenReturn("Tester");
        Mockito.when(player.hasPermission(Mockito.anyString())).thenReturn(false);
        data = new PlayerData(uuid);
    }

    @Test
    public void firstNanPacketIsAStrikeNotAFlag() {
        check.onRotation(player, data, Float.NaN, 10.0F);
        Assert.assertEquals(1, data.getNanRotationStrikes());
        Assert.assertTrue(data.getLastNanRotationMs() > 0L);
    }

    @Test
    public void secondNanWithinWindowEntersFlagPathAndResets() {
        check.onRotation(player, data, Float.NaN, 10.0F);
        check.onRotation(player, data, 10.0F, Float.POSITIVE_INFINITY);
        // Strike count resets to 0 immediately before fail() is invoked.
        Assert.assertEquals(0, data.getNanRotationStrikes());
    }

    @Test
    public void expiredWindowRestartsAtStrikeOne() {
        check.onRotation(player, data, Float.NaN, 10.0F);
        Assert.assertEquals(1, data.getNanRotationStrikes());
        // Age the last strike past the 30s default window.
        data.setLastNanRotationMs(System.currentTimeMillis() - 31000L);
        check.onRotation(player, data, Float.NaN, 10.0F);
        Assert.assertEquals("expired window restarts the count", 1, data.getNanRotationStrikes());
    }

    @Test
    public void normalRotationNeverTouchesStrikes() {
        check.onRotation(player, data, 10.0F, 45.0F);
        check.onRotation(player, data, -120.5F, -89.0F);
        Assert.assertEquals(0, data.getNanRotationStrikes());
        Assert.assertEquals(0L, data.getLastNanRotationMs());
    }

    @Test
    public void teleportExemptPlayersAreSkippedEntirely() {
        data.markTeleportExempt(60000L);
        check.onRotation(player, data, Float.NaN, 10.0F);
        Assert.assertEquals(0, data.getNanRotationStrikes());
    }
}
