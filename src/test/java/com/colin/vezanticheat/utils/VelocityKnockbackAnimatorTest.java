package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.prediction.PredictionState;
import com.colin.vezanticheat.velocity.PredictedTick;
import com.colin.vezanticheat.velocity.VelocityCorrectionContext;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VelocityKnockbackAnimatorTest {

    @Test
    public void resamplePolylineProducesRequestedStepCount() {
        List<Location> polyline = new ArrayList<Location>();
        polyline.add(new Location(null, 0, 64, 0));
        polyline.add(new Location(null, 1, 64, 0));
        polyline.add(new Location(null, 2, 63, 0));
        polyline.add(new Location(null, 3, 62, 0));

        List<Location> waypoints = VelocityKnockbackAnimator.resamplePolyline(polyline, 24);
        assertEquals(24, waypoints.size());
    }

    @Test
    public void buildWaypointsFromPredictedTicksReachesTwentyFourSteps() {
        Location start = new Location(null, 0, 64, 0);
        List<PredictedTick> ticks = new ArrayList<PredictedTick>();
        for (int i = 0; i < 8; i++) {
            double x = i * 0.35D;
            double y = 64.0D + (i < 2 ? i * 0.2D : -(i - 2) * 0.08D);
            ticks.add(PredictedTick.fromPoint(i, x, y, 0, 0.05D, 0.05D));
        }

        VelocityCorrectionContext ctx = new VelocityCorrectionContext(
                new Vector(0.35, 0.2, 0), start, ticks, null, "test",
                0.5D, 0.35D, false, 0.0D);

        List<Location> waypoints = VelocityKnockbackAnimator.buildWaypoints(null, ctx, start);
        assertEquals(24, waypoints.size());
        assertTrue(waypoints.get(waypoints.size() - 1).getX() >= waypoints.get(0).getX());
    }

    @Test
    public void displacementAlongWaypointsMeetsReplayRatio() {
        Location start = new Location(null, 0, 64, 0);
        List<PredictedTick> ticks = new ArrayList<PredictedTick>();
        for (int i = 1; i <= 8; i++) {
            ticks.add(PredictedTick.fromPoint(i, i * 0.4D, 64.0D, 0, 0.05D, 0.05D));
        }
        VelocityCorrectionContext ctx = new VelocityCorrectionContext(
                new Vector(0.4, 0, 0), start, ticks, null, "test",
                0.5D, 3.2D, false, 0.0D);
        List<Location> waypoints = VelocityKnockbackAnimator.buildWaypoints(null, ctx, start);

        Location end = waypoints.get(waypoints.size() - 1);
        double horizontal = Math.hypot(end.getX() - start.getX(), end.getZ() - start.getZ());
        assertTrue(horizontal >= 3.2D * 0.85D);
    }

    @Test
    public void sequenceInvalidationAbortsFollowUpSteps() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        PredictionState state = data.getPredictionState();
        int first = state.nextVelocityCorrectionSequence();
        state.invalidateVelocityCorrection();
        assertFalse(state.getVelocityCorrectionSequence() == first);
    }

    @Test
    public void emptyKnockbackSkipsAnimation() {
        PlayerData data = new PlayerData(UUID.randomUUID());
        VelocityCorrectionContext ctx = VelocityCorrectionContext.minimal(new Vector(), new Location(null, 0, 64, 0), "test");
        assertFalse(VelocityKnockbackAnimator.applyKnockbackAnimation(null, null, data, ctx));
    }

    @Test
    public void typicalCombatKnockbackWithinAnimateDistanceLimits() {
        Location start = new Location(null, 0, 64, 0);
        List<PredictedTick> ticks = new ArrayList<PredictedTick>();
        for (int i = 1; i <= 6; i++) {
            ticks.add(PredictedTick.fromPoint(i, i * 0.22D, 64.0D + (i <= 2 ? i * 0.15D : 0.0D), 0, 0.05D, 0.05D));
        }
        VelocityCorrectionContext ctx = new VelocityCorrectionContext(
                new Vector(0.22, 0.15, 0), start, ticks, null, "test",
                0.5D, 1.32D, false, 0.0D);
        List<Location> waypoints = VelocityKnockbackAnimator.buildWaypoints(null, ctx, start);

        Location end = waypoints.get(waypoints.size() - 1);
        double horizontalDistance = Math.hypot(end.getX() - start.getX(), end.getZ() - start.getZ());
        double verticalDistance = Math.abs(end.getY() - start.getY());
        assertTrue(horizontalDistance <= 1.70D);
        assertTrue(verticalDistance <= 1.25D);
    }
}
