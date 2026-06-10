package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.PrismCheckLabels;
import com.colin.vezanticheat.utils.PrismPatternSupport;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * AutoClickC -- Sustained CPS Stability (Window-Spread) Detection
 *
 * <p><b>What it detects:</b> Autoclickers that maintain a stable CPS over extended
 * time periods. Even if individual intervals have slight jitter, the overall CPS
 * across time slices remains unnaturally constant. This catches "humanized"
 * autoclickers that add random noise but fail to simulate the natural drift in
 * clicking speed that humans exhibit over 5-10 second windows.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Divide the swing window into N time slices (default 4).</li>
 *   <li>Compute the CPS of each slice independently.</li>
 *   <li>Compute "window spread" = max slice CPS - min slice CPS.</li>
 *   <li>Flag when spread is low (default &lt;= 0.55 CPS difference between slices),
 *       combined with narrow band width, high two-bucket ratio, and low CV.</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code armSwings} -- shared deque from AutoClickA.</li>
 *   <li>{@code autoClickCVerbose} -- independent buffer counter.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> bufferToFlag default 4. VL weight 1.2
 * (high confidence from sustained pattern detection).</p>
 *
 * <p><b>Exemptions:</b> Same as AutoClickA. Additionally, insufficient slice
 * samples (minSliceSamples 3) return early.</p>
 *
 * <p><b>False positive protections:</b> Window spread is the key differentiator --
 * legitimate players naturally speed up/slow down over seconds. The 0.55 CPS
 * threshold is well below normal human variance (~1.5-3.0 CPS spread). Requires
 * combat precision context and minimum tracked swings.</p>
 *
 * <p><b>Connections:</b> Extends AutoClickA. Independent from AutoClickB.</p>
 */
public final class PrismAutoClickC extends TierCheck {

    public PrismAutoClickC(VezAntiCheat plugin) {
        super(plugin, "PrismAutoClickC", CheckTier.PRISM);
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null || PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!isCombatSwing(now, p, data)) {
            data.setAutoClickCVerbose(Math.max(0, data.getAutoClickCVerbose() - 1));
            decay(p, 1.0);
            return;
        }

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean() || combat.isTrade() || combat.isRecentJump() || combat.isRecentBlockhit()) {
            data.setAutoClickCVerbose(Math.max(0, data.getAutoClickCVerbose() - 1));
            decay(p, 0.9);
            return;
        }

        int windowSwings = plugin.tierCfg().checkInt(name(), "windowSwings", 40);
        ClickStats s = stats(data.getArmSwings(), windowSwings);
        if (s.samples < plugin.tierCfg().checkInt(name(), "minSamples", 24)) {
            decay(p, 0.7);
            return;
        }

        double minCps = plugin.tierCfg().checkDouble(name(), "minTrackedCps", 9.0);
        double maxCps = plugin.tierCfg().checkDouble(name(), "maxTrackedCps", 16.0);
        if (s.meanCps < minCps || s.meanCps > maxCps) {
            data.setAutoClickCVerbose(Math.max(0, data.getAutoClickCVerbose() - 1));
            decay(p, 0.8);
            return;
        }

        double windowSpread = windowSpread(data.getArmSwings(), windowSwings, plugin.tierCfg().checkInt(name(), "sliceCount", 4));
        boolean suspicious = s.bandWidth <= plugin.tierCfg().checkDouble(name(), "maxBandWidth", 1.25)
                && s.twoBucketRatio >= plugin.tierCfg().checkDouble(name(), "minTwoBucketRatio", 0.90)
                && windowSpread <= plugin.tierCfg().checkDouble(name(), "maxWindowSpread", 0.55)
                && s.cv <= plugin.tierCfg().checkDouble(name(), "maxCv", 0.09)
                && combat.isPrecisionSample()
                && combat.getAttackIntervalStdMs() <= plugin.tierCfg().checkDouble(name(), "maxAttackStdMs", 18.0);

        if (suspicious) {
            int vb = data.getAutoClickCVerbose() + 1;
            data.setAutoClickCVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 4)) {
                failWithMitigation(p, data, 1.2D,
                        PrismCheckLabels.autoClickerReason("sustain cps=" + r(s.meanCps)
                                + " spread=" + r(windowSpread) + " band=" + r(s.minBucketCps) + "-" + r(s.maxBucketCps)),
                        PrismMitigationPolicy.Confidence.MODERATE);
                data.setAutoClickCVerbose(0);
            }
        } else {
            data.setAutoClickCVerbose(Math.max(0, data.getAutoClickCVerbose() - 1));
            decay(p, 0.8);
        }
    }

    private double windowSpread(Deque<Long> swings, int maxSwings, int slices) {
        if (swings == null
                || swings.size() < plugin.tierCfg().checkInt(name(), "minTrackedSwings", 10)
                || slices <= 1) return 999.0;

        List<Long> times = new ArrayList<Long>(swings);
        int start = Math.max(0, times.size() - maxSwings);
        List<Long> sub = times.subList(start, times.size());
        int minSliceSamples = plugin.tierCfg().checkInt(name(), "minSliceSamples", 3);
        if (sub.size() < slices * minSliceSamples) return 999.0;

        int sliceSize = sub.size() / slices;
        if (sliceSize < minSliceSamples) return 999.0;

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (int i = 0; i < slices; i++) {
            int from = i * sliceSize;
            int to = (i == slices - 1) ? sub.size() : Math.min(sub.size(), from + sliceSize);
            if (to - from < minSliceSamples) continue;

            long first = sub.get(from);
            long last = sub.get(to - 1);
            long span = Math.max(1L, last - first);
            double cps = ((to - from) * 1000.0) / span;

            if (cps < min) min = cps;
            if (cps > max) max = cps;
        }

        if (min == Double.MAX_VALUE || max == Double.MIN_VALUE) return 999.0;
        return max - min;
    }

    protected boolean isCombatSwing(long now, Player p, PlayerData data) {
        if (data.wasLastUseEntityAttack()
                && (now - data.getLastUseEntityTime() <= plugin.tierCfg().checkLong(name(), "combatSwingWindowMs", 180L))) {
            return true;
        }

        Block target = getTargetBlockCompat(p, plugin.tierCfg().checkInt(name(), "targetBlockDistance", 5));
        return target == null || target.getType() == Material.AIR;
    }

    @SuppressWarnings("deprecation")
    private Block getTargetBlockCompat(Player p, int maxDistance) {
        try {
            return p.getTargetBlock((HashSet<Byte>) null, maxDistance);
        } catch (Throwable t) {
            return null;
        }
    }

    protected ClickStats stats(Deque<Long> swings, int maxSwings) {
        int minTrackedSwings = plugin.tierCfg().checkInt(name(), "minTrackedSwings", 8);
        if (swings == null || swings.size() < minTrackedSwings) {
            return ClickStats.empty();
        }

        List<Long> times = new ArrayList<Long>(swings);
        int start = Math.max(0, times.size() - maxSwings);
        List<Long> sub = times.subList(start, times.size());

        List<Long> intervals = new ArrayList<Long>();
        for (int i = 1; i < sub.size(); i++) {
            long dt = sub.get(i) - sub.get(i - 1);
            if (dt > 0L && dt < plugin.tierCfg().checkLong(name(), "maxIntervalMs", 300L)) {
                intervals.add(dt);
            }
        }
        if (intervals.size() < plugin.tierCfg().checkInt(name(), "minIntervals", 6)) {
            return ClickStats.empty();
        }

        double meanMs = avg(intervals);
        double stdMs = std(intervals, meanMs);
        double cv = meanMs > 0.0 ? stdMs / meanMs : 999.0;
        double meanCps = meanMs > 0.0 ? 1000.0 / meanMs : 0.0;

        Map<Integer, Integer> cpsBuckets = new HashMap<Integer, Integer>();
        int minBucket = Integer.MAX_VALUE;
        int maxBucket = Integer.MIN_VALUE;
        int outliers = 0;

        for (Long dt : intervals) {
            double cps = 1000.0 / dt.doubleValue();
            int bucket = (int) Math.floor(cps);
            cpsBuckets.put(bucket, cpsBuckets.containsKey(bucket) ? cpsBuckets.get(bucket) + 1 : 1);
            if (bucket < minBucket) minBucket = bucket;
            if (bucket > maxBucket) maxBucket = bucket;
            if (Math.abs(cps - meanCps) > plugin.tierCfg().checkDouble(name(), "outlierCpsDelta", 1.10)) outliers++;
        }

        int bestAdjacent = 0;
        for (Map.Entry<Integer, Integer> entry : cpsBuckets.entrySet()) {
            int bucket = entry.getKey();
            int total = entry.getValue().intValue();
            Integer next = cpsBuckets.get(bucket + 1);
            if (next != null) total += next.intValue();
            if (total > bestAdjacent) bestAdjacent = total;
        }

        double twoBucketRatio = intervals.isEmpty() ? 0.0 : bestAdjacent / (double) intervals.size();
        double outlierRatio = intervals.isEmpty() ? 0.0 : outliers / (double) intervals.size();
        double bandWidth = Math.max(0.0, maxBucket - minBucket);

        return new ClickStats(
                intervals.size(),
                meanCps,
                stdMs,
                cv,
                twoBucketRatio,
                outlierRatio,
                minBucket,
                maxBucket,
                bandWidth
        );
    }

    private double avg(List<Long> values) {
        double sum = 0.0;
        for (Long value : values) sum += value.doubleValue();
        return values.isEmpty() ? 0.0 : sum / values.size();
    }

    private double std(List<Long> values, double mean) {
        if (values.isEmpty()) return 999.0;
        double variance = 0.0;
        for (Long value : values) {
            double delta = value.doubleValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / values.size());
    }

    protected double r(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    protected static final class ClickStats {
        final int samples;
        final double meanCps;
        final double stdMs;
        final double cv;
        final double twoBucketRatio;
        final double outlierRatio;
        final double minBucketCps;
        final double maxBucketCps;
        final double bandWidth;

        ClickStats(int samples, double meanCps, double stdMs, double cv,
                   double twoBucketRatio, double outlierRatio,
                   double minBucketCps, double maxBucketCps, double bandWidth) {
            this.samples = samples;
            this.meanCps = meanCps;
            this.stdMs = stdMs;
            this.cv = cv;
            this.twoBucketRatio = twoBucketRatio;
            this.outlierRatio = outlierRatio;
            this.minBucketCps = minBucketCps;
            this.maxBucketCps = maxBucketCps;
            this.bandWidth = bandWidth;
        }

        static ClickStats empty() {
            return new ClickStats(0, 0.0, 999.0, 999.0, 0.0, 1.0, 0.0, 0.0, 99.0);
        }
    }

}
