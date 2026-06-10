package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FallArcTracker;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import com.colin.vezanticheat.utils.PotionUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Primary Grim offset handler for the PREDICTION tier.
 */
public final class PredictionOffset extends AbstractMovementTierCheck {

    public PredictionOffset(VezAntiCheat plugin) {
        super(plugin, "PredictionOffset", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (data.isEatMovementGrace() || data.isActivelyEating()) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.75D));
            data.clearPendingSetback();
            cool(p, 0.4D);
            return;
        }
        if (!engineActive()) {
            decay(p, 0.4D);
            return;
        }

        // Timer/rotation packets must not consume stale engine results or block movement.
        if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
            cool(p, 0.2D);
            return;
        }

        EngineResult result = engineResult(data);
        if (result == null) {
            cool(p, 0.4D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());
        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.75D));
            cool(p, 0.4D);
            return;
        }
        if (EngineMovementGrace.isLikelyLegitJumpArc(plugin, p, data, result, nowMs)) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.85D));
            cool(p, 0.4D);
            return;
        }
        if (com.colin.vezanticheat.utils.FallArcTracker.isLikelyLegitFallArc(plugin, data, result, nowMs)) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.80D));
            data.clearPendingSetback();
            cool(p, 0.4D);
            return;
        }
        if (com.colin.vezanticheat.utils.NoFallUtil.shouldExemptFallDamagePrediction(plugin, p, data, result, nowMs)) {
            data.setEngineOffsetAdvantage(Math.max(0.0D, data.getEngineOffsetAdvantage() * 0.85D));
            data.clearPendingSetback();
            cool(p, 0.4D);
            return;
        }
        if (EngineMovementGrace.isLegitSlabOrStairMotion(plugin, data, dy, distH, result.offset, name())) {
            cool(p, 0.4D);
            return;
        }
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.4D);
            return;
        }

        double speedBuffer = PotionUtil.hasSpeedBoost(p)
                ? PotionUtil.combinedSpeedOffsetAllowance(p) * 0.35D : 0.0D;
        double threshold = cfgDouble("offsetThreshold", 0.10D) + speedBuffer;
        double moderateOffset = cfgDouble("moderateOffset", 0.14D) + speedBuffer;
        double setbackThreshold = cfgDouble("setbackThreshold", 0.18D) + speedBuffer;
        double blatantOffset = cfgDouble("engineBlatantOffset", 0.18D);
        double advantageSetback = cfgDouble("advantageSetbackThreshold", 1.0D);
        double advantage = data.getEngineOffsetAdvantage();

        if (result.offset >= blatantOffset) {
            requestBlatant(p, data, "blatant off=" + r(result.offset) + " " + result.debug);
            fail(p, data, cfgDouble("failVl", 1.0D) * 0.5D,
                    "blatant off=" + r(result.offset) + " thr=" + r(blatantOffset) + " " + result.debug);
            data.setEngineOffsetAdvantage(0.0D);
            resetBuffer(p);
            return;
        }

        if (advantage >= advantageSetback) {
            requestBlatant(p, data, "advantage=" + r(advantage) + " " + result.debug);
            fail(p, data, cfgDouble("failVl", 1.0D),
                    "advantage=" + r(advantage) + " thr=" + r(advantageSetback) + " " + result.debug);
            data.setEngineOffsetAdvantage(0.0D);
            resetBuffer(p);
            return;
        }

        if (result.offset >= moderateOffset && result.offset < blatantOffset
                && !result.couldSkipTick
                && !FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, nowMs)
                && !FallArcTracker.isLikelyLegitFallArc(plugin, data, result, nowMs)
                && !com.colin.vezanticheat.utils.NoFallUtil.shouldExemptFallDamagePrediction(plugin, p, data, result, nowMs)) {
            blockMovementPacket(data, "moderate off=" + r(result.offset) + " " + result.debug);
        }

        if (result.offset <= threshold) {
            cool(p, 0.35D);
            return;
        }

        int gain = result.offset > setbackThreshold ? 3 : (result.offset > threshold * 2.0D ? 2 : 1);
        boolean setback = result.offset > setbackThreshold || advantage >= advantageSetback * 0.75D;
        String debug = "off=" + r(result.offset) + " adv=" + r(advantage) + " " + result.debug;

        if (flagBuffered(p, data, gain, debug) && setback) {
            predictionSetback(p, data, "simulation");
        }
    }
}
