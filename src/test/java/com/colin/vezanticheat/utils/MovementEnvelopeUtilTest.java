package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MovementEnvelopeUtilTest {

    @Test
    public void acceptsVanillaJumpChainPastShortJumpWindow() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 20_000L;
        data.setLastJumpTime(nowMs - 620L);
        data.setEngineAirborneTicks(9);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.22D, -0.16D, 0.0D))
                .predicted(new Vector(0.21D, -0.15D, 0.0D))
                .offset(0.08D)
                .horizontalOffset(0.02D)
                .verticalOffset(0.03D)
                .clientGround(false)
                .predictedOnGround(false)
                .build();

        assertTrue(MovementEnvelopeUtil.isLikelyVanillaJumpChain(
                data, er, nowMs, MovementEnvelopeUtil.JumpEnvelopeSettings.defaults()));
    }

    @Test
    public void rejectsYPortSlamFromJumpGrace() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 30_000L;
        data.setLastJumpTime(nowMs - 120L);
        data.setLastMoveDy(0.42D);
        data.setEngineAirborneTicks(2);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.30D, -0.76D, 0.0D))
                .offset(0.12D)
                .horizontalOffset(0.04D)
                .verticalOffset(0.05D)
                .clientGround(false)
                .predictedOnGround(false)
                .build();

        assertFalse(MovementEnvelopeUtil.isLikelyVanillaJumpChain(
                data, er, nowMs, MovementEnvelopeUtil.JumpEnvelopeSettings.defaults()));
    }

    @Test
    public void rejectsCompressedLowHopFromJumpGrace() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 40_000L;
        data.setLastJumpTime(nowMs - 100L);
        data.setEngineAirborneTicks(2);

        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.24D, 0.10D, 0.0D))
                .predicted(new Vector(0.18D, 0.34D, 0.0D))
                .offset(0.10D)
                .horizontalOffset(0.06D)
                .verticalOffset(0.24D)
                .clientGround(false)
                .predictedOnGround(false)
                .build();

        assertFalse(MovementEnvelopeUtil.isLikelyVanillaJumpChain(
                data, er, nowMs, MovementEnvelopeUtil.JumpEnvelopeSettings.defaults()));
    }

    @Test
    public void acceptsBoundedSpeedPotionGroundMovement() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.32D, 0.0D, 0.0D))
                .predicted(new Vector(0.30D, 0.0D, 0.0D))
                .offset(0.04D)
                .horizontalOffset(0.04D)
                .verticalOffset(0.0D)
                .clientGround(true)
                .predictedOnGround(true)
                .build();

        MovementEnvelopeUtil.SpeedGroundSettings settings =
                new MovementEnvelopeUtil.SpeedGroundSettings(true, 0.14D, 1.15D, 0.60D);
        assertTrue(MovementEnvelopeUtil.isLegalSpeedPotionGroundMovement(er, settings));
    }

    @Test
    public void rejectsOverspeedEvenWithSpeedPotion() {
        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.72D, 0.0D, 0.0D))
                .predicted(new Vector(0.25D, 0.0D, 0.0D))
                .offset(0.22D)
                .horizontalOffset(0.22D)
                .verticalOffset(0.0D)
                .clientGround(true)
                .predictedOnGround(true)
                .build();

        MovementEnvelopeUtil.SpeedGroundSettings settings =
                new MovementEnvelopeUtil.SpeedGroundSettings(true, 0.14D, 1.15D, 0.60D);
        assertFalse(MovementEnvelopeUtil.isLegalSpeedPotionGroundMovement(er, settings));
    }
}
