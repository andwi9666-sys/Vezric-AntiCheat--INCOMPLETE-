package com.colin.vezanticheat.velocity;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Active knockback response window for one player.
 */
public final class VelocitySession {

    public final VelocitySnapshot snapshot;
    public final List<PredictedTick> predictedTicks;
    public final long windowMs;

    public double startY;
    public double maxY;
    public double maxHorizontal;
    public double projectedHorizontal;
    public double directionDot;
    public Vector expectedDirection;

    public int stackCount;
    public int impossibleTicks;
    public double maxOutsideDistance;
    public int lastObservedTick;

    public boolean evaluated;
    public long evaluatedAtMs;
    public VelocityEvaluationResult result;

    public boolean jumpNearVelocity;
    public boolean lagCover;
    public boolean attackNearVelocity;
    public boolean combatInteractNearVelocity;

    // Track first tick observations for early-window KB measurement
    public double earlyMaxHorizontal;
    public double earlyProjectedHorizontal;
    public int earlyTickCount;
    public Location lastObservedLocation;

    public VelocitySession(VelocitySnapshot snapshot, List<PredictedTick> predictedTicks, long windowMs) {
        this.snapshot = snapshot;
        this.predictedTicks = predictedTicks == null ? new ArrayList<PredictedTick>() : predictedTicks;
        this.windowMs = windowMs;
        if (snapshot != null && snapshot.startLocation != null) {
            this.startY = snapshot.startLocation.getY();
        }
        if (snapshot != null && snapshot.velocity != null) {
            this.expectedDirection = VelocityPredictionEngine.horizontalDirection(snapshot.velocity);
        }
    }

    public void observe(Location loc, long nowMs) {
        if (loc == null || snapshot == null || snapshot.startLocation == null) return;
        if (loc.getWorld() == null || snapshot.startLocation.getWorld() == null) return;
        if (!loc.getWorld().equals(snapshot.startLocation.getWorld())) return;

        double dx = loc.getX() - snapshot.startLocation.getX();
        double dz = loc.getZ() - snapshot.startLocation.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz > maxHorizontal) maxHorizontal = horiz;

        if (loc.getY() > maxY) maxY = loc.getY();

        if (expectedDirection != null) {
            projectedHorizontal = (dx * expectedDirection.getX()) + (dz * expectedDirection.getZ());
            double actLen = Math.hypot(dx, dz);
            if (actLen > 1.0E-6) {
                directionDot = ((dx / actLen) * expectedDirection.getX())
                        + ((dz / actLen) * expectedDirection.getZ());
            }
        }

        long elapsed = Math.max(0L, nowMs - snapshot.timeMs);
        int tickIndex = (int) Math.min(predictedTicks.size() - 1,
                Math.max(0, Math.round(elapsed / 50.0)));
        lastObservedTick = tickIndex;

        // Track early-window KB response (first 3 ticks / 150ms)
        // This measures immediate KB response before player input dominates
        if (tickIndex <= 2) {
            earlyTickCount++;
            if (horiz > earlyMaxHorizontal) {
                earlyMaxHorizontal = horiz;
            }
            if (expectedDirection != null) {
                double earlyProj = (dx * expectedDirection.getX()) + (dz * expectedDirection.getZ());
                if (earlyProj > earlyProjectedHorizontal) {
                    earlyProjectedHorizontal = earlyProj;
                }
            }
        }

        if (tickIndex >= 0 && tickIndex < predictedTicks.size()) {
            PredictedTick tick = predictedTicks.get(tickIndex);
            if (!tick.contains(loc.getX(), loc.getY(), loc.getZ())) {
                impossibleTicks++;
                double outside = tick.distanceOutside(loc.getX(), loc.getY(), loc.getZ());
                if (outside > maxOutsideDistance) maxOutsideDistance = outside;
            }
        }

        lastObservedLocation = loc.clone();
    }

    public double verticalGain() {
        return maxY - startY;
    }

    public boolean hasCombatInputWindow() {
        return attackNearVelocity || combatInteractNearVelocity;
    }
}
