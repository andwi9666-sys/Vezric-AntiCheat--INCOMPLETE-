package com.colin.vezanticheat.engine;

import org.bukkit.Location;

/**
 * CombatResult — decomposed outcome of one attack's lag-compensated reach analysis, published on
 * PlayerData each attack for the combat sub-checks to read.
 *
 * {@code rewoundDistance} is the smallest eye->hitbox distance across the transaction-bracketed
 * window (the most lenient distance a legitimate client could have perceived) and is what reach
 * checks compare against their limit. {@code maxDistance} is the largest distance in the window,
 * used by BackTrack to detect hits landed on a stale, far-away position. When {@code tracked} is
 * false the engine had no packet-synced position and the values came from the legacy
 * {@code CombatUtil.analyzeReach} fallback.
 */
public final class CombatResult {

    public final long timeMs;
    public final boolean valid;
    public final boolean tracked;
    public final double rewoundDistance;
    public final double currentDistance;
    public final double minDistance;
    public final double maxDistance;
    public final double displacement;
    public final double width;
    public final double height;
    public final long pingMs;
    public final long bracketAgeMs;
    public final int snapshotsConsidered;
    public final Location chosenLocation;
    public final String debug;

    private CombatResult(Builder b) {
        this.timeMs = b.timeMs;
        this.valid = b.valid;
        this.tracked = b.tracked;
        this.rewoundDistance = b.rewoundDistance;
        this.currentDistance = b.currentDistance;
        this.minDistance = b.minDistance;
        this.maxDistance = b.maxDistance;
        this.displacement = b.displacement;
        this.width = b.width;
        this.height = b.height;
        this.pingMs = b.pingMs;
        this.bracketAgeMs = b.bracketAgeMs;
        this.snapshotsConsidered = b.snapshotsConsidered;
        this.chosenLocation = b.chosenLocation;
        this.debug = b.debug;
    }

    /** Whether this result carries usable reach data. False means the producer could not compute. */
    public boolean isValid() {
        return valid;
    }

    /** An invalid result with no usable data; consumers must check {@link #isValid()} before reading. */
    public static CombatResult invalid(long timeMs, String debug) {
        return builder().timeMs(timeMs).valid(false).tracked(false)
                .debug(debug == null ? "invalid" : debug).build();
    }

    public Location getChosenLocation() {
        return chosenLocation == null ? null : chosenLocation.clone();
    }

    /** Alias for reach checks (rewound eye-to-hitbox distance). */
    public double reachDistance() { return rewoundDistance; }

    /** Alias for backtrack/lag-range checks. */
    public long snapshotAgeMs() { return bracketAgeMs; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long timeMs;
        private boolean valid = true;
        private boolean tracked;
        private double rewoundDistance;
        private double currentDistance;
        private double minDistance;
        private double maxDistance;
        private double displacement;
        private double width = 0.6D;
        private double height = 1.8D;
        private long pingMs;
        private long bracketAgeMs;
        private int snapshotsConsidered;
        private Location chosenLocation;
        private String debug = "";

        public Builder timeMs(long v) { this.timeMs = v; return this; }
        public Builder valid(boolean v) { this.valid = v; return this; }
        public Builder tracked(boolean v) { this.tracked = v; return this; }
        public Builder rewoundDistance(double v) { this.rewoundDistance = v; return this; }
        public Builder currentDistance(double v) { this.currentDistance = v; return this; }
        public Builder minDistance(double v) { this.minDistance = v; return this; }
        public Builder maxDistance(double v) { this.maxDistance = v; return this; }
        public Builder displacement(double v) { this.displacement = v; return this; }
        public Builder width(double v) { this.width = v; return this; }
        public Builder height(double v) { this.height = v; return this; }
        public Builder pingMs(long v) { this.pingMs = v; return this; }
        public Builder bracketAgeMs(long v) { this.bracketAgeMs = v; return this; }
        public Builder snapshotsConsidered(int v) { this.snapshotsConsidered = v; return this; }
        public Builder chosenLocation(Location v) { this.chosenLocation = v == null ? null : v.clone(); return this; }
        public Builder debug(String v) { this.debug = v; return this; }

        public CombatResult build() {
            return new CombatResult(this);
        }
    }
}
