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
 * AutoClickB -- Strict Adjacent-Bucket Autoclicker Detection
 *
 * <p><b>What it detects:</b> Autoclickers with even tighter timing than AutoClickA.
 * This check uses stricter thresholds: band width &lt;= 1.0, two-bucket ratio &gt;= 0.95,
 * CV &lt;= 0.08, outlier ratio &lt;= 0.05. Targets clients that lock CPS to a single
 * integer bucket (e.g., exactly 14 CPS with negligible variance).</p>
 *
 * <p><b>Algorithm:</b> Identical to AutoClickA (inherits stats() and isCombatSwing()),
 * but evaluates with tighter suspicious thresholds and a smaller window (38 swings)
 * and lower buffer requirement (bufferToFlag 3). This makes it catch blatant
 * autoclickers faster at the cost of a slightly narrower detection scope.</p>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code armSwings} -- shared swing timestamp deque from AutoClickA.</li>
 *   <li>{@code autoClickBVerbose} -- independent buffer counter for this check.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> bufferToFlag default 3 (stricter = faster flag).
 * Higher VL weight (1.15) reflects higher confidence from tighter thresholds.</p>
 *
 * <p><b>Exemptions:</b> Same as AutoClickA (non-combat swings, trades, jumps,
 * block-hits, CPS out of range 9-16).</p>
 *
 * <p><b>False positive protections:</b> Even tighter constraints mean only truly
 * robotic clicking patterns pass; requires combat isPrecisionSample and low
 * attack interval std (&lt;= 14ms).</p>
 *
 * <p><b>Connections:</b> Extends AutoClickA. Independent from AutoClickC.</p>
 */
public final class PrismAutoClickB extends TierCheck {

    public PrismAutoClickB(VezAntiCheat plugin) {
        super(plugin, "PrismAutoClickB", CheckTier.PRISM);
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null || PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!isCombatSwing(now, p, data)) {
            data.setAutoClickBVerbose(Math.max(0, data.getAutoClickBVerbose() - 1));
            decay(p, 1.0);
            return;
        }

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean() || combat.isTrade() || combat.isRecentJump() || combat.isRecentBlockhit()) {
            data.setAutoClickBVerbose(Math.max(0, data.getAutoClickBVerbose() - 1));
            decay(p, 0.9);
            return;
        }

        ClickStats s = stats(data.getArmSwings(), plugin.tierCfg().checkInt(name(), "windowSwings", 38));
        if (s.samples < plugin.tierCfg().checkInt(name(), "minSamples", 22)) {
            decay(p, 0.7);
            return;
        }

        double minCps = plugin.tierCfg().checkDouble(name(), "minTrackedCps", 9.0);
        double maxCps = plugin.tierCfg().checkDouble(name(), "maxTrackedCps", 16.0);
        if (s.meanCps < minCps || s.meanCps > maxCps) {
            data.setAutoClickBVerbose(Math.max(0, data.getAutoClickBVerbose() - 1));
            decay(p, 0.8);
            return;
        }

        boolean suspicious = s.bandWidth <= plugin.tierCfg().checkDouble(name(), "maxBandWidth", 1.0)
                && s.twoBucketRatio >= plugin.tierCfg().checkDouble(name(), "minTwoBucketRatio", 0.95)
                && s.stdMs <= plugin.tierCfg().checkDouble(name(), "maxStdMs", 5.0)
                && s.cv <= plugin.tierCfg().checkDouble(name(), "maxCv", 0.08)
                && s.outlierRatio <= plugin.tierCfg().checkDouble(name(), "maxOutlierRatio", 0.05)
                && combat.isPrecisionSample()
                && combat.getAttackIntervalStdMs() <= plugin.tierCfg().checkDouble(name(), "maxAttackStdMs", 14.0);

        if (suspicious) {
            int vb = data.getAutoClickBVerbose() + 1;
            data.setAutoClickBVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 3)) {
                PrismPatternSupport.ClickPatternScore pattern =
                        PrismPatternSupport.analyzeClickPattern(plugin, name(), data.getArmSwings(),
                                plugin.tierCfg().checkInt(name(), "windowSwings", 38));
                failWithMitigation(p, data, 1.15D,
                        PrismCheckLabels.autoClickerReason("adjacent band=" + r(s.minBucketCps) + "-" + r(s.maxBucketCps)
                                + " cps=" + r(s.meanCps) + " two=" + r(s.twoBucketRatio)),
                        pattern.triangleLike ? PrismMitigationPolicy.Confidence.HIGH
                                : PrismMitigationPolicy.Confidence.MODERATE);
                data.setAutoClickBVerbose(0);
            }
        } else {
            data.setAutoClickBVerbose(Math.max(0, data.getAutoClickBVerbose() - 1));
            decay(p, 0.8);
        }
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
