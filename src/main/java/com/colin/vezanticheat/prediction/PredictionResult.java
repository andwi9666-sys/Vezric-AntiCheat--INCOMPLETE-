package com.colin.vezanticheat.prediction;

import org.bukkit.Location;

public final class PredictionResult {

    public final long timeMs;
    public final Location from;
    public final Location to;
    public final boolean clientGround;
    public final boolean serverGround;
    public final boolean effectiveServerGround;
    public final boolean usingItem;
    public final boolean inLiquid;
    public final boolean climbable;
    public final boolean inWeb;
    public final boolean onIce;
    public final boolean onSlime;
    public final boolean weirdSurface;
    public final boolean constrained;
    public final boolean cleanMovement;
    public final boolean positionIncluded;
    public final int pointThreeTicks;
    public final int hiddenGroundTicks;
    public final boolean recentBlockUpdate;
    public final boolean supportUpdate;
    public final boolean worldCompensated;
    public final boolean groundUncertain;
    public final boolean lagCompensated;
    public final boolean replayCompensated;
    public final boolean simulated;
    public final double dx;
    public final double dy;
    public final double dz;
    public final double horizontalDistance;
    public final double expectedHorizontal;
    public final double horizontalUncertainty;
    public final double horizontalOffset;
    public final double minExpectedDy;
    public final double maxExpectedDy;
    public final double verticalUncertainty;
    public final double verticalOffset;
    public final double positionOffset;
    public final double compensationScore;
    public final boolean horizontalViolation;
    public final boolean verticalViolation;
    public final boolean hoverViolation;
    public final boolean phaseViolation;
    public final int fromSolid;
    public final int toSolid;
    public final int pathSolid;
    public final double timerDebtMs;
    public final boolean timerViolation;
    public final String debugSummary;

    public PredictionResult(
            long timeMs,
            Location from,
            Location to,
            boolean clientGround,
            boolean serverGround,
            boolean effectiveServerGround,
            boolean usingItem,
            boolean inLiquid,
            boolean climbable,
            boolean inWeb,
            boolean onIce,
            boolean onSlime,
            boolean weirdSurface,
            boolean constrained,
            boolean cleanMovement,
            boolean positionIncluded,
            int pointThreeTicks,
            int hiddenGroundTicks,
            boolean recentBlockUpdate,
            boolean supportUpdate,
            boolean worldCompensated,
            boolean groundUncertain,
            boolean lagCompensated,
            boolean replayCompensated,
            boolean simulated,
            double dx,
            double dy,
            double dz,
            double horizontalDistance,
            double expectedHorizontal,
            double horizontalUncertainty,
            double horizontalOffset,
            double minExpectedDy,
            double maxExpectedDy,
            double verticalUncertainty,
            double verticalOffset,
            double positionOffset,
            double compensationScore,
            boolean horizontalViolation,
            boolean verticalViolation,
            boolean hoverViolation,
            boolean phaseViolation,
            int fromSolid,
            int toSolid,
            int pathSolid,
            double timerDebtMs,
            boolean timerViolation,
            String debugSummary) {
        this.timeMs = timeMs;
        this.from = from == null ? null : from.clone();
        this.to = to == null ? null : to.clone();
        this.clientGround = clientGround;
        this.serverGround = serverGround;
        this.effectiveServerGround = effectiveServerGround;
        this.usingItem = usingItem;
        this.inLiquid = inLiquid;
        this.climbable = climbable;
        this.inWeb = inWeb;
        this.onIce = onIce;
        this.onSlime = onSlime;
        this.weirdSurface = weirdSurface;
        this.constrained = constrained;
        this.cleanMovement = cleanMovement;
        this.positionIncluded = positionIncluded;
        this.pointThreeTicks = pointThreeTicks;
        this.hiddenGroundTicks = hiddenGroundTicks;
        this.recentBlockUpdate = recentBlockUpdate;
        this.supportUpdate = supportUpdate;
        this.worldCompensated = worldCompensated;
        this.groundUncertain = groundUncertain;
        this.lagCompensated = lagCompensated;
        this.replayCompensated = replayCompensated;
        this.simulated = simulated;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.horizontalDistance = horizontalDistance;
        this.expectedHorizontal = expectedHorizontal;
        this.horizontalUncertainty = horizontalUncertainty;
        this.horizontalOffset = horizontalOffset;
        this.minExpectedDy = minExpectedDy;
        this.maxExpectedDy = maxExpectedDy;
        this.verticalUncertainty = verticalUncertainty;
        this.verticalOffset = verticalOffset;
        this.positionOffset = positionOffset;
        this.compensationScore = compensationScore;
        this.horizontalViolation = horizontalViolation;
        this.verticalViolation = verticalViolation;
        this.hoverViolation = hoverViolation;
        this.phaseViolation = phaseViolation;
        this.fromSolid = fromSolid;
        this.toSolid = toSolid;
        this.pathSolid = pathSolid;
        this.timerDebtMs = timerDebtMs;
        this.timerViolation = timerViolation;
        this.debugSummary = debugSummary == null ? "" : debugSummary;
    }
}
