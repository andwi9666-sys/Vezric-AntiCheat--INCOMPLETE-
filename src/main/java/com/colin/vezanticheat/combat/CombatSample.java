package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.data.PlayerCombatData;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.HitboxUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of one attack event for future combat geometry analysis.
 */
public final class CombatSample {

    private final Player attacker;
    private final Player target;
    private final UUID attackerUuid;
    private final UUID targetUuid;
    private final Location attackerLocation;
    private final Location attackerEye;
    private final Location targetLocation;
    private final Location rewoundTargetLocation;
    private final boolean rewoundValid;
    private final float attackerYaw;
    private final float attackerPitch;
    private final double targetWidth;
    private final double targetHeight;
    private final int pingEstimate;
    private final int targetPingEstimate;
    private final double attackDistance;
    private final long timestampMs;
    private final List<RotationPoint> recentRotations;
    private final List<MovementPoint> recentMovements;
    private final List<MovementPoint> recentTargetMovements;

    private CombatSample(Builder builder) {
        this.attacker = builder.attacker;
        this.target = builder.target;
        this.attackerUuid = builder.attackerUuid;
        this.targetUuid = builder.targetUuid;
        this.attackerLocation = cloneLocation(builder.attackerLocation);
        this.attackerEye = cloneLocation(builder.attackerEye);
        this.targetLocation = cloneLocation(builder.targetLocation);
        this.rewoundTargetLocation = cloneLocation(builder.rewoundTargetLocation);
        this.rewoundValid = builder.rewoundValid;
        this.attackerYaw = builder.attackerYaw;
        this.attackerPitch = builder.attackerPitch;
        this.targetWidth = builder.targetWidth;
        this.targetHeight = builder.targetHeight;
        this.pingEstimate = builder.pingEstimate;
        this.targetPingEstimate = builder.targetPingEstimate;
        this.attackDistance = builder.attackDistance;
        this.timestampMs = builder.timestampMs;
        this.recentRotations = Collections.unmodifiableList(new ArrayList<RotationPoint>(builder.recentRotations));
        this.recentMovements = Collections.unmodifiableList(new ArrayList<MovementPoint>(builder.recentMovements));
        this.recentTargetMovements = Collections.unmodifiableList(
                new ArrayList<MovementPoint>(builder.recentTargetMovements));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Player getAttacker() {
        return attacker;
    }

    public Player getTarget() {
        return target;
    }

    public UUID getAttackerUuid() {
        return attackerUuid;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public Location getAttackerLocation() {
        return cloneLocation(attackerLocation);
    }

    public Location getAttackerEye() {
        return cloneLocation(attackerEye);
    }

    public Location getTargetLocation() {
        return cloneLocation(targetLocation);
    }

    public Location getRewoundTargetLocation() {
        return cloneLocation(rewoundTargetLocation);
    }

    public boolean isRewoundValid() {
        return rewoundValid;
    }

    /** Preferred classification anchor: rewound AABB feet when valid, else live fallback. */
    public Location getClassificationTargetLocation() {
        if (rewoundValid && rewoundTargetLocation != null) {
            return cloneLocation(rewoundTargetLocation);
        }
        return cloneLocation(targetLocation);
    }

    public float getAttackerYaw() {
        return attackerYaw;
    }

    public float getAttackerPitch() {
        return attackerPitch;
    }

    public double getTargetWidth() {
        return targetWidth;
    }

    public double getTargetHeight() {
        return targetHeight;
    }

    public int getPingEstimate() {
        return pingEstimate;
    }

    public int getTargetPingEstimate() {
        return targetPingEstimate;
    }

    public double getAttackDistance() {
        return attackDistance;
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public List<RotationPoint> getRecentRotations() {
        return recentRotations;
    }

    public List<MovementPoint> getRecentMovements() {
        return recentMovements;
    }

    public List<MovementPoint> getRecentTargetMovements() {
        return recentTargetMovements;
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }

    public static final class RotationPoint {

        private final float yaw;
        private final float pitch;
        private final long timeMs;

        public RotationPoint(float yaw, float pitch, long timeMs) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.timeMs = timeMs;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public long getTimeMs() {
            return timeMs;
        }
    }

    public static final class MovementPoint {

        private final double x;
        private final double y;
        private final double z;
        private final long timeMs;

        public MovementPoint(double x, double y, double z, long timeMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timeMs = timeMs;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public long getTimeMs() {
            return timeMs;
        }
    }

    public static final class Builder {

        private Player attacker;
        private Player target;
        private UUID attackerUuid;
        private UUID targetUuid;
        private Location attackerLocation;
        private Location attackerEye;
        private Location targetLocation;
        private Location rewoundTargetLocation;
        private boolean rewoundValid;
        private float attackerYaw;
        private float attackerPitch;
        private double targetWidth = 0.6D;
        private double targetHeight = 1.8D;
        private int pingEstimate;
        private int targetPingEstimate;
        private double attackDistance;
        private long timestampMs;
        private final List<RotationPoint> recentRotations = new ArrayList<RotationPoint>();
        private final List<MovementPoint> recentMovements = new ArrayList<MovementPoint>();
        private final List<MovementPoint> recentTargetMovements = new ArrayList<MovementPoint>();

        public Builder attacker(Player attacker) {
            this.attacker = attacker;
            if (attacker != null) {
                this.attackerUuid = attacker.getUniqueId();
            }
            return this;
        }

        public Builder target(Player target) {
            this.target = target;
            if (target != null) {
                this.targetUuid = target.getUniqueId();
            }
            return this;
        }

        public Builder attackerUuid(UUID attackerUuid) {
            this.attackerUuid = attackerUuid;
            return this;
        }

        public Builder targetUuid(UUID targetUuid) {
            this.targetUuid = targetUuid;
            return this;
        }

        public Builder attackerLocation(Location attackerLocation) {
            this.attackerLocation = attackerLocation;
            return this;
        }

        public Builder attackerEye(Location attackerEye) {
            this.attackerEye = attackerEye;
            return this;
        }

        public Builder targetLocation(Location targetLocation) {
            this.targetLocation = targetLocation;
            return this;
        }

        public Builder rewoundTargetLocation(Location rewoundTargetLocation) {
            this.rewoundTargetLocation = rewoundTargetLocation;
            return this;
        }

        public Builder rewoundValid(boolean rewoundValid) {
            this.rewoundValid = rewoundValid;
            return this;
        }

        public Builder attackerYaw(float attackerYaw) {
            this.attackerYaw = attackerYaw;
            return this;
        }

        public Builder attackerPitch(float attackerPitch) {
            this.attackerPitch = attackerPitch;
            return this;
        }

        public Builder targetWidth(double targetWidth) {
            this.targetWidth = targetWidth;
            return this;
        }

        public Builder targetHeight(double targetHeight) {
            this.targetHeight = targetHeight;
            return this;
        }

        public Builder pingEstimate(int pingEstimate) {
            this.pingEstimate = pingEstimate;
            return this;
        }

        public Builder targetPingEstimate(int targetPingEstimate) {
            this.targetPingEstimate = targetPingEstimate;
            return this;
        }

        public Builder attackDistance(double attackDistance) {
            this.attackDistance = attackDistance;
            return this;
        }

        public Builder timestampMs(long timestampMs) {
            this.timestampMs = timestampMs;
            return this;
        }

        public Builder addRotation(RotationPoint rotation) {
            if (rotation != null) {
                this.recentRotations.add(rotation);
            }
            return this;
        }

        public Builder recentRotations(List<RotationPoint> rotations) {
            this.recentRotations.clear();
            if (rotations != null) {
                this.recentRotations.addAll(rotations);
            }
            return this;
        }

        public Builder addMovement(MovementPoint movement) {
            if (movement != null) {
                this.recentMovements.add(movement);
            }
            return this;
        }

        public Builder recentMovements(List<MovementPoint> movements) {
            this.recentMovements.clear();
            if (movements != null) {
                this.recentMovements.addAll(movements);
            }
            return this;
        }

        public Builder addTargetMovement(MovementPoint movement) {
            if (movement != null) {
                this.recentTargetMovements.add(movement);
            }
            return this;
        }

        public Builder recentTargetMovements(List<MovementPoint> movements) {
            this.recentTargetMovements.clear();
            if (movements != null) {
                this.recentTargetMovements.addAll(movements);
            }
            return this;
        }

        public CombatSample build() {
            return new CombatSample(this);
        }
    }

    /**
     * Convenience factory that copies live player state into a sample.
     * Classification logic is not applied here.
     */
    public static CombatSample fromAttack(Player attacker, Player target, int pingEstimate, long timestampMs) {
        return fromAttack(null, attacker, null, target, pingEstimate, timestampMs);
    }

    /**
     * Packet-synced attack snapshot with rolling combat history for classification.
     */
    public static CombatSample fromAttack(CombatAnalyzer analyzer, Player attacker, PlayerData attackerData,
                                          Player target, int pingEstimate, long timestampMs) {
        if (attacker == null || target == null) {
            return null;
        }
        if (attacker.getWorld() == null || target.getWorld() == null) {
            return null;
        }

        float yaw = attackerData != null ? attackerData.getPacketYaw() : attacker.getLocation().getYaw();
        float pitch = attackerData != null ? attackerData.getPacketPitch() : attacker.getLocation().getPitch();

        Location attackerLoc = attackerData != null && attackerData.getLastLoc() != null
                ? attackerData.getLastLoc().clone()
                : attacker.getLocation();
        Location eye = attackerData != null
                ? HitboxUtil.buildPacketSyncedEye(attacker, attackerData)
                : attacker.getEyeLocation();
        if (eye == null) {
            eye = attacker.getEyeLocation();
        }
        eye.setYaw(yaw);
        eye.setPitch(pitch);

        Location targetLoc = target.getLocation();
        Location rewoundLoc = null;
        boolean rewoundValid = false;
        if (attackerData != null) {
            com.colin.vezanticheat.engine.CombatResult combatResult = attackerData.getLastCombatResult();
            if (combatResult != null && combatResult.isValid() && combatResult.getChosenLocation() != null) {
                rewoundLoc = combatResult.getChosenLocation();
                rewoundValid = true;
            }
        }
        double attackDistance = rewoundValid && rewoundLoc != null
                ? CombatUtil.distanceToHitbox(eye, rewoundLoc, 0.6D, 1.8D)
                : CombatUtil.distanceToHitbox(eye, target);
        int targetPing = Math.max(0, PingUtil.getPing(target));
        Builder sampleBuilder = builder()
                .attacker(attacker)
                .target(target)
                .attackerLocation(attackerLoc)
                .attackerEye(eye)
                .targetLocation(targetLoc)
                .rewoundTargetLocation(rewoundLoc)
                .rewoundValid(rewoundValid)
                .attackerYaw(yaw)
                .attackerPitch(pitch)
                .targetWidth(0.6D)
                .targetHeight(1.8D)
                .pingEstimate(pingEstimate)
                .targetPingEstimate(targetPing)
                .attackDistance(attackDistance)
                .timestampMs(timestampMs);

        if (analyzer != null) {
            // Rolling rotations/movements feed pre-aim, spike, correlation, and switch checks.
            PlayerCombatData combatData = analyzer.getCombatData(attacker.getUniqueId());
            if (combatData != null) {
                for (com.colin.vezanticheat.combat.history.RotationSample rotation
                        : combatData.getRecentRotations(PlayerCombatData.MAX_ROTATION_SAMPLES)) {
                    if (rotation == null) {
                        continue;
                    }
                    sampleBuilder.addRotation(new RotationPoint(
                            rotation.getYaw(), rotation.getPitch(), rotation.getTimestamp()));
                }
                for (com.colin.vezanticheat.combat.history.MovementSample movement
                        : combatData.getRecentMovements(PlayerCombatData.MAX_MOVEMENT_SAMPLES)) {
                    if (movement == null) {
                        continue;
                    }
                    Location movementLoc = movement.getLocation();
                    if (movementLoc == null) {
                        continue;
                    }
                    sampleBuilder.addMovement(new MovementPoint(
                            movementLoc.getX(),
                            movementLoc.getY(),
                            movementLoc.getZ(),
                            movement.getTimestamp()));
                }
            }

            PlayerCombatData targetCombatData = analyzer.getCombatData(target.getUniqueId());
            if (targetCombatData != null) {
                for (com.colin.vezanticheat.combat.history.MovementSample movement
                        : targetCombatData.getRecentMovements(PlayerCombatData.MAX_MOVEMENT_SAMPLES)) {
                    if (movement == null) {
                        continue;
                    }
                    Location movementLoc = movement.getLocation();
                    if (movementLoc == null) {
                        continue;
                    }
                    sampleBuilder.addTargetMovement(new MovementPoint(
                            movementLoc.getX(),
                            movementLoc.getY(),
                            movementLoc.getZ(),
                            movement.getTimestamp()));
                }
            }
        }

        return sampleBuilder.build();
    }
}
