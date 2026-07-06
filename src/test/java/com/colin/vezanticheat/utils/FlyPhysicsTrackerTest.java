package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlyPhysicsTrackerTest {

    @Test
    public void monotonicFallSessionDoesNotTriggerBobbing() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long startMs = 10_000L;
        data.setAirborneSessionStartMs(startMs);
        data.setSessionMinY(100.0D);
        data.setSessionMaxY(100.0D);

        for (int i = 0; i < 30; i++) {
            double y = 100.0D - (i * 0.15D);
            simulateAirTick(data, -0.15D, y, startMs + (i * 50L), 0.05D);
        }

        EngineResult er = airResult(-0.15D, 0.05D);
        assertTrue(FlyPhysicsTracker.isMonotonicFallSession(data, er, startMs + 1500L));

        FlyPhysicsTracker.BobConfig cfg = new FlyPhysicsTracker.BobConfig(1300L, 3, 0.06D, 0.04D);
        assertFalse(FlyPhysicsTracker.isBobbingFlySession(data, er, startMs + 1500L, cfg));
    }

    @Test
    public void syntheticOscillationTriggersBobbingAfterThreshold() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long startMs = 20_000L;
        double baseY = 64.0D;

        for (int i = 0; i < 30; i++) {
            double dy = (i % 2 == 0) ? 0.08D : -0.08D;
            double y = baseY + ((i % 2 == 0) ? 0.08D : 0.0D);
            simulateAirTick(data, dy, y, startMs + (i * 50L), 0.06D);
        }

        EngineResult er = airResult(-0.08D, 0.06D);
        FlyPhysicsTracker.BobConfig cfg = new FlyPhysicsTracker.BobConfig(1300L, 3, 0.06D, 0.04D);
        assertTrue(FlyPhysicsTracker.isBobbingFlySession(data, er, startMs + 1500L, cfg));
    }

    @Test
    public void burstExemptWithinVelocityWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastVelocityTime(5000L);
        EngineResult er = airResult(0.45D, 0.08D);
        assertTrue(FlyPhysicsTracker.isBurstExempt(null, data, er, 5200L, 700L));
    }

    @Test
    public void vanillaJumpChainSkipsFlyCheckPastOldShortWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 40_000L;
        data.setLastJumpTime(nowMs - 650L);
        data.setEngineAirborneTicks(9);

        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getAllowFlight()).thenReturn(false);
        when(player.isFlying()).thenReturn(false);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.20D, -0.14D, 0.0D))
                .offset(0.07D)
                .horizontalOffset(0.02D)
                .verticalOffset(0.03D)
                .clientGround(false)
                .predictedOnGround(false)
                .build();

        assertTrue(FlyPhysicsTracker.shouldSkipMovementFlyCheck(player, data, er, nowMs, null));
    }

    @Test
    public void gravityViolationStreakDetectsFlatHoverDy() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        for (int i = 0; i < 12; i++) {
            data.getRecentYMotions().addLast(0.0D);
        }
        int streak = FlyPhysicsTracker.gravityViolationStreak(data, 12, 0.06D);
        assertTrue(streak >= 8);
    }

    private static void simulateAirTick(PlayerData data, double dy, double y, long nowMs, double verticalOffset) {
        data.setLastLoc(new Location(null, 0.0D, y, 0.0D));
        data.setEngineAirborneTicks(data.getEngineAirborneTicks() + 1);
        EngineResult er = airResult(dy, verticalOffset);
        FlyPhysicsTracker.observe(null, data, er, nowMs);
    }

    private static EngineResult airResult(double dy, double verticalOffset) {
        return EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.06D, dy, 0.0D))
                .verticalOffset(verticalOffset)
                .clientGround(false)
                .predictedOnGround(false)
                .build();
    }
}
