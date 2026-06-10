package com.colin.vezanticheat.combat;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Tunable thresholds for combat hit classification and evidence buffering.
 */
public final class CombatConfig {

    private static final String BASE = "combat-analysis.";

    private double normalHitboxExpansion = 0.0D;
    private double smallHitboxExpansion = 0.03D;
    private double mediumHitboxExpansion = 0.06D;
    private double fullHitboxExpansion = 0.10D;
    private double maxNormalReach = 3.05D;
    private double maxLenientReach = 3.25D;
    private double maxVeryLenientReach = 3.45D;
    private double maxBadReach = 3.70D;
    private int preAimTicks = 5;
    private double preAimNearYawError = 12.0D;
    private double preAimNearPitchError = 15.0D;
    private double preAimStrongYawError = 6.0D;
    private int accuracySpikeTicks = 10;
    private boolean accuracySpikeEnabled = true;
    private double accuracySpikeTrackingErrorThreshold = 15.0D;
    private double accuracySpikeAttackErrorThreshold = 3.0D;
    private double accuracySpikeSevereTrackingErrorThreshold = 25.0D;
    private double accuracySpikeSevereAttackErrorThreshold = 2.0D;
    private boolean targetSwitchEnabled = true;
    private long targetSwitchQuickMs = 250L;
    private long targetSwitchVeryQuickMs = 150L;
    private double targetSwitchLargeAngle = 45.0D;
    private double targetSwitchPerfectHitYawError = 3.0D;
    private int aimCorrelationTicks = 5;
    private int combatEvidenceWindow = 100;
    private double bufferDecayPerSecond = 0.25D;
    private double cancelHitBuffer = 8.0D;
    private double flagBuffer = 15.0D;
    /** Tightest expansion shell allowed; used when ping is at or below {@link #pingThresholdLow}. */
    private double pingExpansionLow = 0.035D;
    private double pingExpansionMid = 0.060D;
    private double pingExpansionHigh = 0.085D;
    /** Widest expansion shell for very high ping; impossible reach still classifies normally. */
    private double pingExpansionMax = 0.100D;
    /** Ping bucket boundary: at or below this, geometry expectations are strictest. */
    private int pingThresholdLow = 50;
    private int pingThresholdMid = 100;
    /** Ping above this bucket uses high-ping score reduction, not immunity. */
    private int pingThresholdHigh = 160;
    private double cleanScore = 0.0D;
    private double lenientScore = 0.5D;
    private double veryLenientScore = 1.5D;
    private double badScore = 3.0D;
    private double impossibleScore = 5.0D;
    private double lowPingFullExpansionExtra = 1.0D;
    private double lowPingMediumExpansionExtra = 0.5D;
    private double highPingReductionMultiplier = 0.70D;
    private int hitDistributionRatioWindow = 50;
    private int hitDistributionMinSamples = 20;
    private int hitDistributionHistorySize = 100;
    private double hitDistributionShellRatioThreshold = 0.35D;
    private double hitDistributionLowPingShellRatioThreshold = 0.25D;
    private int hitDistributionLowPingThreshold = 50;
    private double hitDistributionShellBuffer = 1.0D;
    private double hitDistributionLowPingShellBuffer = 0.75D;
    private long knockbackLeniencyWindowMs = 300L;
    private double knockbackBehaviorMultiplier = 0.75D;
    private long targetKnockbackWindowMs = 300L;
    private double knockbackAimScoreReduction = 0.25D;
    private double knockbackTargetReachBonus = 0.12D;
    private double knockbackTargetExpansionBonus = 0.02D;
    private double maxBuffer = 30.0D;
    private double punishBuffer = 25.0D;
    private double cleanHitBufferDecay = 0.15D;
    private long fpJoinSkipMs = 5000L;
    private long fpTeleportSkipMs = 750L;
    private boolean fpTeleportLenientImpossible = true;
    private double fpCloseRangeBlocks = 1.2D;
    private int fpHighAttackerPing = 250;
    private int fpHighTargetPing = 250;
    private double fpTpsReduceThreshold = 18.5D;
    private double fpTpsAlertSuppressThreshold = 17.5D;
    private double fpTpsSuspicionMultiplier = 0.50D;
    private double fpAttackerPingAimMultiplier = 0.35D;
    private double fpTargetPingReachMultiplier = 0.50D;
    private double fpCloseRangeAimMultiplier = 0.60D;
    private double fpTightSpaceMultiplier = 0.70D;
    private double fpNearBlockMultiplier = 0.80D;
    private double fpVelocityMultiplier = 0.75D;
    private double fpVerticalOffsetMultiplier = 0.75D;
    private double fpVerticalOffsetBlocks = 1.5D;
    private double fpRecentTeleportMultiplier = 0.50D;

    public CombatConfig() {
    }

    public CombatConfig(CombatConfig other) {
        if (other == null) return;
        this.normalHitboxExpansion = other.normalHitboxExpansion;
        this.smallHitboxExpansion = other.smallHitboxExpansion;
        this.mediumHitboxExpansion = other.mediumHitboxExpansion;
        this.fullHitboxExpansion = other.fullHitboxExpansion;
        this.maxNormalReach = other.maxNormalReach;
        this.maxLenientReach = other.maxLenientReach;
        this.maxVeryLenientReach = other.maxVeryLenientReach;
        this.maxBadReach = other.maxBadReach;
        this.preAimTicks = other.preAimTicks;
        this.preAimNearYawError = other.preAimNearYawError;
        this.preAimNearPitchError = other.preAimNearPitchError;
        this.preAimStrongYawError = other.preAimStrongYawError;
        this.accuracySpikeTicks = other.accuracySpikeTicks;
        this.accuracySpikeEnabled = other.accuracySpikeEnabled;
        this.accuracySpikeTrackingErrorThreshold = other.accuracySpikeTrackingErrorThreshold;
        this.accuracySpikeAttackErrorThreshold = other.accuracySpikeAttackErrorThreshold;
        this.accuracySpikeSevereTrackingErrorThreshold = other.accuracySpikeSevereTrackingErrorThreshold;
        this.accuracySpikeSevereAttackErrorThreshold = other.accuracySpikeSevereAttackErrorThreshold;
        this.targetSwitchEnabled = other.targetSwitchEnabled;
        this.targetSwitchQuickMs = other.targetSwitchQuickMs;
        this.targetSwitchVeryQuickMs = other.targetSwitchVeryQuickMs;
        this.targetSwitchLargeAngle = other.targetSwitchLargeAngle;
        this.targetSwitchPerfectHitYawError = other.targetSwitchPerfectHitYawError;
        this.aimCorrelationTicks = other.aimCorrelationTicks;
        this.combatEvidenceWindow = other.combatEvidenceWindow;
        this.bufferDecayPerSecond = other.bufferDecayPerSecond;
        this.cancelHitBuffer = other.cancelHitBuffer;
        this.flagBuffer = other.flagBuffer;
        this.pingExpansionLow = other.pingExpansionLow;
        this.pingExpansionMid = other.pingExpansionMid;
        this.pingExpansionHigh = other.pingExpansionHigh;
        this.pingExpansionMax = other.pingExpansionMax;
        this.pingThresholdLow = other.pingThresholdLow;
        this.pingThresholdMid = other.pingThresholdMid;
        this.pingThresholdHigh = other.pingThresholdHigh;
        this.cleanScore = other.cleanScore;
        this.lenientScore = other.lenientScore;
        this.veryLenientScore = other.veryLenientScore;
        this.badScore = other.badScore;
        this.impossibleScore = other.impossibleScore;
        this.lowPingFullExpansionExtra = other.lowPingFullExpansionExtra;
        this.lowPingMediumExpansionExtra = other.lowPingMediumExpansionExtra;
        this.highPingReductionMultiplier = other.highPingReductionMultiplier;
        this.hitDistributionRatioWindow = other.hitDistributionRatioWindow;
        this.hitDistributionMinSamples = other.hitDistributionMinSamples;
        this.hitDistributionHistorySize = other.hitDistributionHistorySize;
        this.hitDistributionShellRatioThreshold = other.hitDistributionShellRatioThreshold;
        this.hitDistributionLowPingShellRatioThreshold = other.hitDistributionLowPingShellRatioThreshold;
        this.hitDistributionLowPingThreshold = other.hitDistributionLowPingThreshold;
        this.hitDistributionShellBuffer = other.hitDistributionShellBuffer;
        this.hitDistributionLowPingShellBuffer = other.hitDistributionLowPingShellBuffer;
        this.knockbackLeniencyWindowMs = other.knockbackLeniencyWindowMs;
        this.knockbackBehaviorMultiplier = other.knockbackBehaviorMultiplier;
        this.targetKnockbackWindowMs = other.targetKnockbackWindowMs;
        this.knockbackAimScoreReduction = other.knockbackAimScoreReduction;
        this.knockbackTargetReachBonus = other.knockbackTargetReachBonus;
        this.knockbackTargetExpansionBonus = other.knockbackTargetExpansionBonus;
        this.maxBuffer = other.maxBuffer;
        this.punishBuffer = other.punishBuffer;
        this.cleanHitBufferDecay = other.cleanHitBufferDecay;
        this.fpJoinSkipMs = other.fpJoinSkipMs;
        this.fpTeleportSkipMs = other.fpTeleportSkipMs;
        this.fpTeleportLenientImpossible = other.fpTeleportLenientImpossible;
        this.fpCloseRangeBlocks = other.fpCloseRangeBlocks;
        this.fpHighAttackerPing = other.fpHighAttackerPing;
        this.fpHighTargetPing = other.fpHighTargetPing;
        this.fpTpsReduceThreshold = other.fpTpsReduceThreshold;
        this.fpTpsAlertSuppressThreshold = other.fpTpsAlertSuppressThreshold;
        this.fpTpsSuspicionMultiplier = other.fpTpsSuspicionMultiplier;
        this.fpAttackerPingAimMultiplier = other.fpAttackerPingAimMultiplier;
        this.fpTargetPingReachMultiplier = other.fpTargetPingReachMultiplier;
        this.fpCloseRangeAimMultiplier = other.fpCloseRangeAimMultiplier;
        this.fpTightSpaceMultiplier = other.fpTightSpaceMultiplier;
        this.fpNearBlockMultiplier = other.fpNearBlockMultiplier;
        this.fpVelocityMultiplier = other.fpVelocityMultiplier;
        this.fpVerticalOffsetMultiplier = other.fpVerticalOffsetMultiplier;
        this.fpVerticalOffsetBlocks = other.fpVerticalOffsetBlocks;
        this.fpRecentTeleportMultiplier = other.fpRecentTeleportMultiplier;
    }

    public static CombatConfig defaults() {
        return new CombatConfig();
    }

    public static CombatConfig fromConfig(FileConfiguration config) {
        CombatConfig cfg = defaults();
        cfg.loadFrom(config);
        return cfg;
    }

    public void loadFrom(FileConfiguration config) {
        if (config == null) {
            return;
        }

        maxNormalReach = config.getDouble(BASE + "reach.max-normal-reach", maxNormalReach);
        maxLenientReach = config.getDouble(BASE + "reach.max-lenient-reach", maxLenientReach);
        maxVeryLenientReach = config.getDouble(BASE + "reach.max-very-lenient-reach", maxVeryLenientReach);
        maxBadReach = config.getDouble(BASE + "reach.max-bad-reach", maxBadReach);

        smallHitboxExpansion = config.getDouble(BASE + "hitbox-expansion.small", smallHitboxExpansion);
        mediumHitboxExpansion = config.getDouble(BASE + "hitbox-expansion.medium", mediumHitboxExpansion);
        fullHitboxExpansion = config.getDouble(BASE + "hitbox-expansion.full", fullHitboxExpansion);
        pingThresholdLow = config.getInt(BASE + "hitbox-expansion.low-ping-max", pingThresholdLow);
        pingThresholdMid = config.getInt(BASE + "hitbox-expansion.mid-ping-max", pingThresholdMid);
        pingThresholdHigh = config.getInt(BASE + "hitbox-expansion.high-ping-max", pingThresholdHigh);

        cleanScore = config.getDouble(BASE + "scoring.clean-score", cleanScore);
        lenientScore = config.getDouble(BASE + "scoring.lenient-score", lenientScore);
        veryLenientScore = config.getDouble(BASE + "scoring.very-lenient-score", veryLenientScore);
        badScore = config.getDouble(BASE + "scoring.bad-score", badScore);
        impossibleScore = config.getDouble(BASE + "scoring.impossible-score", impossibleScore);
        lowPingFullExpansionExtra = config.getDouble(
                BASE + "scoring.low-ping-full-expansion-extra", lowPingFullExpansionExtra);
        lowPingMediumExpansionExtra = config.getDouble(
                BASE + "scoring.low-ping-medium-expansion-extra", lowPingMediumExpansionExtra);
        highPingReductionMultiplier = config.getDouble(
                BASE + "scoring.high-ping-reduction-multiplier", highPingReductionMultiplier);

        preAimTicks = config.getInt(BASE + "pre-aim.ticks", preAimTicks);
        preAimNearYawError = config.getDouble(BASE + "pre-aim.near-yaw-error", preAimNearYawError);
        preAimNearPitchError = config.getDouble(BASE + "pre-aim.near-pitch-error", preAimNearPitchError);
        preAimStrongYawError = config.getDouble(BASE + "pre-aim.strong-yaw-error", preAimStrongYawError);

        accuracySpikeEnabled = config.getBoolean(BASE + "accuracy-spike.enabled", accuracySpikeEnabled);
        accuracySpikeTrackingErrorThreshold = config.getDouble(
                BASE + "accuracy-spike.tracking-error-threshold", accuracySpikeTrackingErrorThreshold);
        accuracySpikeAttackErrorThreshold = config.getDouble(
                BASE + "accuracy-spike.attack-error-threshold", accuracySpikeAttackErrorThreshold);
        accuracySpikeSevereTrackingErrorThreshold = config.getDouble(
                BASE + "accuracy-spike.severe-tracking-error-threshold", accuracySpikeSevereTrackingErrorThreshold);
        accuracySpikeSevereAttackErrorThreshold = config.getDouble(
                BASE + "accuracy-spike.severe-attack-error-threshold", accuracySpikeSevereAttackErrorThreshold);

        targetSwitchEnabled = config.getBoolean(BASE + "target-switch.enabled", targetSwitchEnabled);
        targetSwitchQuickMs = config.getLong(BASE + "target-switch.quick-switch-ms", targetSwitchQuickMs);
        targetSwitchVeryQuickMs = config.getLong(BASE + "target-switch.very-quick-switch-ms", targetSwitchVeryQuickMs);
        targetSwitchLargeAngle = config.getDouble(BASE + "target-switch.large-angle", targetSwitchLargeAngle);
        targetSwitchPerfectHitYawError = config.getDouble(
                BASE + "target-switch.perfect-hit-yaw-error", targetSwitchPerfectHitYawError);

        knockbackLeniencyWindowMs = config.getLong(BASE + "leniency.recent-knockback-ms", knockbackLeniencyWindowMs);
        knockbackBehaviorMultiplier = config.getDouble(
                BASE + "leniency.recent-knockback-suspicion-multiplier", knockbackBehaviorMultiplier);
        targetKnockbackWindowMs = config.getLong(
                BASE + "leniency.target-recent-velocity-ms", targetKnockbackWindowMs);
        knockbackAimScoreReduction = Math.max(0.0D, 1.0D - knockbackBehaviorMultiplier);

        bufferDecayPerSecond = config.getDouble(BASE + "buffers.decay-per-second", bufferDecayPerSecond);
        cancelHitBuffer = config.getDouble(BASE + "buffers.cancel-buffer",
                config.getDouble(BASE + "cancel-buffer", cancelHitBuffer));
        flagBuffer = config.getDouble(BASE + "buffers.alert-buffer",
                config.getDouble(BASE + "alert-buffer", flagBuffer));
        maxBuffer = config.getDouble(BASE + "buffers.max-buffer", maxBuffer);
        punishBuffer = config.getDouble(BASE + "punish-buffer", punishBuffer);

        fpJoinSkipMs = config.getLong(BASE + "false-positive.join-skip-ms", fpJoinSkipMs);
        fpTeleportSkipMs = config.getLong(BASE + "false-positive.teleport-skip-ms", fpTeleportSkipMs);
        fpTeleportLenientImpossible = config.getBoolean(
                BASE + "false-positive.teleport-lenient-impossible", fpTeleportLenientImpossible);
        fpCloseRangeBlocks = config.getDouble(BASE + "false-positive.close-range-blocks", fpCloseRangeBlocks);
        fpHighAttackerPing = config.getInt(BASE + "false-positive.high-attacker-ping", fpHighAttackerPing);
        fpHighTargetPing = config.getInt(BASE + "false-positive.high-target-ping", fpHighTargetPing);
        fpTpsReduceThreshold = config.getDouble(BASE + "false-positive.tps-reduce-threshold", fpTpsReduceThreshold);
        fpTpsAlertSuppressThreshold = config.getDouble(
                BASE + "false-positive.tps-alert-suppress-threshold", fpTpsAlertSuppressThreshold);
        fpTpsSuspicionMultiplier = config.getDouble(
                BASE + "false-positive.tps-suspicion-multiplier", fpTpsSuspicionMultiplier);
        fpAttackerPingAimMultiplier = config.getDouble(
                BASE + "false-positive.attacker-ping-aim-multiplier", fpAttackerPingAimMultiplier);
        fpTargetPingReachMultiplier = config.getDouble(
                BASE + "false-positive.target-ping-reach-multiplier", fpTargetPingReachMultiplier);
        fpCloseRangeAimMultiplier = config.getDouble(
                BASE + "false-positive.close-range-aim-multiplier", fpCloseRangeAimMultiplier);
        fpTightSpaceMultiplier = config.getDouble(
                BASE + "false-positive.tight-space-multiplier", fpTightSpaceMultiplier);
        fpNearBlockMultiplier = config.getDouble(BASE + "false-positive.near-block-multiplier", fpNearBlockMultiplier);
        fpVelocityMultiplier = config.getDouble(BASE + "false-positive.velocity-multiplier", fpVelocityMultiplier);
        fpVerticalOffsetMultiplier = config.getDouble(
                BASE + "false-positive.vertical-offset-multiplier", fpVerticalOffsetMultiplier);
        fpVerticalOffsetBlocks = config.getDouble(
                BASE + "false-positive.vertical-offset-blocks", fpVerticalOffsetBlocks);
        fpRecentTeleportMultiplier = config.getDouble(
                BASE + "false-positive.recent-teleport-multiplier", fpRecentTeleportMultiplier);
    }

    public double getNormalHitboxExpansion() {
        return normalHitboxExpansion;
    }

    public void setNormalHitboxExpansion(double normalHitboxExpansion) {
        this.normalHitboxExpansion = normalHitboxExpansion;
    }

    public double getSmallHitboxExpansion() {
        return smallHitboxExpansion;
    }

    public void setSmallHitboxExpansion(double smallHitboxExpansion) {
        this.smallHitboxExpansion = smallHitboxExpansion;
    }

    public double getMediumHitboxExpansion() {
        return mediumHitboxExpansion;
    }

    public void setMediumHitboxExpansion(double mediumHitboxExpansion) {
        this.mediumHitboxExpansion = mediumHitboxExpansion;
    }

    public double getFullHitboxExpansion() {
        return fullHitboxExpansion;
    }

    public void setFullHitboxExpansion(double fullHitboxExpansion) {
        this.fullHitboxExpansion = fullHitboxExpansion;
    }

    public double getMaxNormalReach() {
        return maxNormalReach;
    }

    public void setMaxNormalReach(double maxNormalReach) {
        this.maxNormalReach = maxNormalReach;
    }

    public double getMaxLenientReach() {
        return maxLenientReach;
    }

    public void setMaxLenientReach(double maxLenientReach) {
        this.maxLenientReach = maxLenientReach;
    }

    public double getMaxVeryLenientReach() {
        return maxVeryLenientReach;
    }

    public void setMaxVeryLenientReach(double maxVeryLenientReach) {
        this.maxVeryLenientReach = maxVeryLenientReach;
    }

    public double getMaxBadReach() {
        return maxBadReach;
    }

    public void setMaxBadReach(double maxBadReach) {
        this.maxBadReach = maxBadReach;
    }

    public int getPreAimTicks() {
        return preAimTicks;
    }

    public void setPreAimTicks(int preAimTicks) {
        this.preAimTicks = preAimTicks;
    }

    public double getPreAimNearYawError() {
        return preAimNearYawError;
    }

    public double getPreAimNearPitchError() {
        return preAimNearPitchError;
    }

    public double getPreAimStrongYawError() {
        return preAimStrongYawError;
    }

    public int getAccuracySpikeTicks() {
        return accuracySpikeTicks;
    }

    public void setAccuracySpikeTicks(int accuracySpikeTicks) {
        this.accuracySpikeTicks = accuracySpikeTicks;
    }

    public boolean isAccuracySpikeEnabled() {
        return accuracySpikeEnabled;
    }

    public double getAccuracySpikeTrackingErrorThreshold() {
        return accuracySpikeTrackingErrorThreshold;
    }

    public double getAccuracySpikeAttackErrorThreshold() {
        return accuracySpikeAttackErrorThreshold;
    }

    public double getAccuracySpikeSevereTrackingErrorThreshold() {
        return accuracySpikeSevereTrackingErrorThreshold;
    }

    public double getAccuracySpikeSevereAttackErrorThreshold() {
        return accuracySpikeSevereAttackErrorThreshold;
    }

    public boolean isTargetSwitchEnabled() {
        return targetSwitchEnabled;
    }

    public long getTargetSwitchQuickMs() {
        return targetSwitchQuickMs;
    }

    public long getTargetSwitchVeryQuickMs() {
        return targetSwitchVeryQuickMs;
    }

    public double getTargetSwitchLargeAngle() {
        return targetSwitchLargeAngle;
    }

    public double getTargetSwitchPerfectHitYawError() {
        return targetSwitchPerfectHitYawError;
    }

    public int getAimCorrelationTicks() {
        return aimCorrelationTicks;
    }

    public void setAimCorrelationTicks(int aimCorrelationTicks) {
        this.aimCorrelationTicks = aimCorrelationTicks;
    }

    public int getCombatEvidenceWindow() {
        return combatEvidenceWindow;
    }

    public void setCombatEvidenceWindow(int combatEvidenceWindow) {
        this.combatEvidenceWindow = combatEvidenceWindow;
    }

    public double getBufferDecayPerSecond() {
        return bufferDecayPerSecond;
    }

    public void setBufferDecayPerSecond(double bufferDecayPerSecond) {
        this.bufferDecayPerSecond = bufferDecayPerSecond;
    }

    public double getCancelHitBuffer() {
        return cancelHitBuffer;
    }

    public void setCancelHitBuffer(double cancelHitBuffer) {
        this.cancelHitBuffer = cancelHitBuffer;
    }

    public double getFlagBuffer() {
        return flagBuffer;
    }

    public void setFlagBuffer(double flagBuffer) {
        this.flagBuffer = flagBuffer;
    }

    public double getCleanScore() {
        return cleanScore;
    }

    public double getLenientScore() {
        return lenientScore;
    }

    public double getVeryLenientScore() {
        return veryLenientScore;
    }

    public double getBadScore() {
        return badScore;
    }

    public double getImpossibleScore() {
        return impossibleScore;
    }

    public double getLowPingFullExpansionExtra() {
        return lowPingFullExpansionExtra;
    }

    public double getLowPingMediumExpansionExtra() {
        return lowPingMediumExpansionExtra;
    }

    public double getHighPingReductionMultiplier() {
        return highPingReductionMultiplier;
    }

    public int getPingThresholdLow() {
        return pingThresholdLow;
    }

    public int getPingThresholdMid() {
        return pingThresholdMid;
    }

    public int getPingThresholdHigh() {
        return pingThresholdHigh;
    }

    /**
     * Maximum hitbox expansion allowed for a player's ping bucket.
     * Lower ping maps to smaller shells, so the same physical miss is graded stricter.
     */
    public double getAllowedExpansionForPing(int ping) {
        ping = Math.max(0, ping);
        if (ping <= pingThresholdLow) {
            return pingExpansionLow;
        }
        if (ping <= pingThresholdMid) {
            return pingExpansionMid;
        }
        if (ping <= pingThresholdHigh) {
            return pingExpansionHigh;
        }
        return pingExpansionMax;
    }

    /**
     * Maps a ping-allowed expansion value to the nearest expansion tier.
     */
    public HitboxExpansionTier tierForAllowedExpansion(double allowedExpansion) {
        if (allowedExpansion <= pingExpansionLow) {
            return HitboxExpansionTier.SMALL;
        }
        if (allowedExpansion <= pingExpansionMid) {
            return HitboxExpansionTier.MEDIUM;
        }
        return HitboxExpansionTier.FULL;
    }

    public void setPingExpansionLow(double pingExpansionLow) {
        this.pingExpansionLow = pingExpansionLow;
    }

    public void setPingExpansionMid(double pingExpansionMid) {
        this.pingExpansionMid = pingExpansionMid;
    }

    public void setPingExpansionHigh(double pingExpansionHigh) {
        this.pingExpansionHigh = pingExpansionHigh;
    }

    public void setPingExpansionMax(double pingExpansionMax) {
        this.pingExpansionMax = pingExpansionMax;
    }

    public void setPingThresholdLow(int pingThresholdLow) {
        this.pingThresholdLow = pingThresholdLow;
    }

    public void setPingThresholdMid(int pingThresholdMid) {
        this.pingThresholdMid = pingThresholdMid;
    }

    public void setPingThresholdHigh(int pingThresholdHigh) {
        this.pingThresholdHigh = pingThresholdHigh;
    }

    public int getHitDistributionRatioWindow() {
        return hitDistributionRatioWindow;
    }

    public void setHitDistributionRatioWindow(int hitDistributionRatioWindow) {
        this.hitDistributionRatioWindow = hitDistributionRatioWindow;
    }

    public int getHitDistributionMinSamples() {
        return hitDistributionMinSamples;
    }

    public void setHitDistributionMinSamples(int hitDistributionMinSamples) {
        this.hitDistributionMinSamples = hitDistributionMinSamples;
    }

    public int getHitDistributionHistorySize() {
        return hitDistributionHistorySize;
    }

    public void setHitDistributionHistorySize(int hitDistributionHistorySize) {
        this.hitDistributionHistorySize = hitDistributionHistorySize;
    }

    public double getHitDistributionShellRatioThreshold() {
        return hitDistributionShellRatioThreshold;
    }

    public void setHitDistributionShellRatioThreshold(double hitDistributionShellRatioThreshold) {
        this.hitDistributionShellRatioThreshold = hitDistributionShellRatioThreshold;
    }

    public double getHitDistributionLowPingShellRatioThreshold() {
        return hitDistributionLowPingShellRatioThreshold;
    }

    public void setHitDistributionLowPingShellRatioThreshold(double hitDistributionLowPingShellRatioThreshold) {
        this.hitDistributionLowPingShellRatioThreshold = hitDistributionLowPingShellRatioThreshold;
    }

    public int getHitDistributionLowPingThreshold() {
        return hitDistributionLowPingThreshold;
    }

    public void setHitDistributionLowPingThreshold(int hitDistributionLowPingThreshold) {
        this.hitDistributionLowPingThreshold = hitDistributionLowPingThreshold;
    }

    public double getHitDistributionShellBuffer() {
        return hitDistributionShellBuffer;
    }

    public void setHitDistributionShellBuffer(double hitDistributionShellBuffer) {
        this.hitDistributionShellBuffer = hitDistributionShellBuffer;
    }

    public double getHitDistributionLowPingShellBuffer() {
        return hitDistributionLowPingShellBuffer;
    }

    public void setHitDistributionLowPingShellBuffer(double hitDistributionLowPingShellBuffer) {
        this.hitDistributionLowPingShellBuffer = hitDistributionLowPingShellBuffer;
    }

    public long getKnockbackLeniencyWindowMs() {
        return knockbackLeniencyWindowMs;
    }

    public void setKnockbackLeniencyWindowMs(long knockbackLeniencyWindowMs) {
        this.knockbackLeniencyWindowMs = knockbackLeniencyWindowMs;
    }

    public double getKnockbackBehaviorMultiplier() {
        return knockbackBehaviorMultiplier;
    }

    public long getTargetKnockbackWindowMs() {
        return targetKnockbackWindowMs;
    }

    public double getKnockbackAimScoreReduction() {
        return knockbackAimScoreReduction;
    }

    public void setKnockbackAimScoreReduction(double knockbackAimScoreReduction) {
        this.knockbackAimScoreReduction = knockbackAimScoreReduction;
    }

    public double getKnockbackTargetReachBonus() {
        return knockbackTargetReachBonus;
    }

    public void setKnockbackTargetReachBonus(double knockbackTargetReachBonus) {
        this.knockbackTargetReachBonus = knockbackTargetReachBonus;
    }

    public double getKnockbackTargetExpansionBonus() {
        return knockbackTargetExpansionBonus;
    }

    public void setKnockbackTargetExpansionBonus(double knockbackTargetExpansionBonus) {
        this.knockbackTargetExpansionBonus = knockbackTargetExpansionBonus;
    }

    public double getMaxBuffer() {
        return maxBuffer;
    }

    public void setMaxBuffer(double maxBuffer) {
        this.maxBuffer = maxBuffer;
    }

    public double getPunishBuffer() {
        return punishBuffer;
    }

    public void setPunishBuffer(double punishBuffer) {
        this.punishBuffer = punishBuffer;
    }

    public double getCleanHitBufferDecay() {
        return cleanHitBufferDecay;
    }

    public void setCleanHitBufferDecay(double cleanHitBufferDecay) {
        this.cleanHitBufferDecay = cleanHitBufferDecay;
    }

    public long getFpJoinSkipMs() {
        return fpJoinSkipMs;
    }

    public long getFpTeleportSkipMs() {
        return fpTeleportSkipMs;
    }

    public boolean isFpTeleportLenientImpossible() {
        return fpTeleportLenientImpossible;
    }

    public double getFpCloseRangeBlocks() {
        return fpCloseRangeBlocks;
    }

    public int getFpHighAttackerPing() {
        return fpHighAttackerPing;
    }

    public int getFpHighTargetPing() {
        return fpHighTargetPing;
    }

    public double getFpTpsReduceThreshold() {
        return fpTpsReduceThreshold;
    }

    public double getFpTpsAlertSuppressThreshold() {
        return fpTpsAlertSuppressThreshold;
    }

    public double getFpTpsSuspicionMultiplier() {
        return fpTpsSuspicionMultiplier;
    }

    public double getFpAttackerPingAimMultiplier() {
        return fpAttackerPingAimMultiplier;
    }

    public double getFpTargetPingReachMultiplier() {
        return fpTargetPingReachMultiplier;
    }

    public double getFpCloseRangeAimMultiplier() {
        return fpCloseRangeAimMultiplier;
    }

    public double getFpTightSpaceMultiplier() {
        return fpTightSpaceMultiplier;
    }

    public double getFpNearBlockMultiplier() {
        return fpNearBlockMultiplier;
    }

    public double getFpVelocityMultiplier() {
        return fpVelocityMultiplier;
    }

    public double getFpVerticalOffsetMultiplier() {
        return fpVerticalOffsetMultiplier;
    }

    public double getFpVerticalOffsetBlocks() {
        return fpVerticalOffsetBlocks;
    }

    public double getFpRecentTeleportMultiplier() {
        return fpRecentTeleportMultiplier;
    }
}
