package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlyPatternUtilTest {

    @Test
    public void widenedGlideBandIgnoresLegitJumpRise() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastJumpTime(1000L);
        data.setEngineGlideTicks(0);
        data.setEngineAirborneTicks(10);

        EngineResult jumpRise = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.08D, 0.12D, 0.0D))
                .clientGround(false)
                .predictedOnGround(false)
                .verticalOffset(0.05D)
                .build();

        FlyPatternUtil.observe(data, jumpRise);
        assertFalse(FlyPatternUtil.isSuspiciousGlide(data, jumpRise, 1200L));
    }

    @Test
    public void glideBandObservesSlowDescent() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setEngineAirborneTicks(10);

        EngineResult glide = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.08D, -0.10D, 0.0D))
                .clientGround(false)
                .predictedOnGround(false)
                .verticalOffset(0.05D)
                .build();

        for (int i = 0; i < 6; i++) {
            FlyPatternUtil.observe(data, glide);
        }
        assertTrue(data.getEngineGlideTicks() >= 5);
        assertTrue(FlyPatternUtil.isSuspiciousGlide(data, glide, 5000L));
    }
}
