package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.engine.PlayerClock;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/**
 * PredictionNegativeTimer — sustained SLOW timer (client withholding movement ticks / lag-switch).
 *
 * <p>GrimAC {@code NegativeTimer} analogue. The positive timer ({@link PredictionTimer} +
 * {@link PlayerClock} drift ledger) catches a client running AHEAD of real time; this catches a
 * client running consistently BEHIND while actively moving — position packets arriving at a
 * sub-vanilla rate (~50ms/tick). Accrual is delegated to
 * {@link PlayerClock#accrueNegativeInterval} and is gated to movement-bearing ticks with a checked
 * engine prediction, so a stationary player (who legitimately sends flying-only packets) never
 * accrues a false slow timer. High ping is lag-gated, and each tick's contribution is capped so a
 * single lag spike cannot flag — only a sustained slow rate climbs to the threshold.</p>
 */
public final class PredictionNegativeTimer extends TierCheck {

    public PredictionNegativeTimer(VezAntiCheat plugin) {
        super(plugin, "PredictionNegativeTimer", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!plugin.getConfig().getBoolean("engine.player-clock.enabled", true)) {
            decay(p, 0.20D);
            return;
        }

        // Only evaluate on a fresh position move with a checked engine prediction. A stationary player
        // sends flying-only packets at a sub-rate that would otherwise look like a slow timer.
        if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
            coolBuffer(p, 1);
            decay(p, 0.20D);
            return;
        }
        EngineResult er = data.getLastEngineResult();
        if (er == null || !er.checked) {
            coolBuffer(p, 1);
            decay(p, 0.20D);
            return;
        }

        long interval = data.getLastFlyingIntervalMs();
        if (interval <= 0L) {
            decay(p, 0.20D);
            return;
        }

        long perTickTolerance = plugin.tierCfg().checkLong(name(), "perTickToleranceMs", 8L);
        long perTickCap = plugin.tierCfg().checkLong(name(), "perTickCapMs", 60L);
        long bleed = plugin.tierCfg().checkLong(name(), "bleedPerCleanTickMs", 40L);
        long ledgerCap = plugin.tierCfg().checkLong(name(), "ledgerCapMs", 4000L);
        long minBehind = plugin.tierCfg().checkLong(name(), "minBehindMs", 1000L);

        long ledger = PlayerClock.accrueNegativeInterval(
                data.getPlayerClockState(), interval, perTickTolerance, perTickCap, bleed, ledgerCap);

        if (ledger < minBehind) {
            coolBuffer(p, 1);
            return;
        }

        int gain = ledger >= minBehind * 2 ? 2 : 1;
        if (incrementBuffer(p, gain)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "slow-timer ledger=" + ledger + "ms interval=" + interval + "ms");
            resetBuffer(p);
            PlayerClock.resetNegative(data);
        }
    }
}
