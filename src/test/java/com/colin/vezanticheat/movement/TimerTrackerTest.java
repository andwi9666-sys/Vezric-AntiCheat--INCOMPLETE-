package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.data.PlayerData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertTrue;

public class TimerTrackerTest {

    @Test
    public void fastPacketsAccumulateTimerDebt() {
        PlayerMovementState state = new PlayerMovementState();
        PlayerData data = new PlayerData(UUID.randomUUID());
        MovementPhysicsConfig cfg = MovementPhysicsConfig.from(null);

        for (int i = 0; i < 8; i++) {
            TimerTracker.update(state, data, true, 35L, cfg);
        }

        assertTrue(state.timerDebtMs >= cfg.timerDebtThresholdMs);
    }

    @Test
    public void positionlessPacketsAccumulateBlinkTicks() {
        PlayerMovementState state = new PlayerMovementState();
        PlayerData data = new PlayerData(UUID.randomUUID());
        MovementPhysicsConfig cfg = MovementPhysicsConfig.from(null);

        for (int i = 0; i < 5; i++) {
            TimerTracker.update(state, data, false, 50L, cfg);
        }

        assertTrue(state.blinkTicks >= 5);
    }
}
