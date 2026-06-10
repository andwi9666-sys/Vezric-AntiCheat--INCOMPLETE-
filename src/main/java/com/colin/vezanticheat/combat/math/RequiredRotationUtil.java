package com.colin.vezanticheat.combat.math;

import com.colin.vezanticheat.combat.CombatSample;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validates that packet yaw/pitch at attack time could geometrically produce the hit
 * against a lag-compensated target AABB. Closes classic silent-aim where the client
 * attacks without rotating toward the rewound hitbox.
 */
public final class RequiredRotationUtil {

    private RequiredRotationUtil() {}

    public static Result evaluate(Location eye, float yaw, float pitch, BoundingBox box, int pingMs) {
        return evaluate(eye, yaw, pitch, box, pingMs, 0.0D);
    }

    public static Result evaluate(Location eye, float yaw, float pitch, BoundingBox box,
                                  int pingMs, double extraToleranceDeg) {
        if (eye == null || box == null) {
            return Result.empty();
        }

        AngleUtil.YawPitchError error = AngleUtil.getYawPitchError(eye, yaw, pitch, box);
        double yawError = error.getYawError();
        double pitchError = error.getPitchError();
        double combined = Math.hypot(yawError, pitchError);

        double dist = Math.max(0.5D, RayTraceUtil.distanceEyeToBox(eye.toVector(), box));
        double halfWidth = (box.maxX - box.minX) * 0.5D;
        double halfHeight = (box.maxY - box.minY) * 0.5D;
        double yawRadius = Math.toDegrees(Math.atan2(halfWidth, dist));
        double pitchRadius = Math.toDegrees(Math.atan2(halfHeight, dist));
        double hitboxAngularRadius = Math.hypot(yawRadius, pitchRadius);

        double pingTolerance = pingScaledTolerance(pingMs);
        double allowance = hitboxAngularRadius + pingTolerance + Math.max(0.0D, extraToleranceDeg);
        boolean exceeds = combined > allowance;

        return new Result(yawError, pitchError, combined, hitboxAngularRadius, pingTolerance, exceeds);
    }

    /** Ping-scaled angular tolerance for required-rotation checks (replaces hard ping disable). */
    public static double pingScaledTolerance(int pingMs) {
        int ping = Math.max(0, pingMs);
        return Math.min(14.0D, 2.0D + (ping * 0.035D));
    }

    /** Sustained snap threshold in degrees/second; scales up with ping instead of disabling. */
    public static double snapAngularVelocityThreshold(int pingMs) {
        int ping = Math.max(0, pingMs);
        return 800.0D + Math.min(450.0D, ping * 1.6D);
    }

    /**
     * Flags attack-packet aim that is far tighter than the visible pre-attack rotation history
     * (classic silent aim: correct attack rotation, wrong client-facing aim).
     */
    public static double preAttackSnapScore(CombatSample sample, BoundingBox box) {
        if (sample == null || box == null) {
            return 0.0D;
        }
        Location eye = sample.getAttackerEye();
        if (eye == null) {
            return 0.0D;
        }
        List<CombatSample.RotationPoint> rotations = sample.getRecentRotations();
        if (rotations == null || rotations.size() < 4) {
            return 0.0D;
        }

        List<Double> preErrors = new ArrayList<Double>();
        for (CombatSample.RotationPoint point : rotations) {
            if (point == null) {
                continue;
            }
            Result result = evaluate(eye, point.getYaw(), point.getPitch(), box, sample.getPingEstimate());
            preErrors.add(result.getCombinedError());
        }
        if (preErrors.size() < 3) {
            return 0.0D;
        }

        Collections.sort(preErrors);
        double preMedian = preErrors.get(preErrors.size() / 2);
        Result attack = evaluate(eye, sample.getAttackerYaw(), sample.getAttackerPitch(),
                box, sample.getPingEstimate());
        double gap = preMedian - attack.getCombinedError();
        if (gap < 10.0D) {
            return 0.0D;
        }
        return Math.min(1.5D, gap / 12.0D);
    }

    public static final class Result {
        private final double yawError;
        private final double pitchError;
        private final double combinedError;
        private final double hitboxAngularRadius;
        private final double pingTolerance;
        private final boolean exceedsTolerance;

        public Result(double yawError, double pitchError, double combinedError,
                      double hitboxAngularRadius, double pingTolerance, boolean exceedsTolerance) {
            this.yawError = yawError;
            this.pitchError = pitchError;
            this.combinedError = combinedError;
            this.hitboxAngularRadius = hitboxAngularRadius;
            this.pingTolerance = pingTolerance;
            this.exceedsTolerance = exceedsTolerance;
        }

        public static Result empty() {
            return new Result(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false);
        }

        public double getYawError() { return yawError; }
        public double getPitchError() { return pitchError; }
        public double getCombinedError() { return combinedError; }
        public double getHitboxAngularRadius() { return hitboxAngularRadius; }
        public double getPingTolerance() { return pingTolerance; }
        public boolean exceedsTolerance() { return exceedsTolerance; }

        public double excessBeyondAllowance() {
            return Math.max(0.0D, combinedError - (hitboxAngularRadius + pingTolerance));
        }
    }
}
