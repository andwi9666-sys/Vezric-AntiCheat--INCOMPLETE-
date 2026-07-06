package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.AttackRayContext;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.testutil.PluginTestSupport;
import com.colin.vezanticheat.utils.TierConfigManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrismPacketOrderSupportTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final long now = System.currentTimeMillis();

    @Test
    public void positionPacketAgeUsesPositionOnlyTimestamp() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastFlyingPacket(now - 5L);
        data.badPackets().notePositionPacket(now - 90L);

        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);
        assertTrue(posAge >= 85L);
    }

    @Test
    public void dedicatedRotationAgeIgnoresPositionLookRefresh() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastRotationPacket(now - 5L);
        data.noteDedicatedRotationPacket(now - 160L);

        long rotAge = PrismPacketOrderSupport.dedicatedRotationAgeMs(data, now);
        assertTrue(rotAge >= 155L);
    }

    @Test
    public void freshPositionAgeBelowMaxLeadIsClean() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.badPackets().notePositionPacket(now - 15L);
        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);
        assertTrue(posAge <= 30L);
    }

    @Test
    public void stalePositionAgeExceedsMaxLeadForBlatantPath() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.badPackets().notePositionPacket(now - 120L);
        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);
        assertTrue(posAge > 30L);
    }

    @Test
    public void attackBeforePositionSequenceSetsFlag() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.badPackets().notePositionPacket(now - 100L);
        data.noteAttackPacketOrder(now - 20L);
        data.notePositionPacketOrder(now);
        assertTrue(data.isAttackBeforeLastPosition());
    }

    @Test
    public void effectiveFlyingAgeIsLowerThanPositionAgeWhenRotationOnly() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastFlyingPacket(now - 8L);
        data.setLastMoveMillis(now - 8L);
        data.badPackets().notePositionPacket(now - 95L);

        long flyAge = PrismPacketOrderSupport.effectiveFlyingAgeMs(data, now);
        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);
        assertTrue(flyAge < posAge);
    }

    @Test
    public void positionAgeDetectsRt3004BypassScenario() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastFlyingPacket(now - 10L);
        data.setLastMoveMillis(now - 10L);
        data.badPackets().notePositionPacket(now - 200L);
        data.setLastUseEntity(null, true, 2.0D, null, 30L, now - 5L);

        long flyAge = PrismPacketOrderSupport.effectiveFlyingAgeMs(data, now);
        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);
        assertTrue(flyAge < 50L);
        assertTrue(posAge > 100L);
    }

    @Test
    public void stationaryAttackCanHaveFreshFlyingWithStalePosition() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastFlyingPacket(now - 6L);
        data.setLastMoveMillis(now - 6L);
        data.setLastRotationPacket(now - 6L);
        data.badPackets().notePositionPacket(now - 180L);
        data.setLastUseEntity(null, true, 2.9D, null, 20L, now - 3L);

        long flyAge = PrismPacketOrderSupport.effectiveFlyingAgeMs(data, now);
        long posAge = PrismPacketOrderSupport.positionPacketAgeMs(data, now);

        assertTrue(flyAge <= 10L);
        assertTrue(posAge > 100L);
    }

    @Test
    public void staleRotationWithCleanAttackRayIsNotPacketOrderSuspicion() {
        VezAntiCheat plugin = plugin();
        PrismPacketOrderB check = new PrismPacketOrderB(plugin);
        PlayerData data = attackDataWithRay(8.0D, 0.92D);

        double score = PrismPacketOrderSupport.score(plugin, check, data, now,
                PrismPacketOrderSupport.OrderKind.ATTACK_WITHOUT_ROTATE);

        assertEquals(0.0D, score, 0.0D);
    }

    @Test
    public void staleRotationRequiresBadAttackRayToScore() {
        VezAntiCheat plugin = plugin();
        PrismPacketOrderB check = new PrismPacketOrderB(plugin);
        PlayerData data = attackDataWithRay(65.0D, 0.10D);

        double score = PrismPacketOrderSupport.score(plugin, check, data, now,
                PrismPacketOrderSupport.OrderKind.ATTACK_WITHOUT_ROTATE);

        assertTrue(score > 0.0D);
    }

    private VezAntiCheat plugin() {
        VezAntiCheat plugin = PluginTestSupport.pluginWithDataFolder(temp.getRoot());
        Mockito.when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        TierConfigManager tierCfg = new TierConfigManager(plugin);
        Mockito.when(plugin.tierCfg()).thenReturn(tierCfg);
        return plugin;
    }

    private PlayerData attackDataWithRay(double angle, double dot) {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastUseEntity(null, true, 2.9D, null, 20L, now - 5L);
        data.noteDedicatedRotationPacket(now - 500L);
        data.setAttackRayContext(new AttackRayContext(
                now - 5L,
                new Location(null, 0.0D, 64.0D, 0.0D),
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                null,
                angle,
                dot,
                2.9D,
                2));
        return data;
    }
}
