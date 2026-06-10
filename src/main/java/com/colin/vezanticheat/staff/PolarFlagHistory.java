package com.colin.vezanticheat.staff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ring buffer of recent Polar-style flags for the staff GUI. */
public final class PolarFlagHistory {

    private static final int MAX_ENTRIES = 54;
    private final CopyOnWriteArrayList<PolarFlagRecord> entries = new CopyOnWriteArrayList<PolarFlagRecord>();

    public void record(PolarFlagRecord record) {
        if (record == null) return;
        entries.add(0, record);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public List<PolarFlagRecord> recent() {
        return Collections.unmodifiableList(new ArrayList<PolarFlagRecord>(entries));
    }

    public int size() {
        return entries.size();
    }
}
