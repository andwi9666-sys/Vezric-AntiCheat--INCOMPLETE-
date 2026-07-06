package com.colin.vezanticheat.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-check flag statistics over the last hour, feeding /perplexion
 * recommendations and /perplexion perf. The 54-entry PolarFlagHistory ring is too
 * small for trend analysis, so this tracker aggregates instead of storing records.
 *
 * Layout: 6 ten-minute buckets per check, reused circularly — eviction is O(1)
 * (a stale bucket is reset on first write), memory is bounded by check count
 * (~100) x 6 buckets x ≤64 tracked players per bucket.
 *
 * record() is called from Netty threads (TierCheck.fail) — buckets synchronize
 * internally; summarize() runs on the main thread for commands.
 */
public final class FlagStatsTracker {

    private static final int BUCKET_COUNT = 6;
    private static final long BUCKET_MS = 10L * 60L * 1000L;
    private static final long WINDOW_MS = BUCKET_COUNT * BUCKET_MS;
    private static final int MAX_PLAYERS_PER_BUCKET = 64;

    private final Map<String, Bucket[]> byCheck = new ConcurrentHashMap<String, Bucket[]>();

    public void record(String check, UUID player, int ping, double tps, boolean shadow) {
        if (check == null || check.isEmpty()) return;
        long now = System.currentTimeMillis();
        Bucket[] buckets = byCheck.get(check);
        if (buckets == null) {
            Bucket[] fresh = newBuckets();
            Bucket[] raced = ((ConcurrentHashMap<String, Bucket[]>) byCheck).putIfAbsent(check, fresh);
            buckets = raced != null ? raced : fresh;
        }
        long alignedStart = (now / BUCKET_MS) * BUCKET_MS;
        Bucket bucket = buckets[(int) ((now / BUCKET_MS) % BUCKET_COUNT)];
        synchronized (bucket) {
            if (bucket.alignedStart != alignedStart) {
                bucket.reset(alignedStart);
            }
            bucket.flags++;
            if (shadow) bucket.shadowFlags++;
            if (ping > 0) {
                bucket.pingSum += ping;
                bucket.pingCount++;
            }
            if (tps > 0.0D) {
                bucket.tpsSum += tps;
                bucket.tpsCount++;
            }
            if (player != null
                    && (bucket.playerFlags.containsKey(player) || bucket.playerFlags.size() < MAX_PLAYERS_PER_BUCKET)) {
                Integer prev = bucket.playerFlags.get(player);
                bucket.playerFlags.put(player, prev == null ? 1 : prev.intValue() + 1);
            }
        }
    }

    /** Per-check aggregates over the last hour, most-flagged first. */
    public List<CheckSummary> summarize() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        List<CheckSummary> out = new ArrayList<CheckSummary>();
        for (Map.Entry<String, Bucket[]> entry : byCheck.entrySet()) {
            int flags = 0;
            int shadowFlags = 0;
            long pingSum = 0;
            int pingCount = 0;
            double tpsSum = 0.0D;
            int tpsCount = 0;
            Map<UUID, Integer> playerFlags = new HashMap<UUID, Integer>();
            for (Bucket bucket : entry.getValue()) {
                synchronized (bucket) {
                    if (bucket.alignedStart < cutoff) continue;
                    flags += bucket.flags;
                    shadowFlags += bucket.shadowFlags;
                    pingSum += bucket.pingSum;
                    pingCount += bucket.pingCount;
                    tpsSum += bucket.tpsSum;
                    tpsCount += bucket.tpsCount;
                    for (Map.Entry<UUID, Integer> pf : bucket.playerFlags.entrySet()) {
                        Integer prev = playerFlags.get(pf.getKey());
                        playerFlags.put(pf.getKey(),
                                prev == null ? pf.getValue() : prev.intValue() + pf.getValue().intValue());
                    }
                }
            }
            if (flags == 0) continue;
            UUID topPlayer = null;
            int topPlayerFlags = 0;
            for (Map.Entry<UUID, Integer> pf : playerFlags.entrySet()) {
                if (pf.getValue().intValue() > topPlayerFlags) {
                    topPlayerFlags = pf.getValue().intValue();
                    topPlayer = pf.getKey();
                }
            }
            out.add(new CheckSummary(entry.getKey(), flags, shadowFlags, playerFlags.size(),
                    pingCount > 0 ? (double) pingSum / pingCount : -1.0D,
                    tpsCount > 0 ? tpsSum / tpsCount : -1.0D,
                    topPlayer, topPlayerFlags));
        }
        Collections.sort(out, new Comparator<CheckSummary>() {
            @Override
            public int compare(CheckSummary a, CheckSummary b) {
                return Integer.compare(b.flags, a.flags);
            }
        });
        return out;
    }

    public int totalFlagsLastHour() {
        int total = 0;
        for (CheckSummary summary : summarize()) total += summary.flags;
        return total;
    }

    public Set<UUID> playersFlaggedLastHour() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        Set<UUID> players = new HashSet<UUID>();
        for (Bucket[] buckets : byCheck.values()) {
            for (Bucket bucket : buckets) {
                synchronized (bucket) {
                    if (bucket.alignedStart < cutoff) continue;
                    players.addAll(bucket.playerFlags.keySet());
                }
            }
        }
        return players;
    }

    public void clear() {
        byCheck.clear();
    }

    private static Bucket[] newBuckets() {
        Bucket[] buckets = new Bucket[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) buckets[i] = new Bucket();
        return buckets;
    }

    private static final class Bucket {
        long alignedStart = -1L;
        int flags;
        int shadowFlags;
        long pingSum;
        int pingCount;
        double tpsSum;
        int tpsCount;
        final Map<UUID, Integer> playerFlags = new HashMap<UUID, Integer>();

        void reset(long newStart) {
            alignedStart = newStart;
            flags = 0;
            shadowFlags = 0;
            pingSum = 0;
            pingCount = 0;
            tpsSum = 0.0D;
            tpsCount = 0;
            playerFlags.clear();
        }
    }

    public static final class CheckSummary {
        public final String check;
        public final int flags;
        public final int shadowFlags;
        public final int distinctPlayers;
        /** Average ping of flagged players, or -1 when unknown. */
        public final double avgPing;
        /** Average TPS at flag time, or -1 when unknown. */
        public final double avgTps;
        public final UUID topPlayer;
        public final int topPlayerFlags;

        CheckSummary(String check, int flags, int shadowFlags, int distinctPlayers,
                     double avgPing, double avgTps, UUID topPlayer, int topPlayerFlags) {
            this.check = check;
            this.flags = flags;
            this.shadowFlags = shadowFlags;
            this.distinctPlayers = distinctPlayers;
            this.avgPing = avgPing;
            this.avgTps = avgTps;
            this.topPlayer = topPlayer;
            this.topPlayerFlags = topPlayerFlags;
        }
    }
}
