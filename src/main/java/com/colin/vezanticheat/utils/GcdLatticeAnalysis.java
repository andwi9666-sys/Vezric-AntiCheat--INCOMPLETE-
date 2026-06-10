package com.colin.vezanticheat.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Detects aim-assist rotations that conform to an inferred mouse-sensitivity GCD lattice.
 * High off-lattice residue over many samples is suspicious but never standalone.
 */
public final class GcdLatticeAnalysis {

    private static final float MIN_DELTA = 0.02F;
    private static final float MAX_INFER_GCD = 2.5F;

    private GcdLatticeAnalysis() {}

    public static double latticeResidueFraction(Deque<Float> yawDeltas, Deque<Float> pitchDeltas) {
        List<Float> yaw = AimAssistUtil.tailFloats(yawDeltas, 100);
        List<Float> pitch = AimAssistUtil.tailFloats(pitchDeltas, 100);
        if (yaw.size() < 20) {
            return 0.0D;
        }

        float inferredGcd = inferGcd(yaw, pitch);
        if (inferredGcd <= 0.0F) {
            return 0.0D;
        }

        int offLattice = 0;
        int tracked = 0;
        for (Float delta : yaw) {
            if (delta == null || delta.floatValue() <= MIN_DELTA) continue;
            tracked++;
            if (!onLattice(delta.floatValue(), inferredGcd)) {
                offLattice++;
            }
        }
        for (Float delta : pitch) {
            if (delta == null || delta.floatValue() <= MIN_DELTA) continue;
            tracked++;
            if (!onLattice(delta.floatValue(), inferredGcd)) {
                offLattice++;
            }
        }
        return tracked == 0 ? 0.0D : offLattice / (double) tracked;
    }

    public static float inferGcd(List<Float> yawDeltas, List<Float> pitchDeltas) {
        float best = 0.0F;
        double bestScore = 0.0D;
        for (float candidate = 0.05F; candidate <= MAX_INFER_GCD; candidate += 0.01F) {
            double score = latticeFitScore(yawDeltas, candidate) + latticeFitScore(pitchDeltas, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= 0.35D ? best : 0.0F;
    }

    private static double latticeFitScore(List<Float> deltas, float gcd) {
        if (deltas == null || deltas.isEmpty() || gcd <= 0.0F) return 0.0D;
        int matches = 0;
        int tracked = 0;
        for (Float delta : deltas) {
            if (delta == null || delta.floatValue() <= MIN_DELTA) continue;
            tracked++;
            if (onLattice(delta.floatValue(), gcd)) {
                matches++;
            }
        }
        return tracked == 0 ? 0.0D : matches / (double) tracked;
    }

    private static boolean onLattice(float delta, float gcd) {
        float bucket = Math.round(delta / gcd);
        float snapped = bucket * gcd;
        return Math.abs(delta - snapped) <= Math.max(0.0025F, gcd * 0.12F);
    }

    /** Rolling peak angular velocity over ~50ms windows (degrees/second). */
    public static double peakAngularVelocityDegPerSec(Deque<AngularSample> samples, long windowMs) {
        if (samples == null || samples.size() < 2 || windowMs <= 0L) {
            return 0.0D;
        }
        double peak = 0.0D;
        AngularSample[] arr = samples.toArray(new AngularSample[0]);
        for (int i = 1; i < arr.length; i++) {
            AngularSample prev = arr[i - 1];
            AngularSample cur = arr[i];
            if (prev == null || cur == null) continue;
            long dtMs = cur.timeMs - prev.timeMs;
            if (dtMs <= 0L || dtMs > windowMs) continue;
            double yaw = Math.abs(AngleWrap(cur.yaw - prev.yaw));
            double pitch = Math.abs(cur.pitch - prev.pitch);
            double deg = Math.hypot(yaw, pitch);
            double perSec = deg * (1000.0D / dtMs);
            peak = Math.max(peak, perSec);
        }
        return peak;
    }

    public static void pushAngularSample(Deque<AngularSample> samples, float yaw, float pitch, long timeMs, int max) {
        if (samples == null) return;
        samples.addLast(new AngularSample(yaw, pitch, timeMs));
        while (samples.size() > max) {
            samples.removeFirst();
        }
    }

    private static float AngleWrap(float delta) {
        float d = delta % 360.0F;
        if (d > 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }

    public static final class AngularSample {
        public final float yaw;
        public final float pitch;
        public final long timeMs;

        public AngularSample(float yaw, float pitch, long timeMs) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.timeMs = timeMs;
        }
    }
}
