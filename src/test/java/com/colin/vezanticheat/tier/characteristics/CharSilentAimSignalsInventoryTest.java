package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.data.PlayerData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CharSilentAimSignalsInventoryTest {

    @Test
    public void inventoryMoveCountFeedsInventoryScore() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setInventoryOpen(true);
        for (int i = 0; i < 6; i++) {
            data.noteInventoryMoveTick(0.12D);
        }
        assertTrue(CharSilentAimSignals.inventoryScore(data, "inventorya") >= 0.45D);
    }

    @Test
    public void inventoryMoveIgnoredWhenClosed() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setInventoryOpen(false);
        for (int i = 0; i < 10; i++) {
            data.noteInventoryMoveTick(0.20D);
        }
        assertEquals(0.0D, CharSilentAimSignals.inventoryScore(data, "inventorya"), 0.001D);
    }
}
