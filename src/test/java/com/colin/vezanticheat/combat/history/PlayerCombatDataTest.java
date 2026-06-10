package com.colin.vezanticheat.combat.history;

import com.colin.vezanticheat.combat.CombatAnalyzer;
import com.colin.vezanticheat.combat.CombatHitClassification;
import com.colin.vezanticheat.data.PlayerCombatData;
import org.bukkit.Location;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class PlayerCombatDataTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private static Location loc(double x, double y, double z) {
        return new Location(null, x, y, z);
    }

    @Test
    public void rotationCapEvictsOldest() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        for (int i = 0; i < 41; i++) {
            data.addRotation(i, 0.0F, i * 50L);
        }
        Assert.assertEquals(PlayerCombatData.MAX_ROTATION_SAMPLES, data.getRecentRotations(100).size());
        List<RotationSample> recent = data.getRecentRotations(1);
        Assert.assertEquals(1, recent.size());
        Assert.assertEquals(40.0F, recent.get(0).getYaw(), 0.001F);
    }

    @Test
    public void rotationDeltaWrapsYaw() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        data.addRotation(350.0F, 0.0F, 0L);
        data.addRotation(10.0F, 5.0F, 50L);

        List<RotationSample> recent = data.getRecentRotations(2);
        Assert.assertEquals(2, recent.size());
        Assert.assertEquals(-10.0F, recent.get(0).getDeltaYaw(), 0.001F);
        Assert.assertEquals(20.0F, recent.get(1).getDeltaYaw(), 0.001F);
        Assert.assertEquals(5.0F, recent.get(1).getDeltaPitch(), 0.001F);
    }

    @Test
    public void movementCapAndDeltas() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        for (int i = 0; i < 41; i++) {
            data.addMovement(loc(i, 0.0D, 0.0D), true, i * 50L);
        }
        Assert.assertEquals(PlayerCombatData.MAX_MOVEMENT_SAMPLES, data.getRecentMovements(100).size());

        PlayerCombatData fresh = new PlayerCombatData(PLAYER);
        fresh.addMovement(loc(0.0D, 0.0D, 0.0D), true, 0L);
        fresh.addMovement(loc(1.0D, 0.0D, 0.0D), true, 50L);
        fresh.addMovement(loc(1.0D, 2.0D, 3.0D), false, 100L);

        List<MovementSample> moves = fresh.getRecentMovements(3);
        Assert.assertEquals(3, moves.size());
        Assert.assertEquals(0.0D, moves.get(0).getDeltaX(), 0.001D);
        Assert.assertEquals(1.0D, moves.get(1).getDeltaX(), 0.001D);
        Assert.assertEquals(0.0D, moves.get(2).getDeltaX(), 0.001D);
        Assert.assertEquals(2.0D, moves.get(2).getDeltaY(), 0.001D);
        Assert.assertEquals(3.0D, moves.get(2).getDeltaZ(), 0.001D);
        Assert.assertFalse(moves.get(2).isOnGround());
    }

    @Test
    public void attackCapEvictsOldest() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        for (int i = 0; i < 21; i++) {
            data.addAttack(TARGET, i * 50L, 0.0F, 0.0F, 3.0D, CombatHitClassification.CLEAN);
        }
        Assert.assertEquals(PlayerCombatData.MAX_ATTACK_SAMPLES, data.getRecentAttacks(100).size());
        List<AttackSample> recent = data.getRecentAttacks(1);
        Assert.assertEquals(1000L, recent.get(0).getTimestamp());
    }

    @Test
    public void getRecentRotationsReturnsChronologicalTail() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        for (int i = 0; i < 10; i++) {
            data.addRotation(i, 0.0F, i * 50L);
        }

        List<RotationSample> recent = data.getRecentRotations(5);
        Assert.assertEquals(5, recent.size());
        Assert.assertEquals(5.0F, recent.get(0).getYaw(), 0.001F);
        Assert.assertEquals(9.0F, recent.get(4).getYaw(), 0.001F);
    }

    @Test
    public void getRecentReturnsEmptyForNonPositiveAmount() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        data.addRotation(1.0F, 2.0F, 0L);
        Assert.assertTrue(data.getRecentRotations(0).isEmpty());
        Assert.assertTrue(data.getRecentMovements(-1).isEmpty());
    }

    @Test
    public void markVelocityAndRecentlyVelocity() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        data.markVelocity(1000L);

        Assert.assertEquals(1000L, data.getLastVelocityTimestamp());
        Assert.assertEquals(1000L, data.getLastKnockbackTimestamp());
        Assert.assertTrue(data.recentlyVelocity(1200L, 300L));
        Assert.assertFalse(data.recentlyVelocity(1401L, 300L));
    }

    @Test
    public void markDamageAndRecentlyDamaged() {
        PlayerCombatData data = new PlayerCombatData(PLAYER);
        data.markDamage(5000L);

        Assert.assertEquals(5000L, data.getLastDamageTimestamp());
        Assert.assertTrue(data.recentlyDamaged(5200L, 300L));
        Assert.assertFalse(data.recentlyDamaged(5401L, 300L));
    }

    @Test
    public void combatAnalyzerRegistryAndRemovePlayer() {
        CombatAnalyzer analyzer = new CombatAnalyzer();
        PlayerCombatData data = analyzer.getOrCreateCombatData(PLAYER);
        Assert.assertNotNull(data);
        Assert.assertSame(data, analyzer.getCombatData(PLAYER));

        analyzer.recordRotation(PLAYER, 90.0F, 10.0F, 100L);
        Assert.assertEquals(1, analyzer.getCombatData(PLAYER).getRecentRotations(5).size());

        analyzer.recordMovement(PLAYER, loc(1.0D, 2.0D, 3.0D), true, 150L);
        Assert.assertEquals(1, analyzer.getCombatData(PLAYER).getRecentMovements(5).size());

        analyzer.recordAttack(PLAYER, TARGET, 200L, 90.0F, 10.0F, 3.1D, CombatHitClassification.LENIENT);
        Assert.assertEquals(1, analyzer.getCombatData(PLAYER).getRecentAttacks(5).size());

        analyzer.removePlayer(PLAYER);
        Assert.assertNull(analyzer.getCombatData(PLAYER));
        Assert.assertNull(analyzer.getEvidence(PLAYER));
    }
}
