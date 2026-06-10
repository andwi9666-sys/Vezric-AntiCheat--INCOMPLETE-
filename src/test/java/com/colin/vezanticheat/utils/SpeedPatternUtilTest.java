package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeedPatternUtilTest {

    @Test
    public void detectsYPortSlamPattern() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setLastMoveDy(0.42D);
        assertTrue(SpeedPatternUtil.isYPortSlam(data, -0.76D));
        assertFalse(SpeedPatternUtil.isYPortSlam(data, -0.40D));
    }

    @Test
    public void tracksReleaseUseItemStreak() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        UseItemTracker.noteReleaseUseItem(data, 1000L);
        UseItemTracker.noteReleaseUseItem(data, 1050L);
        assertTrue(data.getReleaseUseItemStreak() >= 2);
    }

    @Test
    public void combatGracePreventsForceSpeedDetection() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        long nowMs = 20_000L;
        data.setLastDamageTime(nowMs - 100L);
        data.setEngineMicroRatioStreak(12);
        data.setEngineHorizontalGainStreak(10);

        assertTrue(EngineMovementGrace.isKnockbackOrCombatGrace(
                data, nowMs, EngineMovementGrace.GraceSettings.defaults()));
    }
}
