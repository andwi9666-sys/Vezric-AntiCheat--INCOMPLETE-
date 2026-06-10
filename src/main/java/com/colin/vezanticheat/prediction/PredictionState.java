package com.colin.vezanticheat.prediction;

import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocitySession;
import org.bukkit.Location;

public final class PredictionState {

    private double lastMotionX;
    private double lastMotionY;
    private double lastMotionZ;
    private long lastMovementTimeMs;

    // Observed motion (raw packet delta, never clamped) - used for friction carry calculations
    private double lastObservedMotionX;
    private double lastObservedMotionY;
    private double lastObservedMotionZ;

    private Location lastKnownGoodLocation;
    private long lastKnownGoodTimeMs;
    private Location lastValidGroundSetbackLocation;
    private long lastValidGroundSetbackTimeMs;
    private long lastSetbackMs;
    private boolean setbackPending;
    private String lastSetbackReason;

    private PredictionResult lastResult;
    private VelocitySession activeVelocitySession;
    private VelocityEvaluationResult lastVelocityResult;
    private int recentPositionlessTicks;
    private int consecutiveSneakTicks;
    private boolean lastPacketSneaking;
    private int recentGroundStateTicks;
    private boolean hiddenGroundPending;
    private boolean lastClientGround;
    private boolean lastResolvedGround;
    private int velocityCorrectionSequence;

    public double getLastMotionX() {
        return lastMotionX;
    }

    public void setLastMotionX(double lastMotionX) {
        this.lastMotionX = lastMotionX;
    }

    public double getLastMotionY() {
        return lastMotionY;
    }

    public void setLastMotionY(double lastMotionY) {
        this.lastMotionY = lastMotionY;
    }

    public double getLastMotionZ() {
        return lastMotionZ;
    }

    public void setLastMotionZ(double lastMotionZ) {
        this.lastMotionZ = lastMotionZ;
    }

    public double getLastObservedMotionX() {
        return lastObservedMotionX;
    }

    public void setLastObservedMotionX(double lastObservedMotionX) {
        this.lastObservedMotionX = lastObservedMotionX;
    }

    public double getLastObservedMotionY() {
        return lastObservedMotionY;
    }

    public void setLastObservedMotionY(double lastObservedMotionY) {
        this.lastObservedMotionY = lastObservedMotionY;
    }

    public double getLastObservedMotionZ() {
        return lastObservedMotionZ;
    }

    public void setLastObservedMotionZ(double lastObservedMotionZ) {
        this.lastObservedMotionZ = lastObservedMotionZ;
    }

    public long getLastMovementTimeMs() {
        return lastMovementTimeMs;
    }

    public void setLastMovementTimeMs(long lastMovementTimeMs) {
        this.lastMovementTimeMs = lastMovementTimeMs;
    }

    public Location getLastKnownGoodLocation() {
        return lastKnownGoodLocation == null ? null : lastKnownGoodLocation.clone();
    }

    public void setLastKnownGoodLocation(Location lastKnownGoodLocation, long nowMs) {
        this.lastKnownGoodLocation = lastKnownGoodLocation == null ? null : lastKnownGoodLocation.clone();
        this.lastKnownGoodTimeMs = nowMs;
    }

    public long getLastKnownGoodTimeMs() {
        return lastKnownGoodTimeMs;
    }

    public Location getLastValidGroundSetbackLocation() {
        return lastValidGroundSetbackLocation == null ? null : lastValidGroundSetbackLocation.clone();
    }

    public void setLastValidGroundSetbackLocation(Location location, long nowMs) {
        this.lastValidGroundSetbackLocation = location == null ? null : location.clone();
        this.lastValidGroundSetbackTimeMs = nowMs;
    }

    public long getLastValidGroundSetbackTimeMs() {
        return lastValidGroundSetbackTimeMs;
    }

    public long getLastSetbackMs() {
        return lastSetbackMs;
    }

    public void setLastSetbackMs(long lastSetbackMs) {
        this.lastSetbackMs = lastSetbackMs;
    }

    public boolean isSetbackPending() {
        return setbackPending;
    }

    public void setSetbackPending(boolean setbackPending) {
        this.setbackPending = setbackPending;
    }

    public String getLastSetbackReason() {
        return lastSetbackReason;
    }

    public void setLastSetbackReason(String lastSetbackReason) {
        this.lastSetbackReason = lastSetbackReason;
    }

    public PredictionResult getLastResult() {
        return lastResult;
    }

    public void setLastResult(PredictionResult lastResult) {
        this.lastResult = lastResult;
    }

    public VelocitySession getActiveVelocitySession() {
        return activeVelocitySession;
    }

    public void setActiveVelocitySession(VelocitySession activeVelocitySession) {
        this.activeVelocitySession = activeVelocitySession;
    }

    public VelocityEvaluationResult getLastVelocityResult() {
        return lastVelocityResult;
    }

    public void setLastVelocityResult(VelocityEvaluationResult lastVelocityResult) {
        this.lastVelocityResult = lastVelocityResult;
    }

    public int nextVelocityCorrectionSequence() {
        return ++velocityCorrectionSequence;
    }

    public int getVelocityCorrectionSequence() {
        return velocityCorrectionSequence;
    }

    public void invalidateVelocityCorrection() {
        velocityCorrectionSequence++;
    }

    public int getRecentPositionlessTicks() {
        return recentPositionlessTicks;
    }

    public void incrementRecentPositionlessTicks() {
        this.recentPositionlessTicks = Math.min(3, this.recentPositionlessTicks + 1);
    }

    public int consumeRecentPositionlessTicks() {
        int value = recentPositionlessTicks;
        recentPositionlessTicks = 0;
        return value;
    }

    public int getConsecutiveSneakTicks() {
        return consecutiveSneakTicks;
    }

    public void recordSneakState(boolean sneaking) {
        if (sneaking) {
            consecutiveSneakTicks = lastPacketSneaking ? Math.min(20, consecutiveSneakTicks + 1) : 1;
        } else {
            consecutiveSneakTicks = 0;
        }
        lastPacketSneaking = sneaking;
    }

    public void observePacketGround(boolean clientGround, boolean positionIncluded) {
        if (!positionIncluded) {
            if (clientGround || clientGround != lastClientGround) {
                recentGroundStateTicks = Math.min(4, recentGroundStateTicks + 1);
                hiddenGroundPending = true;
            }
        } else if (recentGroundStateTicks > 0) {
            recentGroundStateTicks = Math.max(0, recentGroundStateTicks - 1);
            hiddenGroundPending = recentGroundStateTicks > 0;
        }
        lastClientGround = clientGround;
    }

    public int getRecentGroundStateTicks() {
        return recentGroundStateTicks;
    }

    public boolean isHiddenGroundPending() {
        return hiddenGroundPending;
    }

    public boolean wasLastResolvedGround() {
        return lastResolvedGround;
    }

    public void resolveGround(boolean grounded) {
        lastResolvedGround = grounded;
        if (grounded) {
            recentGroundStateTicks = 0;
            hiddenGroundPending = false;
        }
    }

    public void resetMotion() {
        lastMotionX = 0.0D;
        lastMotionY = 0.0D;
        lastMotionZ = 0.0D;
        lastObservedMotionX = 0.0D;
        lastObservedMotionY = 0.0D;
        lastObservedMotionZ = 0.0D;
        lastMovementTimeMs = 0L;
        recentPositionlessTicks = 0;
        consecutiveSneakTicks = 0;
        lastPacketSneaking = false;
        recentGroundStateTicks = 0;
        hiddenGroundPending = false;
        lastClientGround = false;
        lastResolvedGround = false;
        velocityCorrectionSequence++;
    }
}
