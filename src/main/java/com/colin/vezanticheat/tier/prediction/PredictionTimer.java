package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.PlayerClock;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

/**
 * Transaction-synced player clock drift and prediction timer debt.
 *
 * <p>Only evaluates position packets — rotation-only flying packets must not inflate timer drift.</p>
 */
public final class PredictionTimer extends TierCheck {

    public PredictionTimer(VezAntiCheat plugin) {
        super(plugin, "PredictionTimer", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
            coolTimerBuffer(data);
            decay(p, 0.20D);
            return;
        }

        long txDrift = PlayerClock.evaluateDrift(plugin, data);
        long flagMs = plugin.getConfig().getLong("engine.player-clock.flag-ahead-ms", 110L);
        long minDriftBuffer = plugin.tierCfg().checkLong(name(), "txMinDriftMs", 100L);
        long blatantInterval = plugin.tierCfg().checkLong(name(), "blatantIntervalMs", 45L);
        int txBufferToFlag = plugin.tierCfg().checkInt(name(), "txBufferToFlag", 8);

        if (txDrift >= minDriftBuffer) {
            long interval = data.getLastFlyingIntervalMs();
            boolean fastPackets = interval > 0L && interval <= blatantInterval;
            boolean highDrift = txDrift >= flagMs + 40L;

            if (fastPackets || highDrift) {
                int gain = (txDrift >= flagMs + 60L && fastPackets) ? 2 : 1;
                int buf = Math.min(12, data.getTimerPredictionBuffer() + gain);
                data.setTimerPredictionBuffer(buf);
                if (buf >= txBufferToFlag) {
                    fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                            "tx-drift=" + txDrift + "ms interval=" + interval + "ms");
                    if (setbackEnabled() && txDrift >= plugin.getConfig().getLong("engine.player-clock.setback-min-drift-ms", 200L)) {
                        PlayerClock.flagIfAhead(plugin, p, data, name());
                    }
                    data.setTimerPredictionBuffer(0);
                }
                return;
            }
        }

        coolTimerBuffer(data);

        boolean engineOnly = plugin.engine() != null && plugin.engine().isEnabled()
                && plugin.getConfig().getBoolean("engine.skip-legacy-movement-prediction", true);
        if (engineOnly || plugin.prediction() == null) {
            decay(p, 0.35D);
            return;
        }

        PredictionResult result = plugin.prediction().getLastResult(data);
        if (result == null || !result.positionIncluded) {
            decay(p, 0.35D);
            return;
        }

        if (result.lagCompensated || result.replayCompensated) {
            decay(p, 0.45D);
            return;
        }

        if (!result.timerViolation) {
            decay(p, 0.35D);
            return;
        }

        int gain = 1;
        if (result.timerDebtMs >= plugin.tierCfg().checkDouble(name(), "gainDebtMs", 180.0D)
                && data.getLastFlyingIntervalMs() <= blatantInterval) {
            gain = 2;
        }

        int buffer = Math.min(10, data.getTimerPredictionBuffer() + gain);
        data.setTimerPredictionBuffer(buffer);
        if (buffer >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 6)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "debt=" + round3(result.timerDebtMs)
                            + " interval=" + data.getLastFlyingIntervalMs()
                            + " " + result.debugSummary);
            if (setbackEnabled()
                    && result.timerDebtMs >= plugin.tierCfg().checkDouble(name(), "setbackMinDebtMs", 200.0D)
                    && data.getLastFlyingIntervalMs() <= plugin.tierCfg().checkLong(name(), "setbackMaxIntervalMs", 38L)) {
                MovementEnforcement.requestBlatantEnforcement(plugin, p, data, name() + " timer-debt");
            }
            data.setTimerPredictionBuffer(0);
        }
    }

    private void coolTimerBuffer(PlayerData data) {
        if (data == null) return;
        data.setTimerPredictionBuffer(Math.max(0, data.getTimerPredictionBuffer() - 1));
    }
}
