package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.check.TargetSwitchResult;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class CombatAnalyzerTargetSwitchTest {

    private static final UUID ATTACKER = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID PREVIOUS_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID CURRENT_TARGET = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final long ATTACK_TIME = 1000L;

    private World world;
    private Player attacker;
    private Server server;
    private CombatAnalyzer analyzer;

    @Before
    public void setUp() {
        world = Mockito.mock(World.class);
        attacker = Mockito.mock(Player.class);
        server = Mockito.mock(Server.class);
        analyzer = new CombatAnalyzer();

        Mockito.when(attacker.getServer()).thenReturn(server);
        Mockito.when(attacker.getWorld()).thenReturn(world);

        Player previousTarget = Mockito.mock(Player.class);
        Mockito.when(server.getPlayer(PREVIOUS_TARGET)).thenReturn(previousTarget);
        Mockito.when(previousTarget.isOnline()).thenReturn(true);
        Mockito.when(previousTarget.isDead()).thenReturn(false);
        Mockito.when(previousTarget.getWorld()).thenReturn(world);
    }

    @Test
    public void analyzeHitIncludesTargetSwitchResult() {
        seedPreviousAttack(800L);
        CombatHitResult result = analyzer.analyzeHit(switchSample(0.0F, 0.0F));

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getTargetSwitchResult());
        Assert.assertTrue(result.getTargetSwitchResult().isSwitchedTarget());
    }

    @Test
    public void analyzeHitAddsTargetSwitchScore() {
        seedPreviousAttack(800L);
        CombatSample sample = switchSample(0.0F, 0.0F);
        CombatHitResult geometry = CombatHitClassifier.classify(sample, analyzer.getConfig());
        CombatHitResult merged = analyzer.analyzeHit(sample);

        Assert.assertNotNull(merged);
        Assert.assertTrue(merged.getTargetSwitchResult().isSuspicious());
        Assert.assertEquals(1.5D, merged.getTargetSwitchResult().getSuspiciousScore(), 0.001D);

        double expected = geometry.getSuspiciousScore()
                + merged.getPreAimResult().getSuspiciousScore()
                + merged.getAccuracySpikeResult().getSuspiciousScore()
                + merged.getAimCorrelationResult().getSuspiciousScore()
                + merged.getTargetSwitchResult().getSuspiciousScore();
        Assert.assertEquals(expected, merged.getSuspiciousScore(), 0.001D);
    }

    @Test
    public void analyzeTargetSwitchDelegatesToAnalyzer() {
        seedPreviousAttack(800L);
        TargetSwitchResult result = analyzer.analyzeTargetSwitch(switchSample(0.0F, 0.0F));

        Assert.assertTrue(result.isSwitchedTarget());
        Assert.assertEquals(1.5D, result.getSuspiciousScore(), 0.001D);
    }

    private void seedPreviousAttack(long timestamp) {
        analyzer.recordAttack(
                ATTACKER,
                PREVIOUS_TARGET,
                timestamp,
                -90.0F,
                0.0F,
                3.0D,
                CombatHitClassification.CLEAN);
    }

    private CombatSample switchSample(float yaw, float pitch) {
        return CombatSample.builder()
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
                .timestampMs(ATTACK_TIME)
                .build();
    }
}
