package com.colin.vezanticheat.tier.prism.scaffold;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ScaffoldUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Pattern analysis for the silent scaffold system. Given the rolling placement window plus the
 * timing/pitch histories already maintained on {@link PlayerData}, it derives five 0..1 suspicion
 * categories that {@link ScaffoldEngine} feeds into the per-check buffers:
 *
 * <ol>
 *   <li><b>timing</b> — placement-interval consistency (robotic low coefficient of variation).</li>
 *   <li><b>rotation</b> — pitch locked in a narrow band + unnaturally smooth/repeated yaw steps.</li>
 *   <li><b>raytrace</b> — the crosshair is not believably on the against block face (line-of-sight).</li>
 *   <li><b>legality</b> — illegal support / hidden-face place / repeated identical relative position.</li>
 *   <li><b>sync</b> — placing while moving backward/diagonally (hard to coordinate) <i>and</i> with
 *       robotic timing — a corroborator that can only fire when timing is already consistent.</li>
 * </ol>
 *
 * <p>The design philosophy is "strict against long-term robotic consistency, lenient on one-off skill":
 * every category needs enough samples, and human-range values score zero. Thresholds are per-check
 * config so server owners can tune without code changes.</p>
 */
final class ScaffoldAnalyzer {

    private ScaffoldAnalyzer() {}

    /** Fills {@code sd.s*} category scores from the latest placement and window. Returns a debug string. */
    static String analyze(VezAntiCheat plugin, PlayerData data, ScaffoldData sd, ScaffoldPlacement cur,
                          ScaffoldUtil.Context ctx, String scaffoldCheck, String legitCheck) {
        sd.sTiming = timingScore(plugin, data, legitCheck);
        sd.sRotation = rotationScore(plugin, data, sd, legitCheck);
        sd.sRaytrace = raytraceScore(plugin, cur, scaffoldCheck);
        sd.sLegality = legalityScore(plugin, sd, cur, ctx, scaffoldCheck);
        sd.sSync = syncScore(plugin, sd, sd.sTiming, legitCheck);

        // Track the consecutive near-perfect streak (timing AND rotation both robotic) for the
        // "occasional perfect placement is fine" allowance handled by the engine.
        if (sd.sTiming >= 0.75D && sd.sRotation >= 0.6D) {
            sd.perfectStreak++;
        } else if (sd.sTiming < 0.35D) {
            sd.perfectStreak = 0;
        }

        return "t=" + r(sd.sTiming) + " rot=" + r(sd.sRotation) + " ray=" + r(sd.sRaytrace)
                + " leg=" + r(sd.sLegality) + " sync=" + r(sd.sSync)
                + " perfect=" + sd.perfectStreak + " streak=" + sd.bridgeStreak;
    }

    // ---------------------------------------------------------------- timing

    private static double timingScore(VezAntiCheat plugin, PlayerData data, String check) {
        int minSamples = plugin.tierCfg().checkInt(check, "minSamples", 7);
        ScaffoldUtil.Stats stats = ScaffoldUtil.timingStats(data.getScaffoldIntervals(), minSamples);
        if (stats == null) return 0.0D;

        // Only score a genuine bridging cadence; slow/irregular building is not scaffold.
        double minInterval = plugin.tierCfg().checkDouble(check, "minPlaceIntervalMs", 45.0D);
        double maxInterval = plugin.tierCfg().checkDouble(check, "maxPlaceIntervalMs", 650.0D);
        if (stats.avg < minInterval || stats.avg > maxInterval) return 0.0D;

        double maxCv = plugin.tierCfg().checkDouble(check, "maxTimingCv", 0.075D);
        double humanCv = plugin.tierCfg().checkDouble(check, "humanTimingCv", 0.13D);
        if (stats.cv >= humanCv) return 0.0D;              // clearly human variance
        double cvScore = clamp01((humanCv - stats.cv) / Math.max(1.0E-6D, humanCv - maxCv));

        // Corroborate with absolute spread/std so a low CV at a fast cadence is not enough alone.
        double maxStd = plugin.tierCfg().checkDouble(check, "maxTimingStdMs", 9.5D);
        double maxSpread = plugin.tierCfg().checkDouble(check, "maxTimingSpreadMs", 28.0D);
        double spread = stats.max - stats.min;
        double tightness = 0.0D;
        if (stats.sd <= maxStd) tightness += 0.5D;
        if (spread <= maxSpread) tightness += 0.5D;

        return clamp01(0.65D * cvScore + 0.35D * tightness);
    }

    // -------------------------------------------------------------- rotation

    private static double rotationScore(VezAntiCheat plugin, PlayerData data, ScaffoldData sd, String check) {
        int minSamples = plugin.tierCfg().checkInt(check, "minSamples", 7);

        // (a) Pitch locked in a narrow band. pitchCv returns the pitch RANGE (deg) or 999 sentinel.
        double pitchRange = ScaffoldUtil.pitchCv(data.getScaffoldPitchHistory(), minSamples);
        double maxPitchRange = plugin.tierCfg().checkDouble(check, "maxPitchRange", 2.85D);
        double pitchScore = (pitchRange >= 900.0D) ? 0.0D
                : clamp01((maxPitchRange - pitchRange) / Math.max(0.5D, maxPitchRange));

        // (b) Yaw steps unnaturally smooth/repeated: tiny, low-variance, non-zero deltas.
        List<Double> yawDeltas = new ArrayList<Double>();
        int idx = 0;
        for (ScaffoldPlacement pl : sd.placements) {
            if (idx++ == 0) continue;                       // first has no prior delta
            yawDeltas.add((double) Math.abs(pl.yawDelta));
        }
        double yawScore = 0.0D;
        if (yawDeltas.size() >= minSamples - 1) {
            double[] ms = meanStd(yawDeltas);
            double maxRotStd = plugin.tierCfg().checkDouble(check, "maxRotationStd", 1.15D);
            double minDelta = plugin.tierCfg().checkDouble(check, "minRotationDelta", 0.035D);
            // Smooth correction = small steady non-zero adjustments every placement.
            if (ms[0] > minDelta && ms[1] <= maxRotStd) {
                yawScore = clamp01((maxRotStd - ms[1]) / Math.max(0.25D, maxRotStd));
            }
        }

        return clamp01(0.6D * pitchScore + 0.4D * yawScore);
    }

    // -------------------------------------------------------------- raytrace

    private static double raytraceScore(VezAntiCheat plugin, ScaffoldPlacement cur, String check) {
        double maxAngle = plugin.tierCfg().checkDouble(check, "maxAngleError", 28.0D);
        double hardAngle = plugin.tierCfg().checkDouble(check, "hardAngleError", 45.0D);

        double angleScore = cur.alignAngleDeg <= maxAngle ? 0.0D
                : clamp01((cur.alignAngleDeg - maxAngle) / Math.max(1.0D, hardAngle - maxAngle));

        // A ray that does not intersect the against block at all is strong evidence the player was
        // not looking at it (subject to the alignment tolerance above for latency / quick flicks).
        double missScore = (!cur.faceRayHit && cur.alignAngleDeg > maxAngle * 0.6D) ? 0.6D : 0.0D;

        return clamp01(Math.max(angleScore, missScore));
    }

    // -------------------------------------------------------------- legality

    private static double legalityScore(VezAntiCheat plugin, ScaffoldData sd, ScaffoldPlacement cur,
                                        ScaffoldUtil.Context ctx, String check) {
        double score = 0.0D;
        if (!cur.legalSupport) score = Math.max(score, 0.7D);
        if (ctx != null && ctx.invalidSupport) score = Math.max(score, 0.7D);
        if (ctx != null && ctx.hiddenFacePlace) score = Math.max(score, 0.8D);

        // Repeated identical relative placement position (placing at the exact same offset each time).
        double repeatedStd = plugin.tierCfg().checkDouble(check, "repeatedRelativeStd", 0.035D);
        int minSamples = plugin.tierCfg().checkInt(check, "minSamples", 7);
        List<Double> rx = new ArrayList<Double>();
        List<Double> rz = new ArrayList<Double>();
        for (ScaffoldPlacement pl : sd.placements) {
            rx.add(pl.relX);
            rz.add(pl.relZ);
        }
        if (rx.size() >= minSamples) {
            double sx = meanStd(rx)[1];
            double sz = meanStd(rz)[1];
            if (sx <= repeatedStd && sz <= repeatedStd) {
                score = Math.max(score, 0.55D);
            }
        }
        return clamp01(score);
    }

    // ------------------------------------------------------------------ sync

    private static double syncScore(VezAntiCheat plugin, ScaffoldData sd, double timingScore, String check) {
        // Corroborator only: backward/diagonal placement is normal for speed-bridging, so it can only
        // raise suspicion when timing is ALSO robotic. Legit speed-bridgers keep natural timing variance
        // (timingScore low) and therefore never trip this.
        if (timingScore < 0.5D) return 0.0D;

        int minSamples = plugin.tierCfg().checkInt(check, "minSamples", 7);
        int moving = 0;
        int backward = 0;
        for (ScaffoldPlacement pl : sd.placements) {
            if (Double.isNaN(pl.lookVsMoveDeg)) continue;
            moving++;
            if (pl.lookVsMoveDeg >= 110.0D) backward++;     // travelling away from the look heading
        }
        if (moving < minSamples) return 0.0D;
        double backwardRatio = (double) backward / moving;
        if (backwardRatio < 0.6D) return 0.0D;
        return clamp01((backwardRatio - 0.6D) / 0.4D) * timingScore;
    }

    // -------------------------------------------------------------- helpers

    /** Returns {mean, sampleStdDev} of the values (std is 0 for <2 samples). */
    static double[] meanStd(List<Double> vals) {
        if (vals == null || vals.isEmpty()) return new double[] {0.0D, 0.0D};
        double sum = 0.0D;
        for (double v : vals) sum += v;
        double mean = sum / vals.size();
        if (vals.size() < 2) return new double[] {mean, 0.0D};
        double sq = 0.0D;
        for (double v : vals) {
            double d = v - mean;
            sq += d * d;
        }
        return new double[] {mean, Math.sqrt(sq / (vals.size() - 1))};
    }

    static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }

    private static double r(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }
}
