package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.GroundSpoofTracker;
import com.colin.vezanticheat.utils.NoFallUtil;
import org.bukkit.entity.Player;

/**
 * Ground spoof while descending: client onGround while Y coordinate still decreasing.
 */
public final class PredictionGroundSpoofDescent extends AbstractMovementTierCheck {

    public PredictionGroundSpoofDescent(VezAntiCheat plugin) {
        super(plugin, "PredictionGroundSpoofDescent", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult er = engineResult(data);
        if (er == null) {
            cool(p, 0.3D);
            return;
        }

        if (GroundSpoofTracker.shouldSkipGroundSpoofCheck(p, data, er, nowMs, plugin)) {
            cool(p, 0.3D);
            return;
        }

        if (NoFallUtil.shouldSkipNoFallExpectations(plugin, p)) {
            cool(p, 0.35D);
            return;
        }

        if (EngineMovementGrace.shouldExemptGroundSpoofFlag(plugin, data, er, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        GroundSpoofTracker.DescentConfig cfg = GroundSpoofTracker.descentConfig(plugin, name());
        if (!GroundSpoofTracker.isSustainedGroundDescent(data, cfg)) {
            cool(p, 0.3D);
            return;
        }

        double dy = er.actual == null ? 0.0D : er.actual.getY();
        NoFallUtil.Context ctx = NoFallUtil.analyze(plugin, p, data);
        boolean airBelow = GroundSpoofTracker.isAirBelowGroundClaim(ctx);
        int gain = data.getGroundSpoofAirTicks() >= 5 ? 2 : 1;

        flagBuffered(p, data, gain,
                "ground-descent streak=" + data.getGroundSpoofAirTicks()
                        + " dy=" + r(dy) + " cum=" + r(data.getGroundDescentCumulativeDy())
                        + " airBelow=" + (airBelow ? 1 : 0));
    }
}
