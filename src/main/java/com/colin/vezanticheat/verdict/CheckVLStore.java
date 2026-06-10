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
        double vl;
        long lastChangeMs;
        long lastDecayMs;
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
