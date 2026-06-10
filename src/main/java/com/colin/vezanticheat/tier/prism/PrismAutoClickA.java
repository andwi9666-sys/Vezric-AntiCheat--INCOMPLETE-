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
 * AutoClickA -- CPS Band-Width / Two-Bucket Ratio Autoclicker Detection
 *
 * <p><b>What it detects:</b> Autoclicker modules that produce click intervals locked
 * to a narrow CPS band (e.g., 12-13 CPS). Human jitter/butterfly clicking spreads
 * across 3+ CPS buckets; autoclickers concentrate nearly all intervals into 1-2
 * adjacent integer CPS buckets with very low CV (coefficient of variation).</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>On each arm swing during combat, record the swing timestamp.</li>
 *   <li>From the most recent N swings (windowSwings, default 42), compute click
 *       intervals and filter outliers (&gt; maxIntervalMs).</li>
 *   <li>Bucket intervals by integer CPS. Compute: mean CPS, std in ms, CV,
 *       two-bucket ratio (best pair of adjacent CPS buckets / total), outlier ratio,
 *       and total band width (max bucket - min bucket).</li>
 *   <li>Flag when band width is narrow (default &lt;= 1.15), two-bucket ratio is high
 *       (default &gt;= 0.92), std is low (default &lt;= 4.4ms), CV is low (default &lt;= 0.075),
 *       outlier ratio is low, AND combat context confirms precision.</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code armSwings} -- deque of arm swing timestamps.</li>
 *   <li>{@code autoClickAVerbose} -- buffer counter.</li>
 *   <li>Combat context fields (lastUseEntityAttack, lastUseEntityTime).</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Verbose counter increments by 1 per suspicious
 * swing. Flags at bufferToFlag (default 4). Decrements by 1 on clean swings.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Swing is not a combat swing (player is mining a block or not attacking).</li>
 *   <li>Combat context not clean, is a trade, recent jump, or recent block-hit.</li>
 *   <li>CPS outside tracked range (below 9 or above 17.5 -- legitimate or butterfly).</li>
 *   <li>Insufficient samples.</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Requires combat isPrecisionSample and low
 * attack interval std; CPS range filters out normal clicking and extreme butterfly;
 * minimum sample count prevents premature evaluation; combat context filtering
 * removes unreliable data from jumps/trades/block-hits.</p>
 *
 * <p><b>Connections:</b> AutoClickB and AutoClickC extend this class, sharing the
 * {@code stats()} and {@code isCombatSwing()} utility methods. Does not feed
 * KillAuraH aggregate.</p>
 */
public final class PrismAutoClickA extends TierCheck {

    public PrismAutoClickA(VezAntiCheat plugin) {
        super(plugin, "PrismAutoClickA", CheckTier.PRISM);
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!isCombatSwing(now, p, data)) {
            data.setAutoClickAVerbose(Math.max(0, data.getAutoClickAVerbose() - 1));
            decay(p, 1.0);
            return;
        }

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean() || combat.isTrade() || combat.isRecentJump() || combat.isRecentBlockhit()) {
            data.setAutoClickAVerbose(Math.max(0, data.getAutoClickAVerbose() - 1));
            decay(p, 0.9);
            return;
        }

        ClickStats s = stats(data.getArmSwings(), plugin.tierCfg().checkInt(name(), "windowSwings", 42));
        if (s.samples < plugin.tierCfg().checkInt(name(), "minSamples", 24)) {
            decay(p, 0.7);
            return;
        }

        double minTrackedCps = plugin.tierCfg().checkDouble(name(), "minTrackedCps", 9.0);
        double maxTrackedCps = plugin.tierCfg().checkDouble(name(), "maxTrackedCps", 17.5);
        if (s.meanCps < minTrackedCps || s.meanCps > maxTrackedCps) {
            data.setAutoClickAVerbose(Math.max(0, data.getAutoClickAVerbose() - 1));
            decay(p, 0.8);
            return;
        }

        boolean suspicious = s.bandWidth <= plugin.tierCfg().checkDouble(name(), "maxBandWidth", 1.15)
                && s.twoBucketRatio >= plugin.tierCfg().checkDouble(name(), "minTwoBucketRatio", 0.92)
                && s.stdMs <= plugin.tierCfg().checkDouble(name(), "maxStdMs", 4.4)
                && s.cv <= plugin.tierCfg().checkDouble(name(), "maxCv", 0.075)
                && s.outlierRatio <= plugin.tierCfg().checkDouble(name(), "maxOutlierRatio", 0.08)
                && combat.isPrecisionSample()
                && combat.getAttackIntervalStdMs() <= plugin.tierCfg().checkDouble(name(), "maxAttackStdMs", 16.0);

        if (suspicious) {
            int vb = data.getAutoClickAVerbose() + 1;
            data.setAutoClickAVerbose(vb);

            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 4)) {
                PrismPatternSupport.ClickPatternScore pattern =
                        PrismPatternSupport.analyzeClickPattern(plugin, name(), data.getArmSwings(),
                                plugin.tierCfg().checkInt(name(), "windowSwings", 42));
                failWithMitigation(p, data, 1.0,
                        PrismCheckLabels.autoClickerReason(
                                "band=" + r(s.minBucketCps) + "-" + r(s.maxBucketCps)
                                        + " cps=" + r(s.meanCps)
                                        + " two=" + r(s.twoBucketRatio)
                                        + " pat=" + (pattern.triangleLike ? "tri" : "no")
                                        + " " + combat.debugSummary()),
                        pattern.triangleLike ? PrismMitigationPolicy.Confidence.MODERATE
                                : PrismMitigationPolicy.Confidence.LOW);
                data.setAutoClickAVerbose(0);
            }
        } else {
            data.setAutoClickAVerbose(Math.max(0, data.getAutoClickAVerbose() - 1));
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
