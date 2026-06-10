package com.colin.vezanticheat.utils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Rolling performance counters for /vez status. Thread-safe; intended for coarse TPS impact monitoring.
 */
public final class PerfSampler {

    private final LongAdder movementSamples = new LongAdder();
    private final LongAdder movementTotalNs = new LongAdder();
    private final LongAdder packetSamples = new LongAdder();
    private final LongAdder packetTotalNs = new LongAdder();
    private final AtomicLong lastResetMs = new AtomicLong(System.currentTimeMillis());

    private volatile boolean enabled;

    public PerfSampler(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void recordMovement(long durationNs) {
        if (!enabled || durationNs < 0L) return;
        movementSamples.increment();
        movementTotalNs.add(durationNs);
    }

    public void recordPacketStage(long durationNs) {
        if (!enabled || durationNs < 0L) return;
        packetSamples.increment();
        packetTotalNs.add(durationNs);
    }

    public Snapshot snapshot() {
        long movN = movementSamples.sum();
        long pktN = packetSamples.sum();
        double movAvgMs = movN == 0L ? 0.0D : (movementTotalNs.sum() / (double) movN) / 1_000_000.0D;
        double pktAvgMs = pktN == 0L ? 0.0D : (packetTotalNs.sum() / (double) pktN) / 1_000_000.0D;
        return new Snapshot(movN, movAvgMs, pktN, pktAvgMs, lastResetMs.get());
    }

    public void resetWindow() {
        movementSamples.reset();
        movementTotalNs.reset();
        packetSamples.reset();
        packetTotalNs.reset();
        lastResetMs.set(System.currentTimeMillis());
    }

    public static final class Snapshot {
        public final long movementSamples;
        public final double movementAvgMs;
        public final long packetSamples;
        public final double packetAvgMs;
        public final long windowStartMs;

        Snapshot(long movementSamples, double movementAvgMs, long packetSamples,
                 double packetAvgMs, long windowStartMs) {
            this.movementSamples = movementSamples;
            this.movementAvgMs = movementAvgMs;
            this.packetSamples = packetSamples;
            this.packetAvgMs = packetAvgMs;
            this.windowStartMs = windowStartMs;
        }
    }
}
