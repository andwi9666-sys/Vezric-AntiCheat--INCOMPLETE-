package com.colin.vezanticheat.combat.check;

import com.colin.vezanticheat.combat.CombatConfig;
import com.colin.vezanticheat.combat.CombatHitClassification;
import com.colin.vezanticheat.combat.CombatSample;
import com.colin.vezanticheat.data.PlayerCombatData;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TargetSwitchAnalyzerTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID PREVIOUS_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CURRENT_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000033");

    private World world;
    private CombatConfig config;
    private Player attacker;
    private Server server;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        config = CombatConfig.defaults();
        attacker = Mockito.mock(Player.class);
        server = Mockito.mock(Server.class);
        Mockito.when(attacker.getServer()).thenReturn(server);
        Mockito.when(attacker.getWorld()).thenReturn(world);
    }

    @Test
    public void noHistory() {
        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, 0.0F, 0.0F, null), new PlayerCombatData(ATTACKER), config);
        Assert.assertFalse(result.isSwitchedTarget());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("targetSwitch=none"));
    }

    @Test
    public void sameTarget() {
        PlayerCombatData data = new PlayerCombatData(ATTACKER);
        data.addAttack(CURRENT_TARGET, 800L, -90.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);

        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, -90.0F, 0.0F, null), data, config);

        Assert.assertFalse(result.isSwitchedTarget());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("targetSwitch=same_target"));
    }

    @Test
    public void moderateSuspiciousSwitch() {
        mockPreviousTargetOnline(true);

        PlayerCombatData data = new PlayerCombatData(ATTACKER);
        data.addAttack(PREVIOUS_TARGET, 800L, -90.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);

        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, 0.0F, 0.0F, null), data, config);

        Assert.assertTrue(result.isSwitchedTarget());
        Assert.assertEquals(PREVIOUS_TARGET, result.getPreviousTarget());
        Assert.assertEquals(CURRENT_TARGET, result.getCurrentTarget());
        Assert.assertEquals(200L, result.getTimeSinceLastTarget());
        Assert.assertTrue(result.getAngleBetweenTargets() > 45.0D);
        Assert.assertTrue(result.isSuspicious());
        Assert.assertEquals(1.5D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("Instant target switch: large angle with perfect hit"));
    }

    @Test
    public void strongSuspiciousSwitch() {
        mockPreviousTargetOnline(true);

        PlayerCombatData data = new PlayerCombatData(ATTACKER);
        data.addAttack(PREVIOUS_TARGET, 900L, -90.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);

        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, 0.0F, 0.0F, null), data, config);

        Assert.assertTrue(result.isSwitchedTarget());
        Assert.assertEquals(100L, result.getTimeSinceLastTarget());
        Assert.assertTrue(result.getAngleBetweenTargets() > 45.0D);
        Assert.assertEquals(2.25D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("Very quick target switch: large angle with perfect hit"));
    }

    @Test
    public void preAimReducesScore() {
        mockPreviousTargetOnline(true);

        List<CombatSample.RotationPoint> rotations = new ArrayList<CombatSample.RotationPoint>();
        for (int i = 0; i < 5; i++) {
            rotations.add(new CombatSample.RotationPoint(0.0F, 0.0F, 800L + i));
        }

        PlayerCombatData data = new PlayerCombatData(ATTACKER);
        data.addAttack(PREVIOUS_TARGET, 800L, -90.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);

        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, 0.0F, 0.0F, rotations), data, config);

        Assert.assertTrue(result.isSwitchedTarget());
        Assert.assertEquals(0.75D, result.getSuspiciousScore(), 0.001D);
        Assert.assertTrue(result.getReasons().contains("Pre-aim toward new target reduces switch suspicion"));
    }

    @Test
    public void invalidPreviousTarget() {
        Mockito.when(server.getPlayer(PREVIOUS_TARGET)).thenReturn(null);

        PlayerCombatData data = new PlayerCombatData(ATTACKER);
        data.addAttack(PREVIOUS_TARGET, 800L, -90.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);

        TargetSwitchResult result = TargetSwitchAnalyzer.analyze(
                switchSample(1000L, 0.0F, 0.0F, null), data, config);

        Assert.assertTrue(result.isSwitchedTarget());
        Assert.assertEquals(0.0D, result.getSuspiciousScore(), 0.001D);
        Assert.assertFalse(result.isSuspicious());
        Assert.assertTrue(result.getReasons().contains("targetSwitch=previous_target_invalid"));
    }

    private void mockPreviousTargetOnline(boolean online) {
        Player previousTarget = Mockito.mock(Player.class);
        Mockito.when(server.getPlayer(PREVIOUS_TARGET)).thenReturn(previousTarget);
        Mockito.when(previousTarget.isOnline()).thenReturn(online);
        Mockito.when(previousTarget.isDead()).thenReturn(false);
        Mockito.when(previousTarget.getWorld()).thenReturn(world);
    }

    private CombatSample switchSample(long attackTime, float yaw, float pitch,
                                      List<CombatSample.RotationPoint> rotations) {
        CombatSample.Builder builder = CombatSample.builder()
                .attacker(attacker)
                .attackerUuid(ATTACKER)
                .targetUuid(CURRENT_TARGET)
                .attackerEye(new Location(world, 0.0D, 1.62D, 0.0D))
                .targetLocation(new Location(world, 0.0D, 0.0D, 3.0D))
                .attackerYaw(yaw)
                .attackerPitch(pitch)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(30)
                .timestampMs(attackTime);
        if (rotations != null) {
            builder.recentRotations(rotations);
        }
        return builder.build();
    }
}
