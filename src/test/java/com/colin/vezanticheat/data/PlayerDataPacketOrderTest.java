package com.colin.vezanticheat.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerDataPacketOrderTest {

    @Test
    public void notePositionPacketOrderDetectsAttackBeforePosition() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 10_000L;
        data.badPackets().notePositionPacket(now - 100L);
        data.noteAttackPacketOrder(now - 20L);
        data.notePositionPacketOrder(now);

        assertTrue(data.isAttackBeforeLastPosition());
    }

    @Test
    public void positionBeforeAttackWithoutReorderDoesNotSetSequenceFlag() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 10_000L;
        data.badPackets().notePositionPacket(now - 10L);
        data.noteAttackPacketOrder(now);
        assertFalse(data.isAttackBeforeLastPosition());
    }

    @Test
    public void kbRatioSustainAccumulatesWhileBelowThreshold() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.noteKbRatioBelowThreshold(true);
        data.noteKbRatioBelowThreshold(true);
        data.noteKbRatioBelowThreshold(true);
        assertEquals(3, data.getKbRatioSustainTicks());

        data.noteKbRatioBelowThreshold(false);
        assertEquals(0, data.getKbRatioSustainTicks());
    }

    @Test
    public void knockbackFlagDedupeSuppressesSecondClaimWithinWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 50_000L;
        assertTrue(data.tryClaimKnockbackFlag("PredictionVelocity", now, 350L));
        assertFalse(data.tryClaimKnockbackFlag("SimulationKnockback", now + 100L, 350L));
        assertTrue(data.tryClaimKnockbackFlag("SimulationKnockback", now + 400L, 350L));
    }

    @Test
    public void dedicatedRotationTimestampTrackedSeparately() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long now = 20_000L;
        data.noteDedicatedRotationPacket(now);
        assertEquals(now, data.getLastDedicatedRotationPacketMs());
    }

    @Test
    public void badPacketWindowResetDoesNotClearPendingNoSwingResolution() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.badPackets().notePlace();
        data.badPackets().noteAttack(12);
        data.badPackets().setPendingNoSwingAttack(20_000L, 12);

        data.badPackets().resetWindow(20_050L);

        assertFalse(data.badPackets().placingThisWindow());
        assertFalse(data.badPackets().attackedThisWindow());
        assertEquals(20_000L, data.badPackets().pendingNoSwingAttackMs());
        assertEquals(12, data.badPackets().pendingNoSwingEntityId());
    }
}
