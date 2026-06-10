package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Blink / timer-flush: large flying gaps with flush movement or timer debt corroboration.
 *
 * <p>Combines packet interval gap, movement distance after gap, burst packet count, engine offset,
 * and legacy timer debt. Blatant gap+move triggers immediate enforcement.</p>
 */
public final class PredictionBlink extends AbstractMovementTierCheck {

    public PredictionBlink(VezAntiCheat plugin) {
        super(plugin, "PredictionBlink", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null) {
            cool(p, 0.4D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.4D);
            return;
        }

        double offsetThreshold = cfgDouble("engineOffsetThreshold", 0.18D);
        long blatantGapMs = cfgLong("blatantGapMs", 120L);
        long interval = data.getLastFlyingIntervalMs();
        boolean largeGap = interval >= blatantGapMs;

        PredictionResult legacy = predictionResult(data);
        double timerDebt = legacy == null ? result.timerDebtMs : legacy.timerDebtMs;
        double debtThreshold = cfgDouble("timerDebtMs", 140.0D);

        Vector actual = result.actual;
        double moveDist = actual == null ? 0.0D : actual.length();
        double blatantMove = cfgDouble("blatantFlushMove", 2.0D);
        int burstPackets = data.getPositionPacketsThisTick();
        int blatantBurst = cfgInt("blatantBurstPackets", 3);

        if (largeGap && (moveDist >= blatantMove || burstPackets >= blatantBurst)) {
            requestBlatant(p, data,
                    "blink-flush gap=" + interval + "ms move=" + r(moveDist) + " burst=" + burstPackets);
            fail(p, data, cfgDouble("failVl", 1.2D),
                    "blink-flush gap=" + interval + "ms move=" + r(moveDist) + " burst=" + burstPackets);
            resetBuffer(p);
            data.resetPositionPacketsThisTick();
            return;
        }

        boolean suspiciousOffset = result.offset >= offsetThreshold;
        boolean suspiciousDebt = timerDebt >= debtThreshold;
        if (!suspiciousOffset && !largeGap && !suspiciousDebt) {
            cool(p, 0.35D);
            return;
        }

        int gain = 1;
        if (suspiciousOffset && (largeGap || suspiciousDebt)) gain = 2;
        if (largeGap && interval >= blatantGapMs * 2L) gain = 3;

        boolean setback = largeGap || (suspiciousOffset && suspiciousDebt);
        if (flagBuffered(p, data, gain,
                "off=" + r(result.offset) + " gap=" + interval + "ms debt=" + r(timerDebt)
                        + " move=" + r(moveDist) + " " + result.debug)
                && setback) {
            predictionSetback(p, data, "simulation");
        }
    }
}
