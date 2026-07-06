package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.utils.PrismCheckLabels;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-check VL storage with decay. Each check name has its own VL counter per player.
 */
public final class CheckVLStore {

    private static final class Entry {
        // Written by Netty threads (flag VL adds) and the main thread (decay sweep);
        // volatile guarantees visibility and atomic 64-bit reads under the JMM.
        volatile double vl;
        volatile long lastChangeMs;
        volatile long lastDecayMs;
        volatile boolean changeStamped;
        volatile boolean decayStamped;
    }

    private final Map<UUID, Map<String, Entry>> byPlayer = new ConcurrentHashMap<UUID, Map<String, Entry>>();

    public double getVl(UUID uuid, String checkName) {
        Entry e = entry(uuid, resolvePoolName(checkName));
        if (e == null && !resolvePoolName(checkName).equals(checkName)) {
            e = entry(uuid, checkName);
        }
        return e == null ? 0.0D : e.vl;
    }

    public double addVl(UUID uuid, String checkName, double add, long nowMs) {
        Entry e = entryOrCreate(uuid, resolvePoolName(checkName));
        e.vl = Math.max(0.0D, e.vl + Math.max(0.0D, add));
        e.lastChangeMs = nowMs;
        e.changeStamped = true;
        // A fresh VL change re-opens the grace window: clear any stale decay reference so
        // grace is measured from this change, not from a prior decay tick.
        e.decayStamped = false;
        return e.vl;
    }

    public void resetVl(UUID uuid, String checkName) {
        Entry e = entry(uuid, resolvePoolName(checkName));
        if (e != null) e.vl = 0.0D;
    }

    public void decay(UUID uuid, String checkName, double amount, long nowMs) {
        Entry e = entry(uuid, resolvePoolName(checkName));
        if (e == null || e.vl <= 0.0D) return;
        e.vl = Math.max(0.0D, e.vl - Math.max(0.0D, amount));
        e.lastDecayMs = nowMs;
        e.decayStamped = true;
    }

    /**
     * Time-based scheduled decay for a VL pool. Honors a grace window after the last VL change,
     * decays at {@code ratePerSecond} based on real elapsed time since the last decay tick, and
     * floors the pool at zero (removing the entry when it hits zero to free memory).
     *
     * @return the new VL value for the pool (0.0 if absent / fully decayed)
     */
    public double tickDecay(UUID uuid, String checkName, double ratePerSecond, long graceMs, long nowMs) {
        if (uuid == null || ratePerSecond <= 0.0D) return getVl(uuid, checkName);
        String pool = resolvePoolName(checkName);
        Entry e = entry(uuid, pool);
        if (e == null) return 0.0D;
        if (e.vl <= 0.0D) {
            removeEntry(uuid, pool);
            return 0.0D;
        }
        // Grace: do not decay until graceMs has elapsed since the last VL increase.
        // (lastChangeMs is always stamped when an entry exists, so use it directly — 0 is a
        // valid timestamp and must not be treated as "unset".)
        if (e.changeStamped && nowMs - e.lastChangeMs < graceMs) {
            return e.vl;
        }
        // Reference point for elapsed time: the last decay tick if one has run, else the last
        // VL change. Both are real timestamps once stamped; never substitute nowMs (that would
        // zero out elapsed time and skip the decay).
        long since = e.decayStamped ? e.lastDecayMs : (e.changeStamped ? e.lastChangeMs : nowMs);
        double seconds = Math.max(0.0D, (nowMs - since) / 1000.0D);
        if (seconds <= 0.0D) {
            e.lastDecayMs = nowMs;
            e.decayStamped = true;
            return e.vl;
        }
        e.vl = Math.max(0.0D, e.vl - ratePerSecond * seconds);
        e.lastDecayMs = nowMs;
        e.decayStamped = true;
        if (e.vl <= 0.0D) {
            removeEntry(uuid, pool);
            return 0.0D;
        }
        return e.vl;
    }

    private void removeEntry(UUID uuid, String pool) {
        Map<String, Entry> map = byPlayer.get(uuid);
        if (map == null) return;
        map.remove(pool);
        if (map.isEmpty()) byPlayer.remove(uuid);
    }

    public void clearPlayer(UUID uuid) {
        byPlayer.remove(uuid);
    }

    private Entry entry(UUID uuid, String checkName) {
        Map<String, Entry> map = byPlayer.get(uuid);
        if (map == null) return null;
        return map.get(checkName);
    }

    private Entry entryOrCreate(UUID uuid, String checkName) {
        String pool = resolvePoolName(checkName);
        Map<String, Entry> map = byPlayer.computeIfAbsent(uuid, k -> new ConcurrentHashMap<String, Entry>());
        Entry e = map.get(pool);
        if (e == null) {
            e = new Entry();
            map.put(pool, e);
        }
        return e;
    }

    private static String resolvePoolName(String checkName) {
        return PrismCheckLabels.vlPoolName(checkName);
    }
}
