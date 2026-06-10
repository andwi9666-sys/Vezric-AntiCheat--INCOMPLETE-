package com.colin.vezanticheat.combat.history;

/**
 * One rotation tick in rolling combat history.
 */
public final class RotationSample {

    private final float yaw;
    private final float pitch;
    private final long timestamp;
    private final float deltaYaw;
    private final float deltaPitch;

    public RotationSample(float yaw, float pitch, long timestamp, float deltaYaw, float deltaPitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.timestamp = timestamp;
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getDeltaYaw() {
        return deltaYaw;
    }

    public float getDeltaPitch() {
        return deltaPitch;
    }
}
