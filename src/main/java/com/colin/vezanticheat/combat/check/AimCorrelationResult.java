package com.colin.vezanticheat.combat.check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outcome of movement/aim correlation analysis.
 */
public final class AimCorrelationResult {

    public static final AimCorrelationResult EMPTY = AimCorrelationResult.builder()
            .enoughData(false)
            .targetAngularChange(0.0D)
            .attackerYawChange(0.0D)
            .correlationScore(0.0D)
            .suspicious(false)
            .suspiciousScore(0.0D)
            .addReason("aimCorrelation=insufficient_data")
            .build();

    private final boolean enoughData;
    private final double targetAngularChange;
    private final double attackerYawChange;
    private final double correlationScore;
    private final boolean suspicious;
    private final double suspiciousScore;
    private final List<String> reasons;

    private AimCorrelationResult(Builder builder) {
        this.enoughData = builder.enoughData;
        this.targetAngularChange = builder.targetAngularChange;
        this.attackerYawChange = builder.attackerYawChange;
        this.correlationScore = builder.correlationScore;
        this.suspicious = builder.suspicious;
        this.suspiciousScore = builder.suspiciousScore;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(builder.reasons));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean enoughData() {
        return enoughData;
    }

    public double getTargetAngularChange() {
        return targetAngularChange;
    }

    public double getAttackerYawChange() {
        return attackerYawChange;
    }

    public double getCorrelationScore() {
        return correlationScore;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public double getSuspiciousScore() {
        return suspiciousScore;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public static final class Builder {

        private boolean enoughData;
        private double targetAngularChange;
        private double attackerYawChange;
        private double correlationScore;
        private boolean suspicious;
        private double suspiciousScore;
        private final List<String> reasons = new ArrayList<String>();

        public Builder enoughData(boolean enoughData) {
            this.enoughData = enoughData;
            return this;
        }

        public Builder targetAngularChange(double targetAngularChange) {
            this.targetAngularChange = targetAngularChange;
            return this;
        }

        public Builder attackerYawChange(double attackerYawChange) {
            this.attackerYawChange = attackerYawChange;
            return this;
        }

        public Builder correlationScore(double correlationScore) {
            this.correlationScore = correlationScore;
            return this;
        }

        public Builder suspicious(boolean suspicious) {
            this.suspicious = suspicious;
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

        public AimCorrelationResult build() {
            return new AimCorrelationResult(this);
        }
    }
}
