package com.colin.vezanticheat.combat.check;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of target-switch realism analysis.
 */
public final class TargetSwitchResult {

    public static final TargetSwitchResult EMPTY = TargetSwitchResult.builder()
            .switchedTarget(false)
            .suspicious(false)
            .suspiciousScore(0.0D)
            .addReason("targetSwitch=none")
            .build();

    private final boolean switchedTarget;
    private final UUID previousTarget;
    private final UUID currentTarget;
    private final long timeSinceLastTarget;
    private final double angleBetweenTargets;
    private final boolean suspicious;
    private final double suspiciousScore;
    private final List<String> reasons;

    private TargetSwitchResult(Builder builder) {
        this.switchedTarget = builder.switchedTarget;
        this.previousTarget = builder.previousTarget;
        this.currentTarget = builder.currentTarget;
        this.timeSinceLastTarget = builder.timeSinceLastTarget;
        this.angleBetweenTargets = builder.angleBetweenTargets;
        this.suspicious = builder.suspicious;
        this.suspiciousScore = builder.suspiciousScore;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(builder.reasons));
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isSwitchedTarget() {
        return switchedTarget;
    }

    public UUID getPreviousTarget() {
        return previousTarget;
    }

    public UUID getCurrentTarget() {
        return currentTarget;
    }

    public long getTimeSinceLastTarget() {
        return timeSinceLastTarget;
    }

    public double getAngleBetweenTargets() {
        return angleBetweenTargets;
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

        private boolean switchedTarget;
        private UUID previousTarget;
        private UUID currentTarget;
        private long timeSinceLastTarget;
        private double angleBetweenTargets;
        private boolean suspicious;
        private double suspiciousScore;
        private final List<String> reasons = new ArrayList<String>();

        public Builder switchedTarget(boolean switchedTarget) {
            this.switchedTarget = switchedTarget;
            return this;
        }

        public Builder previousTarget(UUID previousTarget) {
            this.previousTarget = previousTarget;
            return this;
        }

        public Builder currentTarget(UUID currentTarget) {
            this.currentTarget = currentTarget;
            return this;
        }

        public Builder timeSinceLastTarget(long timeSinceLastTarget) {
            this.timeSinceLastTarget = timeSinceLastTarget;
            return this;
        }

        public Builder angleBetweenTargets(double angleBetweenTargets) {
            this.angleBetweenTargets = angleBetweenTargets;
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

        public TargetSwitchResult build() {
            return new TargetSwitchResult(this);
        }
    }
}
