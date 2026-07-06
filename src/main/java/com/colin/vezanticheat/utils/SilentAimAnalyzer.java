package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.combat.math.RequiredRotationUtil;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Stateless analyzer for the INJECTED-ROTATION FINGERPRINT of silent aim.
 *
 * <p>Attack-time angular error is useless against genuine silent aim: the server-side rotation is
 * OVERRIDDEN to point exactly at the target on the attack tick, so the attack-tick error is ~0.
 * Instead, this reconstructs the per-tick rotation TRAJECTORY through the attack moment from the
 * rotation ring buffer (raw yaw/pitch + timeMs) and/or the position history, evaluates each sample's
 * eye-ray error to the SAME lag-comp hitbox the reach engine used, and extracts:
 *
 * <ul>
 *   <li>(A) SNAP-TO-TARGET-AND-RESTORE: a sample whose error collapses to near-0 from a high prior
 *       error, immediately followed by the signed yaw jumping BACK toward the pre-snap heading. The
 *       RESTORE is the tell — humans who flick to a target keep looking there for several ticks.</li>
 *   <li>(B) IMPOSSIBLE ANGULAR VELOCITY ONTO HITBOX: the per-tick deg/sec that lands the aim on the
 *       target exceeds {@link RequiredRotationUtil#snapAngularVelocityThreshold(int)} while the
 *       post-step error is near-perfect. Explicitly INCLUDES 180/large snaps that terminate on a
 *       valid target.</li>
 *   <li>(C) CENTER-LOCK + ZERO JITTER: the sub-degree std-dev of the small post-landing errors
 *       around hitbox center; near-zero = unnatural (real mice always jitter).</li>
 *   <li>(G) LINEAR / CONSTANT-ANGULAR-VELOCITY ramps via {@link GcdLatticeAnalysis#distributedSnapRatio(double[])}.</li>
 * </ul>
 *
 * <p>Java 8, final, private ctor, no streams. All angles in DEGREES, all velocities in DEGREES/SEC.
 * Returns {@link #empty()} on insufficient data so callers never NPE and low != suspicious by accident.
 */
public final class SilentAimAnalyzer {

    private SilentAimAnalyzer() {}

    /** Error (deg) at/below which the aim is considered landed ON the hitbox. */
    private static final double CENTER_NEAR_DEG = 1.5D;
    /** Prior error (deg) that must precede a landing for it to count as a snap-TO leg. */
    private static final double MIN_PRIOR_ERR_DEG = 12.0D;
    /** Signed-yaw tolerance (deg) for the restore leg to count as "returned to pre-snap heading". */
    private static final double RESTORE_TOLERANCE_DEG = 6.0D;
    /** Error (deg) the restore leg must rise back to so the aim demonstrably LEFT the target. */
    private static final double RESTORE_MIN_RISE_DEG = 6.0D;
    /** Window (deg) around hitbox center used for the center-lock jitter sample set. */
    private static final double CENTER_JITTER_BAND_DEG = 3.0D;
    /** Sentinel jitter returned when too few post-landing samples exist (so low != suspicious). */
    private static final double JITTER_SENTINEL_DEG = 1.0D;

    /** Immutable result consumed by the fusion check. All-zero / neutral when no usable signal. */
    public static final class Result {
        /** Index of the snap-TO landing sample within the reconstructed trajectory, or -1. */
        public final int landingIndex;
        /** Highest error (deg) observed strictly BEFORE the landing (the pre-snap heading error). */
        public final double priorErrorDeg;
        /** Error (deg) at the landing sample (near-0 for a real snap). */
        public final double landingErrorDeg;
        /** True when a snap-TO leg (high prior error -> near-0 landing on hitbox) was found. */
        public final boolean hasSnapTo;
        /** True when a subsequent sample returned the signed yaw toward the pre-snap heading while error rose. */
        public final boolean hasRestore;
        /** How completely the restore returned to the pre-snap heading, 0..1 (1 = exact return). */
        public final double restoreCompleteness;
        /** Peak per-tick angular velocity (deg/sec) of the step that LANDED on the hitbox. */
        public final double hitboxSnapVelDegPerSec;
        /** Std-dev (deg) of the small post-landing errors around hitbox center (jitter). */
        public final double centerJitterDeg;
        /** Mean (deg) of the small post-landing errors to hitbox CENTER (center-lock margin). */
        public final double centerMarginDeg;
        /** distributedSnapRatio over the trajectory error series (high = linear/constant-velocity ramp). */
        public final double linearRampRatio;
        /** Number of usable samples the trajectory was built from. */
        public final int sampleCount;

        Result(int landingIndex, double priorErrorDeg, double landingErrorDeg, boolean hasSnapTo,
               boolean hasRestore, double restoreCompleteness, double hitboxSnapVelDegPerSec,
               double centerJitterDeg, double centerMarginDeg, double linearRampRatio, int sampleCount) {
            this.landingIndex = landingIndex;
            this.priorErrorDeg = priorErrorDeg;
            this.landingErrorDeg = landingErrorDeg;
            this.hasSnapTo = hasSnapTo;
            this.hasRestore = hasRestore;
            this.restoreCompleteness = restoreCompleteness;
            this.hitboxSnapVelDegPerSec = hitboxSnapVelDegPerSec;
            this.centerJitterDeg = centerJitterDeg;
            this.centerMarginDeg = centerMarginDeg;
            this.linearRampRatio = linearRampRatio;
            this.sampleCount = sampleCount;
        }
    }

    private static final Result EMPTY =
            new Result(-1, 0.0D, 0.0D, false, false, 0.0D, 0.0D, JITTER_SENTINEL_DEG, 180.0D, 0.0D, 0);

    /** Immutable all-zero/neutral sentinel returned on insufficient data so callers never NPE. */
    public static Result empty() {
        return EMPTY;
    }

    /** Lightweight time-ordered trajectory sample (raw yaw/pitch + time). */
    private static final class TrajSample {
        final float yaw;
        final float pitch;
        final long timeMs;
        double errorDeg;
        double centerErrorDeg;
        TrajSample(float yaw, float pitch, long timeMs) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.timeMs = timeMs;
        }
    }

    /**
     * Top-level entry. Builds the time-ordered trajectory of (yaw,pitch,timeMs) within
     * [attackTime-windowMs, attackTime+windowMs] from {@code ring} (preferred), falling back to
     * {@code positionHistory}; for each sample clones {@code attackEye}, applies that sample's
     * yaw/pitch, and computes the eye-ray angular error to the lag-comp hitbox ({@code hitboxFeet}).
     *
     * @param ring            rotation ring buffer (RAW yaw/pitch + timeMs PUBLIC fields)
     * @param positionHistory fallback position samples (getYaw()/getPitch()/getTime())
     * @param attackEye       attacker eye Location at attack time (cloned per sample)
     * @param hitboxFeet      lag-comp hitbox feet Location (the reach-engine target)
     * @param width           target hitbox width
     * @param height          target hitbox height
     * @param attackTimeMs    attack-tick timestamp
     * @param pingMs          attacker ping (for the impossible-velocity threshold)
     * @param windowMs        half-window (ms) around the attack tick to reconstruct
     */
    public static Result analyze(Deque<PlayerData.RotationSample> ring,
                                 Deque<PlayerData.PositionSample> positionHistory,
                                 Location attackEye, Location hitboxFeet,
                                 double width, double height,
                                 long attackTimeMs, int pingMs, long windowMs) {
        if (attackEye == null || hitboxFeet == null || attackEye.getWorld() == null) {
            return EMPTY;
        }

        List<TrajSample> traj = buildTrajectory(ring, positionHistory, attackTimeMs, windowMs);
        if (traj.size() < 3) {
            return EMPTY;
        }

        // Eye ray + center vector are computed per sample from a clone with that sample's rotation.
        double centerX = hitboxFeet.getX();
        double centerY = hitboxFeet.getY() + height / 2.0D;
        double centerZ = hitboxFeet.getZ();
        Location probe = attackEye.clone();
        for (int i = 0; i < traj.size(); i++) {
            TrajSample s = traj.get(i);
            probe.setYaw(s.yaw);
            probe.setPitch(s.pitch);
            s.errorDeg = CombatUtil.angularError(probe, hitboxFeet, width, height);
            s.centerErrorDeg = angleToPoint(probe, centerX, centerY, centerZ);
        }

        // (A)+(B): locate the best snap-TO landing — the lowest-error sample (<= CENTER_NEAR_DEG)
        // that has a HIGH prior error somewhere before it.
        int landingIndex = -1;
        double landingErr = Double.MAX_VALUE;
        double priorErrAtLanding = 0.0D;
        for (int i = 1; i < traj.size(); i++) {
            double err = traj.get(i).errorDeg;
            if (err > CENTER_NEAR_DEG) continue;
            double maxPrior = 0.0D;
            for (int j = 0; j < i; j++) {
                if (traj.get(j).errorDeg > maxPrior) maxPrior = traj.get(j).errorDeg;
            }
            if (maxPrior < MIN_PRIOR_ERR_DEG) continue;
            if (err < landingErr) {
                landingErr = err;
                landingIndex = i;
                priorErrAtLanding = maxPrior;
            }
        }

        boolean hasSnapTo = landingIndex >= 0;

        // (B): velocity of the step that LANDED on the hitbox (deg/sec). Combined yaw+pitch step.
        double hitboxSnapVel = 0.0D;
        if (hasSnapTo) {
            TrajSample land = traj.get(landingIndex);
            TrajSample prev = traj.get(landingIndex - 1);
            hitboxSnapVel = stepVelocityDegPerSec(prev, land);
        }

        // (A) restore leg: after the landing, does the signed yaw jump BACK toward the pre-snap
        // heading (within RESTORE_TOLERANCE_DEG) while error rises again (LEFT the target)?
        boolean hasRestore = false;
        double restoreCompleteness = 0.0D;
        if (hasSnapTo) {
            float preSnapYaw = preSnapHeadingYaw(traj, landingIndex);
            double preSnapErr = priorErrAtLanding;
            for (int k = landingIndex + 1; k < traj.size() && k <= landingIndex + 3; k++) {
                TrajSample s = traj.get(k);
                double yawBackDelta = Math.abs(wrapTo180(s.yaw - preSnapYaw));
                boolean errorRose = s.errorDeg >= RESTORE_MIN_RISE_DEG;
                if (yawBackDelta <= RESTORE_TOLERANCE_DEG && errorRose) {
                    hasRestore = true;
                    // completeness: 1 when yaw lands exactly on pre-snap heading and error fully restored.
                    double yawPart = 1.0D - (yawBackDelta / Math.max(1.0D, RESTORE_TOLERANCE_DEG));
                    double errPart = preSnapErr <= 0.0D ? 0.0D
                            : Math.min(1.0D, s.errorDeg / preSnapErr);
                    double completeness = 0.5D * clamp01(yawPart) + 0.5D * clamp01(errPart);
                    if (completeness > restoreCompleteness) restoreCompleteness = completeness;
                }
            }
        }

        // (C) center-lock + jitter: std-dev / mean of the small post-landing errors around center.
        double centerJitter = JITTER_SENTINEL_DEG;
        double centerMargin = 180.0D;
        {
            List<Double> centerErrs = new ArrayList<Double>();
            int from = hasSnapTo ? landingIndex : 0;
            for (int i = from; i < traj.size(); i++) {
                double ce = traj.get(i).centerErrorDeg;
                if (ce <= CENTER_JITTER_BAND_DEG) centerErrs.add(ce);
            }
            if (centerErrs.size() >= 3) {
                double mean = AimAssistUtil.average(centerErrs);
                centerJitter = AimAssistUtil.stdDev(centerErrs, mean);
                centerMargin = mean;
            }
        }

        // (G) linear-ramp ratio over the error series.
        double[] errs = new double[traj.size()];
        for (int i = 0; i < traj.size(); i++) errs[i] = traj.get(i).errorDeg;
        double linearRamp = GcdLatticeAnalysis.distributedSnapRatio(errs);

        return new Result(landingIndex, priorErrAtLanding, hasSnapTo ? landingErr : 0.0D,
                hasSnapTo, hasRestore, restoreCompleteness, hitboxSnapVel,
                centerJitter, centerMargin, linearRamp, traj.size());
    }

    /**
     * Maps a Result to a 0..1 snap-and-restore severity. 0 unless BOTH the snap-TO leg (prior error
     * high, lands near-0 on hitbox) AND the restore leg (signed yaw returns toward pre-snap heading)
     * are present; severity scales with how perfectly it lands and how completely it restores.
     */
    public static double snapRestoreScore(Result r) {
        if (r == null || !r.hasSnapTo || !r.hasRestore) return 0.0D;
        // Landing perfection: 1 at error 0, 0 at CENTER_NEAR_DEG.
        double landPart = 1.0D - clamp01(r.landingErrorDeg / CENTER_NEAR_DEG);
        // Prior-error confidence: ramps from MIN_PRIOR_ERR_DEG to ~45deg.
        double priorPart = clamp01((r.priorErrorDeg - MIN_PRIOR_ERR_DEG) / (45.0D - MIN_PRIOR_ERR_DEG));
        double restorePart = clamp01(r.restoreCompleteness);
        // Restore is the load-bearing tell; weight it heaviest.
        double score = 0.30D * landPart + 0.15D * priorPart + 0.55D * restorePart;
        return clamp01(score);
    }

    /**
     * Returns the deg/sec of the rotation step that landed aim on the hitbox (signal B). 0 if no
     * near-perfect landing was found. Caller compares against
     * {@link RequiredRotationUtil#snapAngularVelocityThreshold(int)}; a large value terminating on a
     * valid target (incl. 180 snaps) is the flag.
     */
    public static double hitboxSnapVelocity(Result r) {
        if (r == null || !r.hasSnapTo) return 0.0D;
        return Math.max(0.0D, r.hitboxSnapVelDegPerSec);
    }

    /**
     * Returns the std-dev (deg) of the small post-landing angular errors around hitbox center;
     * near-zero = unnatural zero-jitter center lock. Returns a large sentinel (>= 1.0) when too few
     * samples so low != suspicious by accident.
     */
    public static double centerLockJitter(Result r) {
        if (r == null) return JITTER_SENTINEL_DEG;
        return r.centerJitterDeg;
    }

    // ===================== internals =====================

    /** Builds the time-ordered trajectory from the ring (preferred) or position history. */
    private static List<TrajSample> buildTrajectory(Deque<PlayerData.RotationSample> ring,
                                                    Deque<PlayerData.PositionSample> positionHistory,
                                                    long attackTimeMs, long windowMs) {
        List<TrajSample> out = new ArrayList<TrajSample>();
        long lo = attackTimeMs - windowMs;
        long hi = attackTimeMs + windowMs;

        if (ring != null && !ring.isEmpty()) {
            for (PlayerData.RotationSample s : ring) {
                if (s == null) continue;
                if (s.timeMs >= lo && s.timeMs <= hi) {
                    out.add(new TrajSample(s.yaw, s.pitch, s.timeMs));
                }
            }
        }
        // Fall back to position history only when the ring did not yield enough coverage.
        if (out.size() < 3 && positionHistory != null && !positionHistory.isEmpty()) {
            out.clear();
            for (PlayerData.PositionSample s : positionHistory) {
                if (s == null) continue;
                long t = s.getTime();
                if (t >= lo && t <= hi) {
                    out.add(new TrajSample(s.getYaw(), s.getPitch(), t));
                }
            }
        }
        sortByTime(out);
        return out;
    }

    /** Insertion sort by timeMs ascending (small N, no streams). */
    private static void sortByTime(List<TrajSample> list) {
        for (int i = 1; i < list.size(); i++) {
            TrajSample key = list.get(i);
            int j = i - 1;
            while (j >= 0 && list.get(j).timeMs > key.timeMs) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    /** Combined per-tick angular velocity (deg/sec) between two trajectory samples. */
    private static double stepVelocityDegPerSec(TrajSample a, TrajSample b) {
        double yawStep = Math.abs(wrapTo180(b.yaw - a.yaw));
        double pitchStep = Math.abs(b.pitch - a.pitch);
        double combined = Math.sqrt(yawStep * yawStep + pitchStep * pitchStep);
        long dtMs = b.timeMs - a.timeMs;
        if (dtMs <= 0L) dtMs = 50L; // one tick fallback; never divide by zero.
        return combined * 1000.0D / dtMs;
    }

    /**
     * The pre-snap heading yaw = the yaw of the sample with the highest error strictly before the
     * landing (the heading the aim came FROM and, for silent aim, returns TO).
     */
    private static float preSnapHeadingYaw(List<TrajSample> traj, int landingIndex) {
        float yaw = traj.get(0).yaw;
        double maxErr = -1.0D;
        for (int j = 0; j < landingIndex; j++) {
            if (traj.get(j).errorDeg > maxErr) {
                maxErr = traj.get(j).errorDeg;
                yaw = traj.get(j).yaw;
            }
        }
        return yaw;
    }

    /** Angle (deg) between the look ray of {@code eye} and the vector to a fixed point. */
    private static double angleToPoint(Location eye, double px, double py, double pz) {
        Vector look = eye.getDirection();
        if (look.lengthSquared() <= 1.0E-8) return 180.0D;
        Vector to = new Vector(px - eye.getX(), py - eye.getY(), pz - eye.getZ());
        if (to.lengthSquared() <= 1.0E-8) return 0.0D;
        double dot = look.normalize().dot(to.normalize());
        if (dot > 1.0D) dot = 1.0D;
        if (dot < -1.0D) dot = -1.0D;
        return Math.toDegrees(Math.acos(dot));
    }

    /** Signed shortest angular difference of (a-b) wrapped to [-180, 180]. */
    private static double wrapTo180(double delta) {
        double d = delta % 360.0D;
        if (d > 180.0D) d -= 360.0D;
        if (d < -180.0D) d += 360.0D;
        return d;
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }
}
