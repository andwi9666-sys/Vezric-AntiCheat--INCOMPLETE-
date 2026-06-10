package com.colin.vezanticheat.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Small in-memory tracker for recent detection events so shadow/live behavior can be
 * inspected in-game without scraping console output.
 */
public final class DiagnosticsTracker {

    private final Map<UUID, Deque<Entry>> entriesByPlayer = new ConcurrentHashMap<UUID, Deque<Entry>>();
    private final int maxEntriesPerPlayer;

    public DiagnosticsTracker(int maxEntriesPerPlayer) {
        this.maxEntriesPerPlayer = Math.max(4, maxEntriesPerPlayer);
    }

    public void record(UUID uuid, String check, String stage, String detail) {
        if (uuid == null) return;
        Deque<Entry> entries = entriesByPlayer.get(uuid);
        if (entries == null) {
            entries = new ArrayDeque<Entry>();
            entriesByPlayer.put(uuid, entries);
        }

        entries.addLast(new Entry(System.currentTimeMillis(), sanitize(check), sanitize(stage), sanitize(detail)));
        while (entries.size() > maxEntriesPerPlayer) {
            entries.removeFirst();
        }
    }

    public List<Entry> recent(UUID uuid, int maxEntries) {
        List<Entry> out = new ArrayList<Entry>();
        if (uuid == null || maxEntries <= 0) return out;
        Deque<Entry> entries = entriesByPlayer.get(uuid);
        if (entries == null || entries.isEmpty()) return out;

        int remaining = maxEntries;
        java.util.Iterator<Entry> iterator = entries.descendingIterator();
        while (iterator.hasNext() && remaining-- > 0) {
            out.add(iterator.next());
        }
        return out;
    }

    private String sanitize(String value) {
        if (value == null) return "";
        String trimmed = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (trimmed.length() <= 220) return trimmed;
        return trimmed.substring(0, 217) + "...";
    }

    public static final class Entry {
        private final long timeMs;
        private final String check;
        private final String stage;
        private final String detail;

        private Entry(long timeMs, String check, String stage, String detail) {
            this.timeMs = timeMs;
            this.check = check;
            this.stage = stage;
            this.detail = detail;
        }

        public long getTimeMs() { return timeMs; }
        public String getCheck() { return check; }
        public String getStage() { return stage; }
        public String getDetail() { return detail; }
    }
}
