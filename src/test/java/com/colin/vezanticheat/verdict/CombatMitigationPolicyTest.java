package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.UUID;

public class CombatMitigationPolicyTest {

    @Test
    public void punitiveSetbackPushesAwayFromTarget() {
        World world = Mockito.mock(World.class);
        Player attacker = Mockito.mock(Player.class);
        Entity target = Mockito.mock(Entity.class);

        Location attackerLoc = new Location(world, 0.0, 64.0, 0.0);
        Location targetLoc = new Location(world, 2.0, 64.0, 0.0);
        Mockito.when(attacker.getLocation()).thenReturn(attackerLoc);
        Mockito.when(target.getLocation()).thenReturn(targetLoc);
        Mockito.when(target.getWorld()).thenReturn(world);

        Location snap = CombatMitigationPolicy.resolvePunitiveLocation(attacker, target, null, 0.40D);

        Assert.assertNotNull(snap);
        Assert.assertTrue(snap.getX() < 0.0D);
    }

    @Test
    public void resolvePunitiveLocationFallsBackToLastMoveFrom() {
        World world = Mockito.mock(World.class);
        Player attacker = Mockito.mock(Player.class);
        Location current = new Location(world, 1.0, 64.0, 0.0);
        Location from = new Location(world, 0.5, 64.0, 0.0);
        Mockito.when(attacker.getLocation()).thenReturn(current);

        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastMoveFrom(from);
        data.setLastLoc(current);

        Location snap = CombatMitigationPolicy.resolvePunitiveLocation(attacker, null, data, 0.40D);

        Assert.assertNotNull(snap);
        Assert.assertEquals(0.5D, snap.getX(), 0.001D);
    }

    @Test
    public void isCombatPlayerHitCheckRecognizesSilentAim() {
        Assert.assertTrue(CombatMitigationPolicy.isCombatPlayerHitCheck("CharSilentAim"));
        Assert.assertTrue(CombatMitigationPolicy.isCombatPlayerHitCheck("PrismInteractionLegality"));
        Assert.assertTrue(CombatMitigationPolicy.isCombatPlayerHitCheck("PrismReachA"));
    }

    @Test
    public void prismScaffoldNotCombatCheck() {
        Assert.assertFalse(CombatMitigationPolicy.isCombatPlayerHitCheck("PrismScaffoldA"));
        Assert.assertFalse(CombatMitigationPolicy.isCombatPlayerHitCheck("CharAimAssistA"));
        Assert.assertFalse(CombatMitigationPolicy.isCombatPlayerHitCheck("CharCriticalsA"));
    }

    @Test
    public void highSetbackThresholdDoesNotSetbackModerateConfidence() {
        Assert.assertFalse(CombatMitigationPolicy.shouldPunitiveSetback(
                "high", PrismMitigationPolicy.Confidence.MODERATE));
        Assert.assertTrue(CombatMitigationPolicy.shouldPunitiveSetback(
                "high", PrismMitigationPolicy.Confidence.HIGH));
        Assert.assertTrue(CombatMitigationPolicy.shouldPunitiveSetback(
                "high", PrismMitigationPolicy.Confidence.BLATANT));
    }
}
