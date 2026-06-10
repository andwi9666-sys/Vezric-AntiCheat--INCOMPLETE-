package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatRewind;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.LagrangeUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Composite lag-range (fake lag + reach) detection (Prism tier). */
public final class PrismLagRange extends TierCheck {

    private final Map<UUID, Deque<HitRecord>> samples = new ConcurrentHashMap<UUID, Deque<HitRecord>>();
    private final Map<UUID, Integer> buffer = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> benignStreak = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, UUID> trackedTarget = new ConcurrentHashMap<UUID, UUID>();

    public PrismLagRange(VezAntiCheat plugin) {
        super(plugin, "PrismLagRange", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;
        if (PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 180L)) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        Entity targetEntity = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (!(targetEntity instanceof Player)) return;

        Player target = (Player) targetEntity;
        PlayerData targetData = plugin.data().get(target);
        if (targetData == null) return;
        resetIfTargetChanged(p.getUniqueId(), target.getUniqueId(), data);

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 90L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.30D),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 200L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = p.getEyeLocation();
        }

        CombatUtil.ReachContext ctx = CombatRewind.broadWindowReachContext(
                plugin, p, data, eye, data.getLastTargetEntityId(), data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) {
            ctx = CombatUtil.analyzeReach(eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        }
        if (ctx == null) return;

        LagrangeUtil.LagrangeSample sample = LagrangeUtil.analyzeHit(
                plugin, name(), p, data, target, targetData, ctx, combat, now);

        if (plugin.tierCfg().checkBoolean(name(), "debug", false)) {
            plugin.getLogger().info("[PrismLagRange][debug] " + p.getName() + " "
                    + sample.debugLine() + " " + combat.debugSummary());
        }

        if (sample.isForgiven() || sample.isSkipped()) {
            UUID id = p.getUniqueId();
            if (sample.isForgiven()) {
                clearHistory(p, id, data, "reset", "forgiven " + safeReason(sample.getTags()));
                return;
            }
            handleBenignHit(p, id, data, sample.getTags());
            return;
        }

        UUID id = p.getUniqueId();
        double minConfidence = plugin.tierCfg().checkDouble(name(), "minSignalConfidence", 1.8D);
        if (sample.getConfidence() < minConfidence) {
            handleBenignHit(p, id, data, "conf=" + round3(sample.getConfidence()));
            return;
        }
        benignStreak.put(id, Integer.valueOf(0));

        Deque<HitRecord> history = samples.computeIfAbsent(id, key -> new ArrayDeque<HitRecord>());
        history.addLast(new HitRecord(
                now,
                sample.getConfidence(),
                sample.isSelectiveLag(),
                sample.isBurstAttack() || sample.isStaleExcess(),
                sample.isReachEvidence(),
                sample.isSpatialEvidence(),
                sample.isSevereStale(),
                sample.getStaleExcessMs()
        ));
        pruneHistory(history, now);
        Deque<HitRecord> cluster = recentCluster(history, now);

        double windowSum = sumConfidence(cluster);
        int strongHits = countStrongHits(cluster, plugin.tierCfg().checkDouble(name(), "strongHitConfidence", 2.4D));
        int selectiveHits = countSelectiveHits(cluster);
        int timingHits = countTimingHits(cluster);
        int reachHits = countReachHits(cluster);
        int spatialHits = countSpatialHits(cluster);
        int severeHits = countSevereHits(cluster);
        double averageStaleExcess = averageStaleExcess(cluster);
        double selectiveRatio = ratio(selectiveHits, cluster.size());
        LagrangeUtil.CombatTeleportSummary teleport = LagrangeUtil.summarizeCombatTeleports(
                data, now, plugin.tierCfg().checkLong(name(), "teleportWindowMs", 3200L));
        int teleportHits = teleport.getCount();
        int teleportSameTarget = teleport.getSameTargetCount();
        double teleportAvgClose = teleport.getAverageCloseDelta();
        double teleportAvgMove = teleport.getAverageMoveH();
        double teleportBestClose = teleport.getBestCloseDelta();

        int b = getI(buffer, id);
        int bufferGain = 1
                + (sample.isSelectiveLag() ? LagProfileUtil.bufferBonus(plugin, p, data, now) : 0)
                + ((sample.isSpatialEvidence() && sample.isReachEvidence()) ? 1 : 0);
        if (teleportHits > 0) {
            bufferGain += Math.min(2, teleportHits);
        }
        b = Math.min(plugin.tierCfg().checkInt(name(), "maxBuffer", 12), b + bufferGain);
        buffer.put(id, Integer.valueOf(b));

        double flagConfidence = plugin.tierCfg().checkDouble(name(), "flagConfidence", 5.5D);
        int minStrongHits = plugin.tierCfg().checkInt(name(), "minStrongHits", 3);
        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 2);
        int minSelectiveHits = plugin.tierCfg().checkInt(name(), "minSelectiveHits", 2);
        int minTimingHits = plugin.tierCfg().checkInt(name(), "minTimingHits", 2);
        int minReachHits = plugin.tierCfg().checkInt(name(), "minReachHits", 2);
        int minSpatialHits = plugin.tierCfg().checkInt(name(), "minSpatialHits", 1);
        int minSevereHits = plugin.tierCfg().checkInt(name(), "minSevereHits", 2);
        double minAverageStaleExcessMs = plugin.tierCfg().checkDouble(name(), "minAverageStaleExcessMs", 55.0D);
        double minSelectiveRatio = plugin.tierCfg().checkDouble(name(), "minSelectiveRatio", 0.45D);
        int minClusterHits = plugin.tierCfg().checkInt(name(), "minClusterHits", 2);
        int minTeleportHits = plugin.tierCfg().checkInt(name(), "minTeleportHits", 2);
        int minTeleportSameTarget = plugin.tierCfg().checkInt(name(), "minTeleportSameTarget", 1);
        double minTeleportAverageClose = plugin.tierCfg().checkDouble(name(), "minTeleportAverageCloseDelta", 1.45D);
        double minTeleportAverageMove = plugin.tierCfg().checkDouble(name(), "minTeleportAverageMoveH", 1.35D);
        double teleportScoreBonus = plugin.tierCfg().checkDouble(name(), "teleportScoreBonus", 1.2D);

        boolean currentSupportsPattern = sample.isReachEvidence()
                && (sample.isStaleExcess() || sample.isBurstAttack() || sample.isSelectiveLag() || teleportHits > 0);
        boolean teleportPattern = teleportHits >= minTeleportHits
                && teleportSameTarget >= minTeleportSameTarget
                && teleportAvgClose >= minTeleportAverageClose
                && teleportAvgMove >= minTeleportAverageMove;

        boolean repeatedPattern = reachHits >= minReachHits
                && (timingHits >= minTimingHits || teleportPattern)
                && (averageStaleExcess >= minAverageStaleExcessMs || teleportPattern)
                && (selectiveHits >= minSelectiveHits || selectiveRatio >= minSelectiveRatio || teleportPattern)
                && spatialHits >= minSpatialHits
                && cluster.size() >= minClusterHits
                && currentSupportsPattern;

        boolean severePattern = severeHits >= minSevereHits
                && reachHits >= minReachHits
                && (timingHits >= minTimingHits || teleportPattern)
                && (selectiveHits >= minSelectiveHits || teleportPattern)
                && cluster.size() >= minClusterHits
                && currentSupportsPattern;

        double totalConfidence = windowSum + (teleportPattern ? teleportScoreBonus : 0.0D);

        boolean shouldFlag = (totalConfidence >= flagConfidence && repeatedPattern)
                || (strongHits >= minStrongHits && repeatedPattern)
                || (b >= bufferToFlag && severePattern);

        if (!shouldFlag) {
            if (cluster.size() >= 2 && (!sample.isSelectiveLag() || !sample.isReachEvidence())) {
                buffer.put(id, Integer.valueOf(Math.max(0, b - 1)));
            }
            return;
        }

        fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                sample.debugLine()
                        + " sum=" + round3(totalConfidence)
                        + " cluster=" + cluster.size()
                        + " strong=" + strongHits
                        + " selHits=" + selectiveHits
                        + " timing=" + timingHits
                        + " reach=" + reachHits
                        + " spatial=" + spatialHits
                        + " severe=" + severeHits
                        + " avgStale=" + round3(averageStaleExcess)
                        + " tp=" + teleportHits
                        + "/" + teleportSameTarget
                        + " tpClose=" + round3(teleportAvgClose)
                        + " tpBest=" + round3(teleportBestClose)
                        + " buf=" + b
                        + " " + combat.debugSummary()
                        + " " + LagProfileUtil.debugSummary(plugin, p, data, now));

        buffer.put(id, Integer.valueOf(0));
        history.clear();
        benignStreak.put(id, Integer.valueOf(0));
    }

    private void pruneHistory(Deque<HitRecord> history, long now) {
        long windowMs = plugin.tierCfg().checkLong(name(), "windowMs", 8000L);
        int maxSamples = plugin.tierCfg().checkInt(name(), "maxSamples", 12);
        Iterator<HitRecord> it = history.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > windowMs) {
                it.remove();
            }
        }
        while (history.size() > maxSamples) {
            history.removeFirst();
        }
    }

    private Deque<HitRecord> recentCluster(Deque<HitRecord> history, long now) {
        Deque<HitRecord> cluster = new ArrayDeque<HitRecord>();
        if (history == null || history.isEmpty()) {
            return cluster;
        }

        long clusterWindowMs = plugin.tierCfg().checkLong(name(), "clusterWindowMs", 3200L);
        long maxInterSampleGapMs = plugin.tierCfg().checkLong(name(), "maxInterSampleGapMs", 1400L);
        HitRecord previous = null;
        Iterator<HitRecord> it = history.descendingIterator();
        while (it.hasNext()) {
            HitRecord current = it.next();
            if ((now - current.time) > clusterWindowMs) {
                break;
            }
            if (previous != null && (previous.time - current.time) > maxInterSampleGapMs) {
                break;
            }
            cluster.addFirst(current);
            previous = current;
        }
        return cluster;
    }

    private double sumConfidence(Deque<HitRecord> history) {
        double sum = 0.0D;
        for (HitRecord record : history) {
            sum += record.confidence;
        }
        return sum;
    }

    private int countStrongHits(Deque<HitRecord> history, double threshold) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.confidence >= threshold) count++;
        }
        return count;
    }

    private int countSelectiveHits(Deque<HitRecord> history) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.selectiveLag) count++;
        }
        return count;
    }

    private int countTimingHits(Deque<HitRecord> history) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.timingEvidence) count++;
        }
        return count;
    }

    private int countReachHits(Deque<HitRecord> history) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.reachEvidence) count++;
        }
        return count;
    }

    private int countSpatialHits(Deque<HitRecord> history) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.spatialEvidence) count++;
        }
        return count;
    }

    private int countSevereHits(Deque<HitRecord> history) {
        int count = 0;
        for (HitRecord record : history) {
            if (record.severeStale) count++;
        }
        return count;
    }

    private double averageStaleExcess(Deque<HitRecord> history) {
        if (history.isEmpty()) return 0.0D;
        double sum = 0.0D;
        int count = 0;
        for (HitRecord record : history) {
            if (record.staleExcessMs <= 0L) continue;
            sum += record.staleExcessMs;
            count++;
        }
        return count == 0 ? 0.0D : (sum / count);
    }

    private static double ratio(int hits, int total) {
        if (total <= 0) return 0.0D;
        return hits / (double) total;
    }

    private void decayBuffer(UUID id, Player p, PlayerData data) {
        Integer b = buffer.get(id);
        if (b != null && b.intValue() > 0) {
            buffer.put(id, Integer.valueOf(Math.max(0, b.intValue() - 1)));
        }
        decay(p, 0.4D);
    }

    private void handleBenignHit(Player p, UUID id, PlayerData data, String reason) {
        int streak = getI(benignStreak, id) + 1;
        benignStreak.put(id, Integer.valueOf(streak));
        int cleanHitsToReset = plugin.tierCfg().checkInt(name(), "cleanHitsToReset", 2);
        if (streak >= cleanHitsToReset && samples.containsKey(id)) {
            clearHistory(p, id, data, "reset",
                    "streak=" + streak + "/" + cleanHitsToReset + " " + safeReason(reason));
            return;
        }
        recordSettleStep(p, id, streak, cleanHitsToReset, reason);
        decayBuffer(id, p, data);
    }

    private void clearHistory(Player p, UUID id, PlayerData data, String stage, String detail) {
        if (p != null && plugin.diagnostics() != null && (samples.containsKey(id) || buffer.containsKey(id))) {
            plugin.diagnostics().record(p.getUniqueId(), name(), stage, sanitizeDetail(detail));
        }
        samples.remove(id);
        buffer.remove(id);
        benignStreak.remove(id);
        decay(p, 0.6D);
    }

    private void resetIfTargetChanged(UUID attackerId, UUID targetId, PlayerData data) {
        if (attackerId == null || targetId == null) return;
        UUID previous = trackedTarget.put(attackerId, targetId);
        if (previous == null || previous.equals(targetId)) return;

        clearHistory(Bukkit.getPlayer(attackerId), attackerId, data, "reset",
                "target-switch " + previous + " -> " + targetId);
    }

    private void recordSettleStep(Player p, UUID id, int streak, int cleanHitsToReset, String reason) {
        if (p == null || plugin.diagnostics() == null) return;
        Deque<HitRecord> history = samples.get(id);
        int b = getI(buffer, id);
        if ((history == null || history.isEmpty()) && b <= 0) return;

        Deque<HitRecord> cluster = recentCluster(history, System.currentTimeMillis());
        plugin.diagnostics().record(p.getUniqueId(), name(), "settle",
                "streak=" + streak + "/" + cleanHitsToReset
                        + " cluster=" + cluster.size()
                        + " history=" + (history == null ? 0 : history.size())
                        + " buf=" + b
                        + " " + safeReason(reason));
    }

    private static int getI(Map<UUID, Integer> map, UUID id) {
        Integer v = map.get(id);
        return v == null ? 0 : v.intValue();
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) return "benign";
        return reason.trim();
    }

    private static String sanitizeDetail(String detail) {
        if (detail == null || detail.trim().isEmpty()) return "reset";
        return detail.trim();
    }

    private static final class HitRecord {
        private final long time;
        private final double confidence;
        private final boolean selectiveLag;
        private final boolean timingEvidence;
        private final boolean reachEvidence;
        private final boolean spatialEvidence;
        private final boolean severeStale;
        private final long staleExcessMs;

        private HitRecord(long time, double confidence, boolean selectiveLag, boolean timingEvidence,
                          boolean reachEvidence, boolean spatialEvidence, boolean severeStale, long staleExcessMs) {
            this.time = time;
            this.confidence = confidence;
            this.selectiveLag = selectiveLag;
            this.timingEvidence = timingEvidence;
            this.reachEvidence = reachEvidence;
            this.spatialEvidence = spatialEvidence;
            this.severeStale = severeStale;
            this.staleExcessMs = staleExcessMs;
        }
    }
}
