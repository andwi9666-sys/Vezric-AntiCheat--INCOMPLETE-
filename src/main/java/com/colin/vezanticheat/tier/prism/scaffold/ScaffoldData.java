package com.colin.vezanticheat.tier.prism.scaffold;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Per-player mutable state for the silent scaffold detection system. One instance lives in
 * {@link ScaffoldEngine}'s player map for the duration of the session and is cleared on quit/reload.
 *
 * <p>Holds the rolling placement window, the current bridge streak, the three per-check suspicion
 * buffers (with shared time-based decay), per-check verbose cooldowns, and the Eagle sneak-cycle
 * accumulators. No detection logic lives here — see {@link ScaffoldAnalyzer}.</p>
 */
final class ScaffoldData {

    /** Rolling window of recent placements (newest last). Capacity enforced by the engine. */
    final Deque<ScaffoldPlacement> placements = new ArrayDeque<ScaffoldPlacement>();

    /** Consecutive placements that looked like bridging (resets when the player stops bridging). */
    int bridgeStreak;

    /** Dedup signature of the last ingested placement, so all checks share one analysis per placement. */
    long lastPlacementSig = Long.MIN_VALUE;

    /** Cached per-category suspicion scores from the last ingested placement (each 0..1). */
    double sTiming;
    double sRotation;
    double sRaytrace;
    double sLegality;
    double sSync;
    String lastDebug = "";

    /** Cached verdict from the last fresh analysis, returned to the other checks for the same packet. */
    ScaffoldEngine.ScaffoldResult cachedResult;

    /** Per-check suspicion buffers. */
    double scaffoldBuf;   // PrismScaffoldA  -> Scaffold (legality + raytrace)
    double legitBuf;      // PrismScaffoldC  -> LegitScaffold (consistency)
    double eagleBuf;      // PrismScaffoldB  -> Eagle (sneak automation)
    long lastDecayMs;

    /** Per-check verbose cooldown stamps. */
    long lastScaffoldVerboseMs;
    long lastLegitVerboseMs;
    long lastEagleVerboseMs;

    /** Count of consecutive near-perfect placements (timing+rotation), for the allowance rule. */
    int perfectStreak;

    // ---- Eagle sneak-cycle accumulators ----
    boolean sneaking;
    long sneakStartMs;
    double sneakStartEdgeFrac = Double.NaN;
    long lastPlacementMs;
    final Deque<Double> eagleEdgeFracs = new ArrayDeque<Double>();
    final Deque<Long> eagleStartLeads = new ArrayDeque<Long>();
    final Deque<Long> eagleReleaseDelays = new ArrayDeque<Long>();

    /** Reset the bridge sequence (called when the player clearly stopped bridging). */
    void clearSequence() {
        placements.clear();
        bridgeStreak = 0;
        perfectStreak = 0;
        sTiming = sRotation = sRaytrace = sLegality = sSync = 0.0D;
    }
}
