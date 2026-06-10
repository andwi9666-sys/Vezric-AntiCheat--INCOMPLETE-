package com.colin.vezanticheat.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of pre-attack rotation history analysis.
 */
public final class PreAimResult {

    public static final PreAimResult EMPTY = PreAimResult.builder()
            .hadPreAim(false)
            .samplesChecked(0)
            .samplesNearTarget(0)
            .closestYawError(Double.MAX_VALUE)
            .closestPitchError(Double.MAX_VALUE)
            .addReason("preAim=no_history")
            .build();

    private final boolean hadPreAim;
    private final int samplesChecked;
    private final int samplesNearTarget;
    private final double averageYawError;
    private final double averagePitchError;
    private final double closestYawError;
    private final double closestPitchError;
    private final double suspiciousScore;
    private final List<String> reasons;

    private PreAimResult(Builder builder) {
        this.hadPreAim = builder.hadPreAim;
        this.samplesChecked = builder.samplesChecked;
        this.samplesNearTarget = builder.samplesNearTarget;
        this.averageYawError = builder.averageYawError;
        this.averagePitchError = builder.averagePitchError;
        this.closestYawError = builder.closestYawError;
        this.closestPitchError = builder.closestPitchError;
        this.suspiciousScore = builder.suspiciousScore;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(builder.reasons));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hadPreAim() {
        return hadPreAim;
    }

    public int getSamplesChecked() {
        return samplesChecked;
    }

    public int getSamplesNearTarget() {
        return samplesNearTarget;
    }

    public double getAverageYawError() {
        return averageYawError;
    }

    public double getAveragePitchError() {
        return averagePitchError;
    }

    public double getClosestYawError() {
        return closestYawError;
    }

    public double getClosestPitchError() {
        return closestPitchError;
    }

    public double getSuspiciousScore() {
        return suspiciousScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public static final class Builder {

        private boolean hadPreAim;
        private int samplesChecked;
        private int samplesNearTarget;
        private double averageYawError;
        private double averagePitchError;
        private double closestYawError = Double.MAX_VALUE;
        private double closestPitchError = Double.MAX_VALUE;
        private double suspiciousScore;
        private final List<String> reasons = new ArrayList<String>();

        public Builder hadPreAim(boolean hadPreAim) {
            this.hadPreAim = hadPreAim;
            return this;
        }

        public Builder samplesChecked(int samplesChecked) {
            this.samplesChecked = samplesChecked;
            return this;
        }

        public Builder samplesNearTarget(int samplesNearTarget) {
            this.samplesNearTarget = samplesNearTarget;
            return this;
        }

        public Builder averageYawError(double averageYawError) {
            this.averageYawError = averageYawError;
            return this;
        }

        public Builder averagePitchError(double averagePitchError) {
            this.averagePitchError = averagePitchError;
            return this;
        }

        public Builder closestYawError(double closestYawError) {
            this.closestYawError = closestYawError;
            return this;
        }

        public Builder closestPitchError(double closestPitchError) {
            this.closestPitchError = closestPitchError;
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

        public PreAimResult build() {
            return new PreAimResult(this);
        }
    }
}
