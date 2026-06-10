package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.entity.Player;

/**
 * NoSlow: moving faster than allowed while blocking, eating, or drawing a bow.
 * Includes Grim fake-RELEASE_USE_ITEM bypass detection via {@link UseItemTracker#isCompensatedItemUse}.
 */
public final class PredictionNoSlow extends AbstractMovementTierCheck {

    public PredictionNoSlow(VezAntiCheat plugin) {
        super(plugin, "PredictionNoSlow", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        if (engineActive()) {
            if (data.getLastMoveMillis() <= 0L || nowMs - data.getLastMoveMillis() > 5L) {
                cool(p, 0.2D);
                return;
            }

            boolean compensatedUse = UseItemTracker.isCompensatedItemUse(p, data, nowMs);
            boolean fakeRelease = UseItemTracker.isFakeReleaseUse(p, data, nowMs);
            boolean usingItem = UseItemTracker.isUsingItem(p, data) || fakeRelease;
            EngineResult er = engineResult(data);
            if (er == null || !usingItem) {
                cool(p, 0.35D);
                return;
            }

            if (!compensatedUse
                    && EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, er, nowMs, name())) {
                cool(p, 0.35D);
                return;
            }

            double thr = cfgDouble("engineHorizontalOffset", 0.022D);
            double excessThr = cfgDouble("engineNoSlowExcess", 0.012D);

            boolean suspicious = er.horizontalOffset > thr
                    || er.noSlowExcess > excessThr
                    || (fakeRelease && er.horizontalOffset > cfgDouble("engineFakeReleaseOffset", 0.015D));

            if (!suspicious) {
                cool(p, 0.35D);
                return;
            }

            int gain = (fakeRelease || er.noSlowExcess > excessThr * 2.5D || er.horizontalOffset > thr * 2.0D) ? 2 : 1;
            flagBuffered(p, data, gain,
                    "useItem hOff=" + r(er.horizontalOffset) + " excess=" + r(er.noSlowExcess)
                            + " fakeRel=" + fakeRelease + " streak=" + data.getReleaseUseItemStreak()
                            + " " + er.debug);
            return;
        }

        PredictionResult result = predictionResult(data);
        if (result == null || !result.usingItem || result.inLiquid || result.weirdSurface) {
            cool(p, 0.35D);
            return;
        }

        double maxWhileUsing = plugin.getConfig().getDouble("prediction.no-slow.max-horizontal", 0.21D);
        if (result.horizontalDistance <= maxWhileUsing) {
            cool(p, 0.35D);
            return;
        }

        flagBuffered(p, data, 1,
                "using dist=" + r(result.horizontalDistance) + " limit=" + r(maxWhileUsing)
                        + " " + result.debugSummary);
    }
}
