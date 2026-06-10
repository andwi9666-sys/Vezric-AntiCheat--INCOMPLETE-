package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AimAssistUtil {
    private AimAssistUtil() {}

    public static RotationSample trackRotation(PlayerData data, float yaw, float pitch) {
        if (data == null) return null;
        return new RotationSample(data.getLastRotationYawDelta(), data.getLastRotationPitchDelta());
    }

    public static AimContext currentContext(VezAntiCheat plugin, Player player, PlayerData data, String checkName) {
        if (plugin == null || player == null || data == null) return null;

        long now = System.currentTimeMillis();
        long attackWindowMs = plugin.cfg().checkLong(checkName, "attackWindowMs", 300L);
        if (!data.wasLastUseEntityAttack() || (now - data.getLastUseEntityTime()) > attackWindowMs) {
            return null;
        }

        Entity target = CombatUtil.resolveTarget(player, data.getLastTargetUuid());
        if (target == null || target.getWorld() == null || player.getWorld() == null) return null;
        if (!target.getWorld().equals(player.getWorld())) return null;

        Location eye = player.getEyeLocation();
        double width = CombatUtil.entityWidth(target);
        double height = CombatUtil.entityHeight(target);
        double distance = CombatUtil.distanceToHitbox(eye, target.getLocation(), width, height);

        double maxRange = plugin.cfg().checkDouble(checkName, "maxRange", 3.2);
        if (distance <= 0.0 || distance > maxRange) return null;

        Location base = target.getLocation();
        Location center = base.clone().add(0.0, height * 0.5, 0.0);
        double centerAngle = angleToPoint(eye, center.toVector());
        double closestAngle = CombatUtil.angularError(eye, base, width, height);
        double centerMargin = Math.max(0.0, centerAngle - closestAngle);
        double lookDot = CombatUtil.lookDotToHitbox(eye, base, width, height);

        return new AimContext(target, distance, centerAngle, closestAngle, centerMargin, lookDot);
    }

    public static void pushSample(PlayerData data, double centerAngle, double centerMargin) {
        if (data == null) return;
        long rotationPacket = data.getLastRotationPacket();
        if (rotationPacket > 0L && data.getLastAimSampleRotationPacket() == rotationPacket) {
            return;
        }

        data.getAimCenterErrors().addLast(centerAngle);
        while (data.getAimCenterErrors().size() > 30) data.getAimCenterErrors().removeFirst();

        data.getAimCenterMargins().addLast(centerMargin);
        while (data.getAimCenterMargins().size() > 30) data.getAimCenterMargins().removeFirst();
        data.setLastAimSampleRotationPacket(rotationPacket);
    }

    public static List<Double> tail(Deque<Double> deque, int size) {
        List<Double> out = new ArrayList<Double>(deque);
        if (out.size() > size) {
            out = out.subList(out.size() - size, out.size());
        }
        return out;
    }

    public static List<Float> tailFloats(Deque<Float> deque, int size) {
        List<Float> out = new ArrayList<Float>(deque);
        if (out.size() > size) {
            out = out.subList(out.size() - size, out.size());
        }
        return out;
    }

    public static double average(List<? extends Number> values) {
        double sum = 0.0;
        for (Number value : values) sum += value.doubleValue();
        return values.isEmpty() ? 0.0 : sum / values.size();
    }

    public static double stdDev(List<? extends Number> values, double mean) {
        if (values.isEmpty()) return 999.0;
        double variance = 0.0;
        for (Number value : values) {
            double delta = value.doubleValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / values.size());
    }

    public static double max(List<? extends Number> values) {
        double max = 0.0;
        for (Number value : values) {
            max = Math.max(max, value.doubleValue());
        }
        return max;
    }

    public static double dominantStepRatio(List<Float> deltas, double quantum) {
        if (deltas.isEmpty()) return 0.0;

        java.util.Map<Long, Integer> counts = new java.util.HashMap<Long, Integer>();
        int tracked = 0;
        for (Float delta : deltas) {
            if (delta == null) continue;
            double value = delta.floatValue();
            if (value <= 0.0) continue;
            long bucket = Math.round(value / quantum);
            counts.put(bucket, counts.containsKey(bucket) ? counts.get(bucket) + 1 : 1);
            tracked++;
        }
        if (tracked == 0) return 0.0;

        int dominant = 0;
        for (Integer count : counts.values()) {
            if (count != null) dominant = Math.max(dominant, count.intValue());
        }
        return dominant / (double) tracked;
    }

    public static double quantizedGridScore(List<Float> deltas, double quantum) {
        if (deltas.isEmpty() || quantum <= 0.0) return 0.0;

        int matches = 0;
        int tracked = 0;
        for (Float delta : deltas) {
            if (delta == null) continue;
            double value = delta.floatValue();
            if (value <= 0.0) continue;
            double bucket = Math.round(value / quantum);
            double remainder = Math.abs(value - (bucket * quantum));
            if (remainder <= Math.max(0.0025, quantum * 0.14)) {
                matches++;
            }
            tracked++;
        }
        return tracked == 0 ? 0.0 : matches / (double) tracked;
    }

    public static double averageAcceleration(List<Float> deltas) {
        if (deltas.size() < 2) return 999.0;
        List<Double> accels = new ArrayList<Double>();
        for (int i = 1; i < deltas.size(); i++) {
            accels.add((double) Math.abs(deltas.get(i).floatValue() - deltas.get(i - 1).floatValue()));
        }
        return average(accels);
    }

    public static double intervalVariance(Deque<Long> intervals) {
        if (intervals == null || intervals.isEmpty()) return 999.0D;
        List<Double> values = new ArrayList<Double>();
        for (Long ms : intervals) {
            if (ms != null) values.add(ms.doubleValue());
        }
        if (values.isEmpty()) return 999.0D;
        double mean = average(values);
        return stdDev(values, mean);
    }

    public static double analyzeGcd(Deque<Float> yawDeltas) {
        if (yawDeltas == null || yawDeltas.isEmpty()) return 0.0D;
        List<Float> tail = tailFloats(yawDeltas, 20);
        return dominantStepRatio(tail, 0.15F);
    }

    private static double angleToPoint(Location eye, Vector point) {
        if (eye == null || point == null) return 180.0;

        Vector look = eye.getDirection();
        Vector to = point.clone().subtract(eye.toVector());
        if (look.lengthSquared() <= 1.0E-8 || to.lengthSquared() <= 1.0E-8) return 0.0;

        double dot = look.normalize().dot(to.normalize());
        if (dot > 1.0) dot = 1.0;
        if (dot < -1.0) dot = -1.0;
        return Math.toDegrees(Math.acos(dot));
    }

    public static final class RotationSample {
        private final float yawDelta;
        private final float pitchDelta;

        public RotationSample(float yawDelta, float pitchDelta) {
            this.yawDelta = yawDelta;
            this.pitchDelta = pitchDelta;
        }

        public float getYawDelta() { return yawDelta; }
        public float getPitchDelta() { return pitchDelta; }
    }

    public static final class AimContext {
        private final Entity target;
        private final double distance;
        private final double centerAngle;
        private final double closestAngle;
        private final double centerMargin;
        private final double lookDot;

        public AimContext(Entity target, double distance, double centerAngle, double closestAngle,
                          double centerMargin, double lookDot) {
            this.target = target;
            this.distance = distance;
            this.centerAngle = centerAngle;
            this.closestAngle = closestAngle;
            this.centerMargin = centerMargin;
            this.lookDot = lookDot;
        }

        public Entity getTarget() { return target; }
        public double getDistance() { return distance; }
        public double getCenterAngle() { return centerAngle; }
        public double getClosestAngle() { return closestAngle; }
        public double getCenterMargin() { return centerMargin; }
        public double getLookDot() { return lookDot; }
    }
}
