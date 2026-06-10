package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.prism.PrismInteractionEvaluator;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Polar-style pattern analyzers for Prism tier: click triangle distribution,
 * scaffold perfect-streak tracking, and unified interaction legality scoring.
 */
public final class PrismPatternSupport {

    private PrismPatternSupport() {}

    // --- Click pattern (Polar "triangle" / fake-random CPS) ---

    public static ClickPatternScore analyzeClickPattern(VezAntiCheat plugin, String checkName,
                                                        Deque<Long> swings, int maxSwings) {
        if (swings == null || swings.size() < 8) {
            return ClickPatternScore.empty();
        }

        int window = plugin != null ? plugin.tierCfg().checkInt(checkName, "windowSwings", 42) : maxSwings;
        long maxIntervalMs = plugin != null ? plugin.tierCfg().checkLong(checkName, "maxIntervalMs", 300L) : 300L;
        int minIntervals = plugin != null ? plugin.tierCfg().checkInt(checkName, "minIntervals", 12) : 12;

        List<Long> times = new ArrayList<Long>(swings);
        int start = Math.max(0, times.size() - window);
        List<Long> intervals = new ArrayList<Long>();
        for (int i = start + 1; i < times.size(); i++) {
            long dt = times.get(i) - times.get(i - 1);
            if (dt > 0L && dt < maxIntervalMs) intervals.add(dt);
        }
        if (intervals.size() < minIntervals) return ClickPatternScore.empty();

        double meanMs = avg(intervals);
        double stdMs = std(intervals, meanMs);
        double cv = meanMs > 0.0 ? stdMs / meanMs : 999.0;
        double meanCps = meanMs > 0.0 ? 1000.0 / meanMs : 0.0;

        double minCps = Double.MAX_VALUE;
        double maxCps = 0.0;
        int middleBand = 0;
        int extremes = 0;
        int bestAdjacent = 0;
        java.util.Map<Integer, Integer> buckets = new java.util.HashMap<Integer, Integer>();

        for (Long dt : intervals) {
            double cps = 1000.0 / dt.doubleValue();
            if (cps < minCps) minCps = cps;
            if (cps > maxCps) maxCps = cps;
            int bucket = (int) Math.floor(cps);
            buckets.put(bucket, buckets.containsKey(bucket) ? buckets.get(bucket) + 1 : 1);
        }

        double bandLow = minCps + (maxCps - minCps) * 0.15D;
        double bandHigh = maxCps - (maxCps - minCps) * 0.15D;
        for (Long dt : intervals) {
            double cps = 1000.0 / dt.doubleValue();
            if (cps >= bandLow && cps <= bandHigh) middleBand++;
            if (cps <= minCps + 0.35D || cps >= maxCps - 0.35D) extremes++;
        }

        for (java.util.Map.Entry<Integer, Integer> entry : buckets.entrySet()) {
            int total = entry.getValue();
            Integer next = buckets.get(entry.getKey() + 1);
            if (next != null) total += next;
            if (total > bestAdjacent) bestAdjacent = total;
        }

        double bandOccupancy = middleBand / (double) intervals.size();
        double extremeHitRatio = extremes / (double) intervals.size();
        double twoBucketRatio = bestAdjacent / (double) intervals.size();
        double bandWidth = Math.max(0.0, Math.floor(maxCps) - Math.floor(minCps));

        int slices = plugin != null ? plugin.tierCfg().checkInt(checkName, "timeSlices", 4) : 4;
        double windowSpread = computeWindowSpread(times, start, slices);

        double minBandOcc = plugin != null
                ? plugin.tierCfg().checkDouble(checkName, "minBandOccupancy", 0.72D) : 0.72D;
        double maxExtreme = plugin != null
                ? plugin.tierCfg().checkDouble(checkName, "maxExtremeHitRatio", 0.08D) : 0.08D;
        double maxSpread = plugin != null
                ? plugin.tierCfg().checkDouble(checkName, "maxWindowSpread", 0.55D) : 0.55D;
        double maxCv = plugin != null ? plugin.tierCfg().checkDouble(checkName, "maxCv", 0.085D) : 0.085D;
        double minTwoBucket = plugin != null
                ? plugin.tierCfg().checkDouble(checkName, "minTwoBucketRatio", 0.88D) : 0.88D;

        boolean triangleLike = bandOccupancy >= minBandOcc
                && extremeHitRatio <= maxExtreme
                && twoBucketRatio >= minTwoBucket
                && cv <= maxCv
                && windowSpread <= maxSpread
                && bandWidth >= 1.0D;

        double confidence = 0.0D;
        if (triangleLike) {
            confidence = Math.min(1.0D,
                    (bandOccupancy * 0.35D) + ((1.0D - extremeHitRatio) * 0.25D)
                            + (twoBucketRatio * 0.25D) + ((1.0D - Math.min(1.0D, cv / maxCv)) * 0.15D));
        }

        String debug = "cps=" + round(meanCps) + " bandOcc=" + round(bandOccupancy)
                + " extreme=" + round(extremeHitRatio) + " two=" + round(twoBucketRatio)
                + " cv=" + round(cv) + " spread=" + round(windowSpread);
        return new ClickPatternScore(intervals.size(), meanCps, stdMs, cv, bandOccupancy,
                extremeHitRatio, twoBucketRatio, windowSpread, bandWidth, triangleLike, confidence, debug);
    }

    private static double computeWindowSpread(List<Long> times, int start, int slices) {
        if (times.size() - start < slices + 1 || slices <= 1) return 99.0D;
        int end = times.size();
        int span = end - start;
        int sliceSize = Math.max(1, span / slices);
        double minSliceCps = Double.MAX_VALUE;
        double maxSliceCps = 0.0D;
        for (int s = 0; s < slices; s++) {
            int sliceStart = start + s * sliceSize;
            int sliceEnd = (s == slices - 1) ? end : Math.min(end, sliceStart + sliceSize);
            if (sliceEnd - sliceStart < 2) continue;
            long dt = times.get(sliceEnd - 1) - times.get(sliceStart);
            if (dt <= 0L) continue;
            double cps = (sliceEnd - sliceStart - 1) * 1000.0 / dt;
            if (cps < minSliceCps) minSliceCps = cps;
            if (cps > maxSliceCps) maxSliceCps = cps;
        }
        if (minSliceCps == Double.MAX_VALUE) return 99.0D;
        return maxSliceCps - minSliceCps;
    }

    // --- Scaffold perfect-streak pattern ---

    public static ScaffoldPatternScore analyzeScaffoldPattern(VezAntiCheat plugin, String checkName,
                                                              PlayerData data, ScaffoldUtil.Context ctx,
                                                              ScaffoldUtil.Stats timingStats) {
        if (data == null) return ScaffoldPatternScore.empty();
        if (ctx == null) {
            int minStreak = plugin != null
                    ? plugin.tierCfg().checkInt(checkName, "minPerfectStreak", 7) : 7;
            boolean suspicious = data.getPrismPerfectPlacementStreak() >= minStreak;
            return new ScaffoldPatternScore(data.getPrismPerfectPlacementStreak(), false,
                    suspicious, suspicious ? 0.7D : 0.0D, "streak-only");
        }

        if (ctx.closeBuildLike || ctx.edgeBridgeLike || ctx.extensionBridgeLike || ctx.bridgeGeometryLenient) {
            data.setPrismPerfectPlacementStreak(Math.max(0, data.getPrismPerfectPlacementStreak() - 2));
            return new ScaffoldPatternScore(data.getPrismPerfectPlacementStreak(), false,
                    false, 0.0D, "legit-bridge");
        }

        double maxTimingCv = plugin.tierCfg().checkDouble(checkName, "maxTimingCv", 0.10D);
        double maxPitchRange = plugin.tierCfg().checkDouble(checkName, "maxPitchRange", 5.0D);
        double maxSpreadMs = plugin.tierCfg().checkDouble(checkName, "maxSpreadMs", 75.0D);
        double maxAverageMs = plugin.tierCfg().checkDouble(checkName, "maxAverageMs", 175.0D);
        int pitchSample = plugin.tierCfg().checkInt(checkName, "pitchSampleSize", 6);

        double pitchRange = ScaffoldUtil.pitchCv(data.getScaffoldPitchHistory(), pitchSample);
        if (hasBridgeHumanImperfection(timingStats, pitchRange, maxTimingCv, maxPitchRange, maxSpreadMs)) {
            data.setPrismPerfectPlacementStreak(0);
            return new ScaffoldPatternScore(0, false, false, 0.0D,
                    "human-imperfection cv=" + round(timingStats != null ? timingStats.cv : -1)
                            + " pitchRange=" + round(pitchRange));
        }

        boolean perfectTiming = hasMachineBridgePace(timingStats, maxTimingCv, maxSpreadMs, maxAverageMs);
        boolean perfectPitch = pitchRange <= maxPitchRange;
        boolean cleanSync = ctx.quality >= 0.72D && ctx.moveH >= 0.08D;

        boolean perfectTick = perfectTiming && perfectPitch && cleanSync;
        int streak = data.getPrismPerfectPlacementStreak();
        if (perfectTick) {
            streak++;
        } else {
            streak = Math.max(0, streak - 1);
        }
        data.setPrismPerfectPlacementStreak(streak);

        int minStreak = plugin.tierCfg().checkInt(checkName, "minPerfectStreak", 7);
        boolean suspicious = streak >= minStreak;
        double confidence = suspicious ? Math.min(1.0D, streak / (double) (minStreak + 3)) : 0.0D;
        String debug = "streak=" + streak + "/" + minStreak
                + " timingCv=" + round(timingStats != null ? timingStats.cv : -1)
                + " pitchRange=" + round(pitchRange);
        return new ScaffoldPatternScore(streak, perfectTick, suspicious, confidence, debug);
    }

    private static boolean isHumanImperfection(ScaffoldUtil.Stats stats, double pitchRange,
                                               double maxCv, double maxPitch, double maxSpreadMs) {
        if (stats == null) return true;
        if (stats.cv > maxCv * 1.12D) return true;
        if (pitchRange > maxPitch * 1.12D) return true;
        if (stats.max - stats.min > maxSpreadMs * 1.02D) return true;
        if (stats.avg > 0.0D && stats.sd > stats.avg * maxCv * 1.35D) return true;
        if (hasPlacementIntervalOutlier(stats, maxCv)) return true;
        return false;
    }

    public static boolean hasBridgeHumanImperfection(ScaffoldUtil.Stats stats, double pitchRange,
                                                     double maxTimingCv, double maxPitchRange,
                                                     double maxSpreadMs) {
        return isHumanImperfection(stats, pitchRange, maxTimingCv, maxPitchRange, maxSpreadMs);
    }

    private static boolean hasPlacementIntervalOutlier(ScaffoldUtil.Stats stats, double maxCv) {
        if (stats == null || stats.avg <= 0.0D) return false;
        double tolerance = Math.max(18.0D, stats.avg * maxCv * 2.5D);
        return stats.max - stats.min > tolerance;
    }

    private static boolean hasMachineBridgePace(ScaffoldUtil.Stats stats, double maxTimingCv,
                                                double maxSpreadMs, double maxAverageMs) {
        if (stats == null) return false;
        return stats.cv <= maxTimingCv * 0.82D
                && stats.max - stats.min <= maxSpreadMs * 0.82D
                && stats.avg <= maxAverageMs;
    }

    // --- Interaction legality (unified combat) — delegates to full evaluator ---

    public static InteractionLegalityResult evaluateInteraction(VezAntiCheat plugin, String checkName,
                                                                org.bukkit.entity.Player p, PlayerData data,
                                                                long now) {
        PrismInteractionEvaluator.InteractionResult r =
                PrismInteractionEvaluator.evaluate(plugin, checkName, p, data, now);
        if (r.signalCount <= 0 && !r.hitboxPattern) {
            return InteractionLegalityResult.clean();
        }
        return new InteractionLegalityResult(r.outOfRange, r.outOfSight, r.staleRotation, r.hitboxMiss || r.hitboxPattern,
                r.blatant, r.signalCount, r.confidence, r.debug);
    }

    private static double avg(List<Long> values) {
        double sum = 0.0;
        for (Long v : values) sum += v;
        return values.isEmpty() ? 0.0 : sum / values.size();
    }

    private static double std(List<Long> values, double mean) {
        if (values.isEmpty()) return 999.0;
        double var = 0.0;
        for (Long v : values) {
            double d = v - mean;
            var += d * d;
        }
        return Math.sqrt(var / values.size());
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static final class ClickPatternScore {
        public final int samples;
        public final double meanCps;
        public final double stdMs;
        public final double cv;
        public final double bandOccupancy;
        public final double extremeHitRatio;
        public final double twoBucketRatio;
        public final double windowSpread;
        public final double bandWidth;
        public final boolean triangleLike;
        public final double confidence;
        public final String debug;

        ClickPatternScore(int samples, double meanCps, double stdMs, double cv,
                          double bandOccupancy, double extremeHitRatio, double twoBucketRatio,
                          double windowSpread, double bandWidth, boolean triangleLike,
                          double confidence, String debug) {
            this.samples = samples;
            this.meanCps = meanCps;
            this.stdMs = stdMs;
            this.cv = cv;
            this.bandOccupancy = bandOccupancy;
            this.extremeHitRatio = extremeHitRatio;
            this.twoBucketRatio = twoBucketRatio;
            this.windowSpread = windowSpread;
            this.bandWidth = bandWidth;
            this.triangleLike = triangleLike;
            this.confidence = confidence;
            this.debug = debug;
        }

        static ClickPatternScore empty() {
            return new ClickPatternScore(0, 0, 999, 999, 0, 1, 0, 99, 99, false, 0, "empty");
        }
    }

    public static final class ScaffoldPatternScore {
        public final int perfectStreak;
        public final boolean perfectTick;
        public final boolean suspicious;
        public final double confidence;
        public final String debug;

        ScaffoldPatternScore(int perfectStreak, boolean perfectTick, boolean suspicious,
                             double confidence, String debug) {
            this.perfectStreak = perfectStreak;
            this.perfectTick = perfectTick;
            this.suspicious = suspicious;
            this.confidence = confidence;
            this.debug = debug;
        }

        static ScaffoldPatternScore empty() {
            return new ScaffoldPatternScore(0, false, false, 0, "empty");
        }
    }

    public static final class InteractionLegalityResult {
        public final boolean outOfRange;
        public final boolean outOfSight;
        public final boolean staleRotation;
        public final boolean hitboxMiss;
        public final boolean blatant;
        public final int signalCount;
        public final double confidence;
        public final String debug;

        InteractionLegalityResult(boolean outOfRange, boolean outOfSight, boolean staleRotation,
                                boolean hitboxMiss, boolean blatant, int signalCount,
                                double confidence, String debug) {
            this.outOfRange = outOfRange;
            this.outOfSight = outOfSight;
            this.staleRotation = staleRotation;
            this.hitboxMiss = hitboxMiss;
            this.blatant = blatant;
            this.signalCount = signalCount;
            this.confidence = confidence;
            this.debug = debug;
        }

        static InteractionLegalityResult clean() {
            return new InteractionLegalityResult(false, false, false, false, false, 0, 0, "clean");
        }
    }
}
