package com.colin.vezanticheat.engine;

/**
 * OffsetAdvantageAccumulator — pure (Bukkit-free) math for the LONG-WINDOW OFFSET ADVANTAGE.
 *
 * <p>Each movement tick contributes its reduced prediction offset (above a small threshold) to a
 * slow-decaying accumulator. Sustained sub-threshold speed (e.g. ratio 1.003-1.006, which never
 * trips an instantaneous offset threshold) steadily grows the accumulator until it crosses a flag
 * level. Crucially the decay is a SLOW per-clean-second bleed (default -25%/clean second) rather
 * than a hard rolling-window reset, so a cheater cannot reset it by inserting clean ticks; it also
 * survives teleports (the engine does not reset it on teleport) and is zeroed only when a setback
 * executes.
 *
 * <p>Extracted from {@link MovementCheckRunner} so the accrual/decay can be unit-tested without a
 * live server.
 */
public final class OffsetAdvantageAccumulator {

    private OffsetAdvantageAccumulator() {}

    /**
     * Advance the accumulator by one tick.
     *
     * @param current          current accumulated advantage
     * @param reducedOffset    this tick's offset after uncertainty reduction
     * @param threshold        per-tick offset floor; only the excess above this accrues
     * @param cleanDecayPerSec fraction of the accumulator bled off per clean SECOND (e.g. 0.25)
     * @param gapMs            observed flying interval for this tick (used to scale clean decay)
     * @param cap              hard cap on the accumulator
     * @return the new accumulated advantage, clamped to [0, cap]
     */
    public static double advance(double current, double reducedOffset, double threshold,
                                 double cleanDecayPerSec, long gapMs, double cap) {
        double gain = reducedOffset - threshold;
        if (gain > 0.0D) {
            current += gain;
        } else {
            // Clean tick: bleed off proportional to elapsed time (capped at one second of decay
            // per tick so a single very-long gap cannot wipe the whole ledger).
            long effectiveGap = gapMs > 0L ? gapMs : (long) PlayerClock.TICK_MS;
            double elapsedSec = Math.max(0.0D, Math.min(1.0D, effectiveGap / 1000.0D));
            current *= (1.0D - (cleanDecayPerSec * elapsedSec));
        }
        if (current < 0.0D) current = 0.0D;
        if (cap > 0.0D && current > cap) current = cap;
        return current;
    }
}
