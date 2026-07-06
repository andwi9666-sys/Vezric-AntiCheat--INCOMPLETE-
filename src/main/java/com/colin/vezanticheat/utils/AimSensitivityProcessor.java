package com.colin.vezanticheat.utils;

import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GrimAC {@code AimProcessor} analogue — recovers the client's mouse sensitivity from rotation deltas
 * via the GCD of consecutive yaw/pitch steps, then validates it against Minecraft's sensitivity curve.
 *
 * <p>Vanilla rotation deltas are quantised: every yaw/pitch delta is an integer multiple of a base step
 * determined by the sensitivity slider, {@code f = (sens*0.6 + 0.2)^3 * 8 * 0.15}. The GCD of many small
 * deltas recovers that base step, and {@link #convertToSensitivity(double)} inverts the cube-root curve
 * to map it back to the {@code [0,1]} slider. A legit client always recovers a sensitivity in that band;
 * aimbots that synthesise rotations as arbitrary floats (without honouring the discrete mouse step)
 * recover an out-of-band or degenerate sensitivity — i.e. non-vanilla rotation math.</p>
 *
 * <p>This is the one genuinely-new aim primitive vs the existing {@link GcdLatticeAnalysis} (which scores
 * lattice <i>residue</i> but does not recover or validate the sensitivity value itself).</p>
 */
public final class AimSensitivityProcessor {

    /** Grim {@code GrimMath.MINIMUM_DIVISOR = ((0.2^3 * 8) * 0.15) - 1e-3} (~0.0959). */
    public static final double MINIMUM_DIVISOR = ((Math.pow(0.2D, 3) * 8.0D) * 0.15D) - 1.0E-3D;
    public static final int SIGNIFICANT_SAMPLES = 15;
    public static final int TOTAL_SAMPLES = 80;

    private AimSensitivityProcessor() {}

    /** Grim {@code GrimMath.gcd} — Euclid on doubles, terminating at {@link #MINIMUM_DIVISOR}. */
    public static double gcd(double a, double b) {
        if (a == 0.0D) return 0.0D;
        if (a < b) { double t = a; a = b; b = t; }
        while (b > MINIMUM_DIVISOR) {
            double t = a - (Math.floor(a / b) * b);
            a = b;
            b = t;
        }
        return a;
    }

    /** Grim {@code convertToSensitivity} — invert the cube-root sensitivity curve. */
    public static double convertToSensitivity(double gcd) {
        double v11 = gcd / 0.15D / 8.0D;
        double v9 = Math.cbrt(v11);
        return (v9 - 0.2D) / 0.6D;
    }

    public static Result analyze(Deque<Float> yawDeltas, Deque<Float> pitchDeltas) {
        double dX = modalGcd(yawDeltas);
        double dY = modalGcd(pitchDeltas);
        int significant = significantCount(yawDeltas, dX) + significantCount(pitchDeltas, dY);
        boolean enough = significant >= SIGNIFICANT_SAMPLES;
        double sens = dX > MINIMUM_DIVISOR ? convertToSensitivity(dX) : -1.0D;
        boolean vanilla = dX > MINIMUM_DIVISOR && sens >= -1.0E-4D && sens <= 1.0D + 1.0E-4D;
        return new Result(dX, dY, sens, significant, enough, vanilla);
    }

    /** Most-common GCD of consecutive significant (0 &lt; delta &lt; 5) deltas. 0 if none. */
    private static double modalGcd(Deque<Float> deltas) {
        if (deltas == null) return 0.0D;
        List<Float> list = AimAssistUtil.tailFloats(deltas, TOTAL_SAMPLES);
        Map<Long, Integer> freq = new HashMap<Long, Integer>();
        double last = 0.0D;
        double bestGcd = 0.0D;
        int bestCount = 0;
        for (Float f : list) {
            if (f == null) continue;
            double d = Math.abs(f.doubleValue());
            if (d <= 0.0D || d >= 5.0D) { last = 0.0D; continue; }
            if (last > 0.0D) {
                double g = gcd(d, last);
                if (g > MINIMUM_DIVISOR) {
                    long key = Math.round(g * 1000.0D);
                    int c = freq.getOrDefault(key, 0) + 1;
                    freq.put(key, c);
                    if (c > bestCount) { bestCount = c; bestGcd = g; }
                }
            }
            last = d;
        }
        return bestGcd;
    }

    /** How many significant deltas quantise onto the recovered GCD lattice. */
    private static int significantCount(Deque<Float> deltas, double gcd) {
        if (deltas == null || gcd <= MINIMUM_DIVISOR) return 0;
        List<Float> list = AimAssistUtil.tailFloats(deltas, TOTAL_SAMPLES);
        int count = 0;
        for (Float f : list) {
            if (f == null) continue;
            double d = Math.abs(f.doubleValue());
            if (d <= 0.0D || d >= 5.0D) continue;
            double bucket = Math.round(d / gcd);
            double snapped = bucket * gcd;
            if (Math.abs(d - snapped) <= Math.max(0.0025D, gcd * 0.12D)) count++;
        }
        return count;
    }

    public static final class Result {
        public final double dividerX;        // recovered modal GCD for yaw
        public final double dividerY;        // recovered modal GCD for pitch
        public final double sensitivity;     // recovered sensitivity from the yaw GCD
        public final int significantSamples; // samples conforming to the recovered lattice
        public final boolean enoughSamples;
        public final boolean vanillaSensitivity;

        Result(double dividerX, double dividerY, double sensitivity, int significantSamples,
               boolean enoughSamples, boolean vanillaSensitivity) {
            this.dividerX = dividerX;
            this.dividerY = dividerY;
            this.sensitivity = sensitivity;
            this.significantSamples = significantSamples;
            this.enoughSamples = enoughSamples;
            this.vanillaSensitivity = vanillaSensitivity;
        }

        /** 0 = consistent with a real mouse sensitivity; toward 1 = increasingly impossible (non-vanilla math). */
        public double suspicion() {
            if (!enoughSamples || vanillaSensitivity) return 0.0D;
            double over = sensitivity > 1.0D ? (sensitivity - 1.0D)
                    : (sensitivity < 0.0D ? -sensitivity : 0.0D);
            return Math.min(1.0D, 0.5D + Math.min(0.5D, over));
        }
    }
}
