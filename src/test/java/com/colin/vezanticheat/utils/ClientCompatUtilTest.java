package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

/**
 * Floodgate and ViaVersion are not on the test classpath, so detection falls
 * through to the UUID convention (msb == 0) and the client-brand heuristic.
 */
public class ClientCompatUtilTest {

    private VezAntiCheat plugin;
    private YamlConfiguration config;

    @Before
    public void setUp() {
        plugin = Mockito.mock(VezAntiCheat.class);
        config = new YamlConfiguration();
        config.set("compat.bedrock.enabled", true);
        config.set("compat.bedrock.exempt-aim-checks", true);
        config.set("compat.bedrock.scaffold-buffer-multiplier", 1.5D);
        config.set("compat.bedrock.reach-extra", 0.05D);
        config.set("compat.via.enabled", true);
        Mockito.when(plugin.getConfig()).thenReturn(config);
    }

    private Player playerWithUuid(UUID uuid) {
        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    @Test
    public void floodgateUuidConventionDetectsBedrock() {
        UUID floodgate = new UUID(0L, 12345L);
        Player player = playerWithUuid(floodgate);
        PlayerData data = new PlayerData(floodgate);
        Assert.assertTrue(ClientCompatUtil.isBedrock(plugin, player, data));
        Assert.assertEquals(1, data.getBedrockVerdict());
    }

    @Test
    public void normalUuidWithVanillaBrandIsJava() {
        UUID java = UUID.randomUUID();
        Player player = playerWithUuid(java);
        PlayerData data = new PlayerData(java);
        data.setClientBrand("vanilla");
        Assert.assertFalse(ClientCompatUtil.isBedrock(plugin, player, data));
        Assert.assertEquals(0, data.getBedrockVerdict());
    }

    @Test
    public void geyserBrandDetectsBedrockCaseInsensitive() {
        UUID uuid = UUID.randomUUID();
        Player player = playerWithUuid(uuid);
        PlayerData data = new PlayerData(uuid);
        data.setClientBrand("Geyser-Spigot");
        Assert.assertTrue(ClientCompatUtil.isBedrock(plugin, player, data));
    }

    @Test
    public void disabledCompatNeverDetectsBedrock() {
        config.set("compat.bedrock.enabled", false);
        UUID floodgate = new UUID(0L, 99L);
        Player player = playerWithUuid(floodgate);
        PlayerData data = new PlayerData(floodgate);
        Assert.assertFalse(ClientCompatUtil.isBedrock(plugin, player, data));
    }

    @Test
    public void verdictIsCachedInPlayerData() {
        UUID floodgate = new UUID(0L, 7L);
        PlayerData data = new PlayerData(floodgate);
        Assert.assertTrue(ClientCompatUtil.isBedrock(plugin, playerWithUuid(floodgate), data));
        // Same data object, now presented with a Java-looking player: cache wins.
        Assert.assertTrue(ClientCompatUtil.isBedrock(plugin, playerWithUuid(UUID.randomUUID()), data));
    }

    @Test
    public void aimExemptHonorsConfigToggle() {
        UUID floodgate = new UUID(0L, 8L);
        Player player = playerWithUuid(floodgate);
        PlayerData data = new PlayerData(floodgate);
        Assert.assertTrue(ClientCompatUtil.isAimExempt(plugin, player, data));
        config.set("compat.bedrock.exempt-aim-checks", false);
        Assert.assertFalse(ClientCompatUtil.isAimExempt(plugin, player, data));
    }

    @Test
    public void scaffoldBufferScalesOnlyForBedrock() {
        UUID floodgate = new UUID(0L, 9L);
        Assert.assertEquals(6, ClientCompatUtil.scaledScaffoldBuffer(
                plugin, playerWithUuid(floodgate), new PlayerData(floodgate), 4));
        UUID java = UUID.randomUUID();
        PlayerData javaData = new PlayerData(java);
        javaData.setClientBrand("vanilla");
        Assert.assertEquals(4, ClientCompatUtil.scaledScaffoldBuffer(
                plugin, playerWithUuid(java), javaData, 4));
    }

    @Test
    public void reachExtraOnlyForBedrock() {
        UUID floodgate = new UUID(0L, 10L);
        Assert.assertEquals(0.05D, ClientCompatUtil.bedrockReachExtra(
                plugin, playerWithUuid(floodgate), new PlayerData(floodgate)), 1.0E-9);
        UUID java = UUID.randomUUID();
        PlayerData javaData = new PlayerData(java);
        javaData.setClientBrand("vanilla");
        Assert.assertEquals(0.0D, ClientCompatUtil.bedrockReachExtra(
                plugin, playerWithUuid(java), javaData), 1.0E-9);
    }

    @Test
    public void viaAbsentMeansUnknownProtocol() {
        Player player = playerWithUuid(UUID.randomUUID());
        Assert.assertEquals(-1, ClientCompatUtil.protocolVersion(player));
        Assert.assertFalse(ClientCompatUtil.isModernJavaClient(plugin, player));
    }
}
