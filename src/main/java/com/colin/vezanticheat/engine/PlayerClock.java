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
        long maxDrift = plugin.getConfig().getLong("engine.player-clock.max-drift-ms", 120L);
        if (drift > maxDrift) {
            state.balanceMs = elapsedMs;
        } else {
            state.balanceMs = elapsedMs;
        }
        state.nanoAnchor = nano;
        state.lastSyncMs = nowMs;
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

        public void reset() {
            balanceMs = 0L;
            nanoAnchor = 0L;
            lastFlyingMs = 0L;
            lastSyncMs = 0L;
        }
    }
}
