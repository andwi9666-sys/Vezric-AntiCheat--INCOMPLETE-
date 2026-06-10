package com.colin.vezanticheat.velocity;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

/** Inputs for replaying knockback via micro-teleport animation. */
public final class VelocityCorrectionContext {

    public final Vector knockback;
    public final Location startLoc;
    public final List<PredictedTick> predictedTicks;
    public final VelocitySnapshot snapshot;
    public final String reason;
    public final double setbackConfidence;
    public final double expectedHorizontal;
    public final boolean impossiblePosition;
    public final double maxOutsideDistance;

    public VelocityCorrectionContext(Vector knockback, Location startLoc, List<PredictedTick> predictedTicks,
                                     VelocitySnapshot snapshot, String reason, double setbackConfidence,
                                     double expectedHorizontal, boolean impossiblePosition,
                                     double maxOutsideDistance) {
        this.knockback = knockback == null ? new Vector() : knockback.clone();
        this.startLoc = startLoc == null ? null : startLoc.clone();
        this.predictedTicks = predictedTicks == null
                ? Collections.<PredictedTick>emptyList() : predictedTicks;
        this.snapshot = snapshot;
        this.reason = reason == null ? "velocity" : reason;
        this.setbackConfidence = setbackConfidence;
        this.expectedHorizontal = expectedHorizontal;
        this.impossiblePosition = impossiblePosition;
        this.maxOutsideDistance = maxOutsideDistance;
    }

    public static VelocityCorrectionContext fromEvaluation(VelocityEvaluationResult result, Vector knockback,
                                                           List<PredictedTick> predictedTicks,
                                                           VelocitySnapshot snapshot, String reason) {
        Location start = null;
        if (snapshot != null && snapshot.startLocation != null) {
            start = snapshot.startLocation.clone();
        } else if (result != null && result.setbackLocation != null) {
            start = result.setbackLocation;
        }
        double expectedH = result != null ? result.expectedHorizontal : Math.hypot(knockback.getX(), knockback.getZ());
        double confidence = result != null ? result.setbackConfidence : 0.0D;
        boolean impossible = result != null && result.impossiblePosition;
        double outside = result != null ? result.maxOutsideDistance : 0.0D;
        return new VelocityCorrectionContext(knockback, start, predictedTicks, snapshot, reason,
                confidence, expectedH, impossible, outside);
    }

    public static VelocityCorrectionContext minimal(Vector knockback, Location startLoc, String reason) {
        double expectedH = knockback == null ? 0.0D : Math.hypot(knockback.getX(), knockback.getZ());
        return new VelocityCorrectionContext(knockback, startLoc, null, null, reason,
                0.0D, expectedH, false, 0.0D);
    }
}
