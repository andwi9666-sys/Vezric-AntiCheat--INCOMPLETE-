package com.colin.vezanticheat.combat.history;

import com.colin.vezanticheat.combat.CombatHitClassification;

import java.util.UUID;

/**
 * One attack event in rolling combat history.
 */
public final class AttackSample {

    private final UUID targetUuid;
    private final long timestamp;
    private final float yaw;
    private final float pitch;
    private final double reach;
    private final CombatHitClassification classification;

    public AttackSample(UUID targetUuid, long timestamp, float yaw, float pitch,
                        double reach, CombatHitClassification classification) {
        this.targetUuid = targetUuid;
        this.timestamp = timestamp;
        this.yaw = yaw;
        this.pitch = pitch;
        this.reach = reach;
        this.classification = classification == null ? CombatHitClassification.CLEAN : classification;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public double getReach() {
        return reach;
    }

    public CombatHitClassification getClassification() {
        return classification;
    }
}
