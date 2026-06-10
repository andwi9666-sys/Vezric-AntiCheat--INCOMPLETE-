package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GroundSpoofTrackerTest {

    @Test
    public void sustainedGroundDescentRequiresStreak() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        GroundSpoofTracker.DescentConfig cfg = new GroundSpoofTracker.DescentConfig(-0.04D, 3, 0.12D);

        for (int i = 0; i < 2; i++) {
            simulateGroundDescentTick(data, -0.06D, 64.0D - (i * 0.06D), 1000L + (i * 50L));
        }
        assertFalse(GroundSpoofTracker.isSustainedGroundDescent(data, cfg));

        simulateGroundDescentTick(data, -0.06D, 63.88D, 1100L);
        assertTrue(GroundSpoofTracker.isSustainedGroundDescent(data, cfg));
    }

    @Test
    public void cumulativeDescentTriggersWithoutLongStreak() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        data.setGroundSpoofAirTicks(3);
        data.setGroundDescentCumulativeDy(-0.15D);
        GroundSpoofTracker.DescentConfig cfg = new GroundSpoofTracker.DescentConfig(-0.04D, 5, 0.12D);
        assertTrue(GroundSpoofTracker.isSustainedGroundDescent(data, cfg));
    }

    @Test
    public void airBelowGroundClaimRequiresClientGroundAndAirBelow() {
        NoFallUtil.Context ctx = new NoFallUtil.Context(
                new Location(null, 0, 64, 0),
                new Location(null, 0, 64, 0),
                -0.05D, 0.0D,
                false, true, true,
                false, false, false,
                false, false, false,
                false, true);
        assertTrue(GroundSpoofTracker.isAirBelowGroundClaim(ctx));
    }

    private static void simulateGroundDescentTick(PlayerData data, double dy, double y, long nowMs) {
        data.setLastLoc(new Location(null, 0.0D, y, 0.0D));
        data.setGroundDescentSessionStartMs(data.getGroundDescentSessionStartMs() <= 0L ? nowMs : data.getGroundDescentSessionStartMs());
        if (data.getGroundSpoofLastY() == 0.0D) {
            data.setGroundSpoofLastY(y - dy);
        }
        EngineResult er = EngineResult.builder()
                .checked(true)
                .actual(new Vector(0.0D, dy, 0.0D))
                .clientGround(true)
                .predictedOnGround(true)
                .build();
        GroundSpoofTracker.observe(null, null, data, er, nowMs);
    }
}
