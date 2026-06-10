package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

/**
 * PlayerClock — Grim-style transaction-synced timer detection.
 *
 * Each flying packet adds 50ms to the player's clock balance. On transaction ack the balance
 * syncs to wall-clock nano time. If balance exceeds real time by more than the flag threshold
 * (~50ms = 1.01 timer), the client is running faster than the server allows.
 */
public final class PlayerClock {

    public static final long TICK_MS = 50L;

    private PlayerClock() {}

    public static void onFlyingPacket(VezAntiCheat plugin, Player player, PlayerData data, boolean positionIncluded) {
        if (plugin == null || player == null || data == null) return;
        if (!plugin.getConfig().getBoolean("engine.player-clock.enabled", true)) return;
        if (PlayerData.bypass(player)) return;
        if (!positionIncluded) return;

        PlayerClockState state = data.getPlayerClockState();
        state.balanceMs += TICK_MS;
        state.lastFlyingMs = System.currentTimeMillis();
    }

    public static void onTransactionAck(VezAntiCheat plugin, Player player, PlayerData data, long nowMs) {
        if (plugin == null || player == null || data == null) return;
        if (!plugin.getConfig().getBoolean("engine.player-clock.enabled", true)) return;

        PlayerClockState state = data.getPlayerClockState();
        long nano = System.nanoTime();
        if (state.nanoAnchor <= 0L) {
            state.nanoAnchor = nano;
            state.balanceMs = 0L;
            return;
        }

        long elapsedMs = (nano - state.nanoAnchor) / 1_000_000L;
        long drift = state.balanceMs - elapsedMs;

        long debitCap = plugin.getConfig().getLong("engine.player-clock.ledger-debit-cap-ms", 20L);
        long ledgerCap = plugin.getConfig().getLong("engine.player-clock.ledger-cap-ms", 2000L);
        applyDriftToLedger(state, drift, debitCap, ledgerCap);

        state.balanceMs = elapsedMs;
        state.nanoAnchor = nano;
        state.lastSyncMs = nowMs;
    }

    /**
     * PERSISTENT DRIFT LEDGER (pure math, unit-testable). A cheater that runs slightly fast and then
     * "rests" to bleed off the per-window balance cannot reset this. Positive instantaneous drift
     * (client ahead of server) accrues into the ledger; behind-ticks only debit a bounded amount so
     * brief jitter cancels but sustained timer keeps climbing. The first ack after a teleport pauses
     * accrual once (no debit, no credit) then resumes — teleport corrections must not accrue.
     *
     * @return the new cumulative drift ledger value (clamped to [0, ledgerCap])
     */
    public static long applyDriftToLedger(PlayerClockState state, long drift, long debitCap, long ledgerCap) {
        if (state.teleportPause) {
            state.teleportPause = false;
            return state.cumulativeDriftMs;
        }
        if (drift > 0L) {
            state.cumulativeDriftMs += drift;
        } else if (drift < 0L) {
            state.cumulativeDriftMs += Math.max(-Math.abs(debitCap), drift);
        }
        if (state.cumulativeDriftMs < 0L) state.cumulativeDriftMs = 0L;
        if (ledgerCap > 0L && state.cumulativeDriftMs > ledgerCap) state.cumulativeDriftMs = ledgerCap;
        return state.cumulativeDriftMs;
    }

    /**
     * Debit the persistent ledger for a movement gap (e.g. a {@code >225ms} flying interval). Unlike
     * the per-window balance this is NEVER hard-reset by a gap — it only debits a bounded amount so a
     * cheater cannot farm timer advantage and then deliberately stall to wipe the ledger.
     */
    public static void onMovementGap(VezAntiCheat plugin, PlayerData data, long gapMs) {
        if (plugin == null || data == null) return;
        if (!plugin.getConfig().getBoolean("engine.player-clock.enabled", true)) return;
        long triggerMs = plugin.getConfig().getLong("engine.player-clock.gap-debit-trigger-ms", 225L);
        long gapDebit = plugin.getConfig().getLong("engine.player-clock.gap-debit-ms", 50L);
        applyGapDebit(data.getPlayerClockState(), gapMs, triggerMs, gapDebit);
    }

    /**
     * Debit the ledger for a movement gap (pure, unit-testable). A gap {@code >= triggerMs} debits a
     * bounded {@code gapDebit} ms — it is NEVER a full reset, so a cheater cannot farm timer
     * advantage and then deliberately stall to wipe the ledger.
     *
     * @return the new cumulative drift ledger value
     */
    public static long applyGapDebit(PlayerClockState state, long gapMs, long triggerMs, long gapDebit) {
        if (state == null) return 0L;
        if (gapMs < triggerMs) return state.cumulativeDriftMs;
        state.cumulativeDriftMs = Math.max(0L, state.cumulativeDriftMs - gapDebit);
        return state.cumulativeDriftMs;
    }

    /** Pause ledger accrual across the next ack (called on teleport so corrections don't accrue). */
    public static void onTeleport(VezAntiCheat plugin, PlayerData data) {
        if (data == null) return;
        PlayerClockState state = data.getPlayerClockState();
        state.teleportPause = true;
        // Re-anchor the per-window balance; the persistent ledger is intentionally NOT reset.
        state.balanceMs = 0L;
        state.nanoAnchor = System.nanoTime();
    }

    /**
     * @return positive drift in ms when client is ahead of server clock, or 0 if ok
     */
    public static long evaluateDrift(VezAntiCheat plugin, PlayerData data) {
        if (plugin == null || data == null) return 0L;
        if (!plugin.getConfig().getBoolean("engine.player-clock.enabled", true)) return 0L;

        PlayerClockState state = data.getPlayerClockState();
        if (state.nanoAnchor <= 0L) return 0L;

        long elapsedMs = (System.nanoTime() - state.nanoAnchor) / 1_000_000L;
        long ahead = state.balanceMs - elapsedMs;
        long flagMs = plugin.getConfig().getLong("engine.player-clock.flag-ahead-ms", 100L);
        return ahead > flagMs ? ahead : 0L;
    }

    public static boolean flagIfAhead(VezAntiCheat plugin, Player player, PlayerData data, String source) {
        long drift = evaluateDrift(plugin, data);
        if (drift <= 0L) return false;

        long flagMs = plugin.getConfig().getLong("engine.player-clock.flag-ahead-ms", 100L);
        if (drift < flagMs) return false;

        long cancelMs = plugin.getConfig().getLong("engine.player-clock.cancel-packet-min-drift-ms", 150L);
        if (plugin.getConfig().getBoolean("engine.player-clock.cancel-packet", true) && drift >= cancelMs) {
            MovementEnforcement.blockCurrentMovementPacket(plugin, data, source + " drift=" + drift + "ms");
        }
        long setbackMs = plugin.getConfig().getLong("engine.player-clock.setback-min-drift-ms", 200L);
        if (plugin.getConfig().getBoolean("engine.player-clock.setback", true) && drift >= setbackMs) {
            MovementEnforcement.executeSetback(plugin, player, data, source + " timer drift=" + drift + "ms");
        }
        data.getPlayerClockState().balanceMs = 0L;
        data.getPlayerClockState().nanoAnchor = System.nanoTime();
        return true;
    }

    public static final class PlayerClockState {
        public long balanceMs;
        public long nanoAnchor;
        public long lastFlyingMs;
        public long lastSyncMs;
        /** Persistent cumulative drift ledger (ms). NOT reset by teleports or movement gaps. */
        public long cumulativeDriftMs;
        /** When true, the next ack pauses ledger accrual (set after a teleport). */
        public boolean teleportPause;

        public void reset() {
            balanceMs = 0L;
            nanoAnchor = 0L;
            lastFlyingMs = 0L;
            lastSyncMs = 0L;
            cumulativeDriftMs = 0L;
            teleportPause = false;
        }
    }
}
