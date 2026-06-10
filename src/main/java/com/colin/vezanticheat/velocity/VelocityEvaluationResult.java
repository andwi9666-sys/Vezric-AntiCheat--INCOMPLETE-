package com.colin.vezanticheat.velocity;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * Outcome of a single knockback evaluation window for VelocityA-D.
 */
public final class VelocityEvaluationResult {

    public final long evaluatedAtMs;
    public final String exemptReason;

    public final boolean zeroVertical;
    public final double verticalGain;
    public final double expectedVertical;
    public final double minVerticalRequired;

    public final boolean reducedHorizontal;
    public final double maxHorizontal;
    public final double expectedHorizontal;
    public final double minHorizontalRequired;
    public final boolean weakForwardResponse;
    public final double minProjectedRequired;

    public final boolean reverseKnockback;
    public final double directionDot;
    public final double projectedHorizontal;

    public final boolean impossiblePosition;
    public final int impossibleTicks;
    public final double maxOutsideDistance;
    public final int evaluatedTickIndex;

    public final double setbackConfidence;
    public final Location setbackLocation;

    public final List<PredictedTick> predictedTicks;
    public final VelocitySnapshot snapshot;

    public final String debugSummary;

    private VelocityEvaluationResult(Builder b) {
        this.evaluatedAtMs = b.evaluatedAtMs;
        this.exemptReason = b.exemptReason;
        this.zeroVertical = b.zeroVertical;
        this.verticalGain = b.verticalGain;
        this.expectedVertical = b.expectedVertical;
        this.minVerticalRequired = b.minVerticalRequired;
        this.reducedHorizontal = b.reducedHorizontal;
        this.maxHorizontal = b.maxHorizontal;
        this.expectedHorizontal = b.expectedHorizontal;
        this.minHorizontalRequired = b.minHorizontalRequired;
        this.weakForwardResponse = b.weakForwardResponse;
        this.minProjectedRequired = b.minProjectedRequired;
        this.reverseKnockback = b.reverseKnockback;
        this.directionDot = b.directionDot;
        this.projectedHorizontal = b.projectedHorizontal;
        this.impossiblePosition = b.impossiblePosition;
        this.impossibleTicks = b.impossibleTicks;
        this.maxOutsideDistance = b.maxOutsideDistance;
        this.evaluatedTickIndex = b.evaluatedTickIndex;
        this.setbackConfidence = b.setbackConfidence;
        this.setbackLocation = b.setbackLocation == null ? null : b.setbackLocation.clone();
        this.predictedTicks = b.predictedTicks == null
                ? Collections.<PredictedTick>emptyList() : b.predictedTicks;
        this.snapshot = b.snapshot;
        this.debugSummary = b.debugSummary == null ? "" : b.debugSummary;
    }

    public boolean isExempt() {
        return exemptReason != null && !exemptReason.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long evaluatedAtMs;
        private String exemptReason;
        private boolean zeroVertical;
        private double verticalGain;
        private double expectedVertical;
        private double minVerticalRequired;
        private boolean reducedHorizontal;
        private double maxHorizontal;
        private double expectedHorizontal;
        private double minHorizontalRequired;
        private boolean weakForwardResponse;
        private double minProjectedRequired;
        private boolean reverseKnockback;
        private double directionDot;
        private double projectedHorizontal;
        private boolean impossiblePosition;
        private int impossibleTicks;
        private double maxOutsideDistance;
        private int evaluatedTickIndex;
        private double setbackConfidence;
        private Location setbackLocation;
        private List<PredictedTick> predictedTicks;
        private VelocitySnapshot snapshot;
        private String debugSummary;

        public Builder evaluatedAtMs(long v) { evaluatedAtMs = v; return this; }
        public Builder exemptReason(String v) { exemptReason = v; return this; }
        public Builder zeroVertical(boolean v) { zeroVertical = v; return this; }
        public Builder verticalGain(double v) { verticalGain = v; return this; }
        public Builder expectedVertical(double v) { expectedVertical = v; return this; }
        public Builder minVerticalRequired(double v) { minVerticalRequired = v; return this; }
        public Builder reducedHorizontal(boolean v) { reducedHorizontal = v; return this; }
        public Builder maxHorizontal(double v) { maxHorizontal = v; return this; }
        public Builder expectedHorizontal(double v) { expectedHorizontal = v; return this; }
        public Builder minHorizontalRequired(double v) { minHorizontalRequired = v; return this; }
        public Builder weakForwardResponse(boolean v) { weakForwardResponse = v; return this; }
        public Builder minProjectedRequired(double v) { minProjectedRequired = v; return this; }
        public Builder reverseKnockback(boolean v) { reverseKnockback = v; return this; }
        public Builder directionDot(double v) { directionDot = v; return this; }
        public Builder projectedHorizontal(double v) { projectedHorizontal = v; return this; }
        public Builder impossiblePosition(boolean v) { impossiblePosition = v; return this; }
        public Builder impossibleTicks(int v) { impossibleTicks = v; return this; }
        public Builder maxOutsideDistance(double v) { maxOutsideDistance = v; return this; }
        public Builder evaluatedTickIndex(int v) { evaluatedTickIndex = v; return this; }
        public Builder setbackConfidence(double v) { setbackConfidence = v; return this; }
        public Builder setbackLocation(Location v) { setbackLocation = v; return this; }
        public Builder predictedTicks(List<PredictedTick> v) {
            predictedTicks = v == null ? null : Collections.unmodifiableList(v);
            return this;
        }
        public Builder snapshot(VelocitySnapshot v) { snapshot = v; return this; }
        public Builder debugSummary(String v) { debugSummary = v; return this; }

        public VelocityEvaluationResult build() {
            return new VelocityEvaluationResult(this);
        }
    }
}
