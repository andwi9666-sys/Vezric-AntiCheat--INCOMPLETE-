package com.colin.vezanticheat.data;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Packet-synced attack geometry captured at USE_ENTITY time.
 * Uses packet yaw/pitch (last C03/C05/C06), not Bukkit camera yaw.
 */
public final class AttackRayContext {

    public final long timeMs;
    public final Location eye;
    public final float packetYaw;
    public final float packetPitch;
    public final float cameraYaw;
    public final float cameraPitch;
    public final Vector toTarget;
    public final double angleDegrees;
    public final double lookDot;
    public final double distance;
    public final int targetEntityId;

    public AttackRayContext(long timeMs, Location eye, float packetYaw, float packetPitch,
                            float cameraYaw, float cameraPitch, Vector toTarget,
                            double angleDegrees, double lookDot, double distance, int targetEntityId) {
        this.timeMs = timeMs;
        this.eye = eye == null ? null : eye.clone();
        this.packetYaw = packetYaw;
        this.packetPitch = packetPitch;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.toTarget = toTarget == null ? null : toTarget.clone();
        this.angleDegrees = angleDegrees;
        this.lookDot = lookDot;
        this.distance = distance;
        this.targetEntityId = targetEntityId;
    }

    public boolean rayHitsTarget() {
        return angleDegrees <= 50.0D && lookDot >= 0.45D;
    }
}
