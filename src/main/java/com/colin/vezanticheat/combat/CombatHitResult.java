package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.check.AccuracySpikeResult;
import com.colin.vezanticheat.combat.check.AimCorrelationResult;
import com.colin.vezanticheat.combat.check.TargetSwitchResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable outcome of one attack classification pass.
 */
public final class CombatHitResult {

    private final UUID attackerUuid;
    private final UUID targetUuid;
    private final CombatHitClassification classification;
    private final double baseScore;
    private final double finalScore;
    private final double reachDistance;
    private final double yawError;
    private final double pitchError;
    private final boolean normalHitboxHit;
    private final boolean smallExpansionHit;
    private final boolean fullExpansionHit;
    private final boolean lineOfSightValid;
    private final int ping;
    private final double allowedExpansion;
    private final HitboxExpansionTier expansionTier;
    private final boolean pingCompensated;
    private final PreAimResult preAimResult;
    private final AccuracySpikeResult accuracySpikeResult;
    private final AimCorrelationResult aimCorrelationResult;
    private final Vector hitPoint;
    private final HitPointData hitPointData;
    private final TargetSwitchResult targetSwitchResult;
    private final List<String> reasons;

    private CombatHitResult(Builder builder) {
        this.attackerUuid = builder.attackerUuid;
        this.targetUuid = builder.targetUuid;
        this.classification = builder.classification;
        this.baseScore = builder.baseScore;
        this.finalScore = builder.finalScore;
        this.reachDistance = builder.reachDistance;
        this.yawError = builder.yawError;
        this.pitchError = builder.pitchError;
        this.normalHitboxHit = builder.normalHitboxHit;
        this.smallExpansionHit = builder.smallExpansionHit;
        this.fullExpansionHit = builder.fullExpansionHit;
        this.lineOfSightValid = builder.lineOfSightValid;
        this.ping = builder.ping;
        this.allowedExpansion = builder.allowedExpansion;
        this.expansionTier = builder.expansionTier == null ? HitboxExpansionTier.NORMAL : builder.expansionTier;
        this.pingCompensated = builder.pingCompensated;
        this.preAimResult = builder.preAimResult;
        this.accuracySpikeResult = builder.accuracySpikeResult;
        this.aimCorrelationResult = builder.aimCorrelationResult;
        this.hitPoint = builder.hitPoint;
        this.hitPointData = builder.hitPointData;
        this.targetSwitchResult = builder.targetSwitchResult;
        this.reasons = Collections.unmodifiableList(new ArrayList<String>(builder.reasons));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CombatHitResult withReason(CombatHitResult base, String reason) {
        if (base == null) {
            return null;
        }
        if (reason == null || reason.isEmpty()) {
            return base;
        }
        return builder()
                .attackerUuid(base.getAttackerUuid())
                .targetUuid(base.getTargetUuid())
                .classification(base.getClassification())
                .baseScore(base.getBaseScore())
                .finalScore(base.getFinalScore())
                .reachDistance(base.getReachDistance())
                .yawError(base.getYawError())
                .pitchError(base.getPitchError())
                .normalHitboxHit(base.isNormalHitboxHit())
                .smallExpansionHit(base.isSmallExpansionHit())
                .fullExpansionHit(base.isFullExpansionHit())
                .lineOfSightValid(base.isLineOfSightValid())
                .ping(base.getPing())
                .allowedExpansion(base.getAllowedExpansion())
                .expansionTier(base.getExpansionTier())
                .pingCompensated(base.isPingCompensated())
                .preAimResult(base.getPreAimResult())
                .accuracySpikeResult(base.getAccuracySpikeResult())
                .aimCorrelationResult(base.getAimCorrelationResult())
                .hitPoint(base.getHitPoint())
                .hitPointData(base.getHitPointData())
                .targetSwitchResult(base.getTargetSwitchResult())
                .reasons(base.getReasons())
                .addReason(reason)
                .build();
    }

    public UUID getAttackerUuid() {
        return attackerUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public CombatHitClassification getClassification() {
        return classification;
    }

    public double getBaseScore() {
        return baseScore;
    }

    public double getFinalScore() {
        return finalScore;
    }

    /** @deprecated use {@link #getFinalScore()} */
    @Deprecated
    public double getSuspiciousScore() {
        return finalScore;
    }

    public double getReachDistance() {
        return reachDistance;
    }

    public double getYawError() {
        return yawError;
    }

    public double getPitchError() {
        return pitchError;
    }

    public boolean isNormalHitboxHit() {
        return normalHitboxHit;
    }

    public boolean isSmallExpansionHit() {
        return smallExpansionHit;
    }

    public boolean isFullExpansionHit() {
        return fullExpansionHit;
    }

    public boolean isLineOfSightValid() {
        return lineOfSightValid;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public int getPing() {
        return ping;
    }

    public double getAllowedExpansion() {
        return allowedExpansion;
    }

    public HitboxExpansionTier getExpansionTier() {
        return expansionTier;
    }

    public boolean isPingCompensated() {
        return pingCompensated;
    }

    public PreAimResult getPreAimResult() {
        return preAimResult;
    }

    public AccuracySpikeResult getAccuracySpikeResult() {
        return accuracySpikeResult;
    }

    public AimCorrelationResult getAimCorrelationResult() {
        return aimCorrelationResult;
    }

    public Vector getHitPoint() {
        return hitPoint;
    }

    public HitPointData getHitPointData() {
        return hitPointData;
    }

    public TargetSwitchResult getTargetSwitchResult() {
        return targetSwitchResult;
    }

    @Override
    public String toString() {
        return "CombatHitResult{"
                + "attacker=" + attackerUuid
                + ", target=" + targetUuid
                + ", classification=" + classification
                + ", baseScore=" + baseScore
                + ", finalScore=" + finalScore
                + ", reach=" + reachDistance
                + ", yawError=" + yawError
                + ", pitchError=" + pitchError
                + ", ping=" + ping
                + ", allowedExpansion=" + allowedExpansion
                + ", expansionTier=" + expansionTier
                + ", pingCompensated=" + pingCompensated
                + ", preAimResult=" + preAimResult
                + ", accuracySpikeResult=" + accuracySpikeResult
                + ", aimCorrelationResult=" + aimCorrelationResult
                + ", hitPoint=" + hitPoint
                + ", hitPointData=" + hitPointData
                + ", targetSwitchResult=" + targetSwitchResult
                + ", reasons=" + reasons
                + '}';
    }

    public static final class Builder {

        private UUID attackerUuid;
        private UUID targetUuid;
        private CombatHitClassification classification = CombatHitClassification.CLEAN;
        private double baseScore;
        private double finalScore;
        private double reachDistance;
        private double yawError;
        private double pitchError;
        private boolean normalHitboxHit;
        private boolean smallExpansionHit;
        private boolean fullExpansionHit;
        private boolean lineOfSightValid = true;
        private int ping;
        private double allowedExpansion;
        private HitboxExpansionTier expansionTier = HitboxExpansionTier.NORMAL;
        private boolean pingCompensated;
        private PreAimResult preAimResult;
        private AccuracySpikeResult accuracySpikeResult;
        private AimCorrelationResult aimCorrelationResult;
        private Vector hitPoint;
        private HitPointData hitPointData;
        private TargetSwitchResult targetSwitchResult;
        private final List<String> reasons = new ArrayList<String>();

        public Builder attackerUuid(UUID attackerUuid) {
            this.attackerUuid = attackerUuid;
            return this;
        }

        public Builder targetUuid(UUID targetUuid) {
            this.targetUuid = targetUuid;
            return this;
        }

        public Builder classification(CombatHitClassification classification) {
            this.classification = classification;
            return this;
        }

        public Builder baseScore(double baseScore) {
            this.baseScore = baseScore;
            return this;
        }

        public Builder finalScore(double finalScore) {
            this.finalScore = finalScore;
            return this;
        }

        /** @deprecated use {@link #finalScore(double)} */
        @Deprecated
        public Builder suspiciousScore(double suspiciousScore) {
            this.finalScore = suspiciousScore;
            return this;
        }

        public Builder reachDistance(double reachDistance) {
            this.reachDistance = reachDistance;
            return this;
        }

        public Builder yawError(double yawError) {
            this.yawError = yawError;
            return this;
        }

        public Builder pitchError(double pitchError) {
            this.pitchError = pitchError;
            return this;
        }

        public Builder normalHitboxHit(boolean normalHitboxHit) {
            this.normalHitboxHit = normalHitboxHit;
            return this;
        }

        public Builder smallExpansionHit(boolean smallExpansionHit) {
            this.smallExpansionHit = smallExpansionHit;
            return this;
        }

        public Builder fullExpansionHit(boolean fullExpansionHit) {
            this.fullExpansionHit = fullExpansionHit;
            return this;
        }

        public Builder lineOfSightValid(boolean lineOfSightValid) {
            this.lineOfSightValid = lineOfSightValid;
            return this;
        }

        public Builder ping(int ping) {
            this.ping = ping;
            return this;
        }

        public Builder allowedExpansion(double allowedExpansion) {
            this.allowedExpansion = allowedExpansion;
            return this;
        }

        public Builder expansionTier(HitboxExpansionTier expansionTier) {
            this.expansionTier = expansionTier;
            return this;
        }

        public Builder pingCompensated(boolean pingCompensated) {
            this.pingCompensated = pingCompensated;
            return this;
        }

        public Builder preAimResult(PreAimResult preAimResult) {
            this.preAimResult = preAimResult;
            return this;
        }

        public Builder accuracySpikeResult(AccuracySpikeResult accuracySpikeResult) {
            this.accuracySpikeResult = accuracySpikeResult;
            return this;
        }

        public Builder aimCorrelationResult(AimCorrelationResult aimCorrelationResult) {
            this.aimCorrelationResult = aimCorrelationResult;
            return this;
        }

        public Builder hitPoint(Vector hitPoint) {
            this.hitPoint = hitPoint;
            return this;
        }

        public Builder hitPointData(HitPointData hitPointData) {
            this.hitPointData = hitPointData;
            return this;
        }

        public Builder targetSwitchResult(TargetSwitchResult targetSwitchResult) {
            this.targetSwitchResult = targetSwitchResult;
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

        public CombatHitResult build() {
            return new CombatHitResult(this);
        }
    }
}
