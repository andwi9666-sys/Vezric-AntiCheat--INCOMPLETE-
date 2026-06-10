package com.colin.vezanticheat.combat.math;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Yaw/pitch angle helpers for combat aim analysis.
 */
public final class AngleUtil {

    private AngleUtil() {}

    public static float getYawTo(Vector from, Vector to) {
        if (from == null || to == null) {
            return 0.0F;
        }
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float getPitchTo(Vector from, Vector to) {
        if (from == null || to == null) {
            return 0.0F;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.hypot(dx, dz);
        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    public static float angleDifference(float a, float b) {
        float diff = Math.abs(a - b) % 360.0F;
        if (diff > 180.0F) {
            diff = 360.0F - diff;
        }
        return diff;
    }

    public static YawPitchError getYawPitchError(Location eyeLocation, float actualYaw, float actualPitch,
                                                 BoundingBox targetBox) {
        if (eyeLocation == null || targetBox == null) {
            return new YawPitchError(0.0D, 0.0D);
        }

        Vector eye = eyeLocation.toVector();
        Vector aimPoint = targetBox.closestPoint(eye);
        float neededYaw = getYawTo(eye, aimPoint);
        float neededPitch = getPitchTo(eye, aimPoint);

        double yawError = angleDifference(actualYaw, neededYaw);
        double pitchError = angleDifference(actualPitch, neededPitch);
        return new YawPitchError(yawError, pitchError);
    }

    public static final class YawPitchError {
        private final double yawError;
        private final double pitchError;

        public YawPitchError(double yawError, double pitchError) {
            this.yawError = yawError;
            this.pitchError = pitchError;
        }

        public double getYawError() {
            return yawError;
        }

        public double getPitchError() {
            return pitchError;
        }
    }
}
