package com.colin.vezanticheat.combat.check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of attack-time accuracy spike analysis.
 */
public final class AccuracySpikeResult {

    public static final AccuracySpikeResult EMPTY = AccuracySpikeResult.builder()
            .averageTrackingYawError(0.0D)
            .attackYawError(0.0D)
            .ratio(0.0D)
            .spikeDetected(false)
            .addReason("accuracySpike=no_history")
            .build();

    private final double averageTrackingYawError;
    private final double attackYawError;
    private final double ratio;
    private final boolean spikeDetected;
    private final double suspiciousScore;
    private final List<String> reasons;

    private AccuracySpikeResult(Builder builder) {
        this.averageTrackingYawError = builder.averageTrackingYawError;
        this.attackYawError = builder.attackYawError;
        this.ratio = builder.ratio;
        this.spikeDetected = builder.spikeDetected;
        this.suspiciousScore = builder.suspiciousScore;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(builder.reasons));
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getAverageTrackingYawError() {
        return averageTrackingYawError;
    }

    public double getAttackYawError() {
        return attackYawError;
    }

    public double getRatio() {
        return ratio;
    }

    public boolean isSpikeDetected() {
        return spikeDetected;
    }

    public double getSuspiciousScore() {
        return suspiciousScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public static final class Builder {

        private double averageTrackingYawError;
        private double attackYawError;
        private double ratio;
        private boolean spikeDetected;
        private double suspiciousScore;
        private final List<String> reasons = new ArrayList<String>();

        public Builder averageTrackingYawError(double averageTrackingYawError) {
            this.averageTrackingYawError = averageTrackingYawError;
            return this;
        }

        public Builder attackYawError(double attackYawError) {
            this.attackYawError = attackYawError;
            return this;
        }

        public Builder ratio(double ratio) {
            this.ratio = ratio;
            return this;
        }

        public Builder spikeDetected(boolean spikeDetected) {
            this.spikeDetected = spikeDetected;
            return this;
        }

        public Builder suspiciousScore(double suspiciousScore) {
            this.suspiciousScore = suspiciousScore;
            return this;
        }

        public Builder addReason(String reason) {
            if (reason != null && !reason.isEmpty()) {
                this.reasons.add(reason);
            }
            return this;
        }

        public Builder reasons(List<String> reasons) {
            this.reasons.clear();
            if (reasons != null) {
                this.reasons.addAll(reasons);
            }
            return this;
        }

        public AccuracySpikeResult build() {
            return new AccuracySpikeResult(this);
        }
    }
}
