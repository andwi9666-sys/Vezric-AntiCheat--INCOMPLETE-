package com.colin.vezanticheat.data;

import com.colin.vezanticheat.combat.CombatHitClassification;
import com.colin.vezanticheat.combat.history.AttackSample;
import com.colin.vezanticheat.combat.history.MovementSample;
import com.colin.vezanticheat.combat.history.RotationSample;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Per-player rolling rotation, movement, and attack history for combat analysis.
 */
public final class PlayerCombatData {

    public static final int MAX_ROTATION_SAMPLES = 40;
    public static final int MAX_MOVEMENT_SAMPLES = 40;
    public static final int MAX_ATTACK_SAMPLES = 20;

    private final UUID uuid;
    private final Deque<RotationSample> recentRotations = new ArrayDeque<RotationSample>();
    private final Deque<MovementSample> recentLocations = new ArrayDeque<MovementSample>();
    private final Deque<AttackSample> recentAttacks = new ArrayDeque<AttackSample>();

    private float lastYaw;
    private float lastPitch;
    private Location lastLocation;
    private long lastMovementTimestamp;
    private long lastAttackTimestamp;
    private long lastVelocityTimestamp;
    private long lastDamageTimestamp;
    private long lastKnockbackTimestamp;
    private int suspiciousTargetSwitchCount;
    private long suspiciousTargetSwitchWindowStartMs;
    private final Deque<Double> requiredRotationErrors = new ArrayDeque<Double>();

    public PlayerCombatData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public Location getLastLocation() {
        return lastLocation == null ? null : lastLocation.clone();
    }

    public long getLastMovementTimestamp() {
        return lastMovementTimestamp;
    }

    public long getLastAttackTimestamp() {
        return lastAttackTimestamp;
    }

    public long getLastVelocityTimestamp() {
        return lastVelocityTimestamp;
    }

    public long getLastDamageTimestamp() {
        return lastDamageTimestamp;
    }

    public long getLastKnockbackTimestamp() {
        return lastKnockbackTimestamp;
    }

    public void markVelocity(long nowMs) {
        this.lastVelocityTimestamp = nowMs;
        this.lastKnockbackTimestamp = nowMs;
    }

    public void markDamage(long nowMs) {
        this.lastDamageTimestamp = nowMs;
    }

    public boolean recentlyVelocity(long nowMs, long windowMs) {
        if (windowMs <= 0L) {
            return false;
        }
        return isWithinWindow(nowMs, lastVelocityTimestamp, windowMs)
                || isWithinWindow(nowMs, lastKnockbackTimestamp, windowMs);
    }

    public boolean recentlyDamaged(long nowMs, long windowMs) {
        if (windowMs <= 0L) {
            return false;
        }
        return isWithinWindow(nowMs, lastDamageTimestamp, windowMs);
    }

    public void addRotation(float yaw, float pitch, long timestamp) {
        float prevYaw = recentRotations.isEmpty() ? lastYaw : recentRotations.peekLast().getYaw();
        float prevPitch = recentRotations.isEmpty() ? lastPitch : recentRotations.peekLast().getPitch();

        float deltaYaw = shortestYawDelta(prevYaw, yaw);
        float deltaPitch = pitch - prevPitch;

        recentRotations.addLast(new RotationSample(yaw, pitch, timestamp, deltaYaw, deltaPitch));
        while (recentRotations.size() > MAX_ROTATION_SAMPLES) {
            recentRotations.removeFirst();
        }

        lastYaw = yaw;
        lastPitch = pitch;
    }

    public void addMovement(Location location, boolean onGround, long timestamp) {
        if (location == null) {
            return;
        }

        double deltaX = 0.0D;
        double deltaY = 0.0D;
        double deltaZ = 0.0D;

        if (!recentLocations.isEmpty()) {
            Location prev = recentLocations.peekLast().getLocation();
            if (prev != null) {
                deltaX = location.getX() - prev.getX();
                deltaY = location.getY() - prev.getY();
                deltaZ = location.getZ() - prev.getZ();
            }
        } else if (lastLocation != null) {
            deltaX = location.getX() - lastLocation.getX();
            deltaY = location.getY() - lastLocation.getY();
            deltaZ = location.getZ() - lastLocation.getZ();
        }

        recentLocations.addLast(new MovementSample(location, timestamp, deltaX, deltaY, deltaZ, onGround));
        while (recentLocations.size() > MAX_MOVEMENT_SAMPLES) {
            recentLocations.removeFirst();
        }

        lastLocation = location.clone();
        lastMovementTimestamp = timestamp;
    }

    public void addAttack(UUID target, long timestamp, float yaw, float pitch, double reach,
                          CombatHitClassification classification) {
        if (target == null) {
            return;
        }

        recentAttacks.addLast(new AttackSample(target, timestamp, yaw, pitch, reach, classification));
        while (recentAttacks.size() > MAX_ATTACK_SAMPLES) {
            recentAttacks.removeFirst();
        }

        lastAttackTimestamp = timestamp;
        lastYaw = yaw;
        lastPitch = pitch;
    }

    public List<RotationSample> getRecentRotations(int amount) {
        return tailCopy(recentRotations, amount);
    }

    public List<MovementSample> getRecentMovements(int amount) {
        return tailCopy(recentLocations, amount);
    }

    public List<AttackSample> getRecentAttacks(int amount) {
        return tailCopy(recentAttacks, amount);
    }

    public void recordRequiredRotationError(double combinedError, long nowMs) {
        if (combinedError <= 0.0D) {
            return;
        }
        requiredRotationErrors.addLast(combinedError);
        while (requiredRotationErrors.size() > 12) {
            requiredRotationErrors.removeFirst();
        }
    }

    public double medianRequiredRotationError() {
        if (requiredRotationErrors.isEmpty()) {
            return 0.0D;
        }
        List<Double> sorted = new ArrayList<Double>(requiredRotationErrors);
        java.util.Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return (sorted.get(mid - 1) + sorted.get(mid)) * 0.5D;
    }

    public int getRequiredRotationSampleCount() {
        return requiredRotationErrors.size();
    }

    public void recordSuspiciousTargetSwitch(long nowMs) {
        if (suspiciousTargetSwitchWindowStartMs <= 0L
                || (nowMs - suspiciousTargetSwitchWindowStartMs) > 30_000L) {
            suspiciousTargetSwitchCount = 0;
            suspiciousTargetSwitchWindowStartMs = nowMs;
        }
        suspiciousTargetSwitchCount++;
    }

    public int getSuspiciousTargetSwitchCount() {
        return suspiciousTargetSwitchCount;
    }

    public void decaySuspiciousTargetSwitch(long nowMs) {
        if (suspiciousTargetSwitchWindowStartMs > 0L
                && (nowMs - suspiciousTargetSwitchWindowStartMs) > 30_000L) {
            suspiciousTargetSwitchCount = 0;
            suspiciousTargetSwitchWindowStartMs = 0L;
        }
    }

    private static <T> List<T> tailCopy(Deque<T> deque, int amount) {
        if (amount <= 0 || deque.isEmpty()) {
            return new ArrayList<T>();
        }

        int count = Math.min(amount, deque.size());
        List<T> all = new ArrayList<T>(deque);
        int start = all.size() - count;
        return new ArrayList<T>(all.subList(start, all.size()));
    }

    private static float shortestYawDelta(float from, float to) {
        float delta = to - from;
        while (delta > 180.0F) {
            delta -= 360.0F;
        }
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    private static boolean isWithinWindow(long nowMs, long timestampMs, long windowMs) {
        if (timestampMs <= 0L) {
            return false;
        }
        long elapsed = nowMs - timestampMs;
        return elapsed >= 0L && elapsed <= windowMs;
    }
}
