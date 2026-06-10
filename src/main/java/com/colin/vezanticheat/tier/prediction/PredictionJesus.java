package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.LiquidLocomotionUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Jesus / liquid walk / fast ladder: movement faster than water or climbable prediction allows.
 */
public final class PredictionJesus extends AbstractMovementTierCheck {

    public PredictionJesus(VezAntiCheat plugin) {
        super(plugin, "PredictionJesus", CheckTier.PREDICTION);
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
        if (!LiquidLocomotionUtil.isLiquidLocomotionContext(p, data, result)) {
            cool(p, 0.35D);
            return;
        }
        if (EngineMovementGrace.shouldExemptLiquidLocomotionFlag(plugin, data, result, nowMs)) {
            cool(p, 0.4D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        double hOff = result.horizontalOffset;
        double vOff = result.verticalOffset;
        boolean surfaceJesus = LiquidLocomotionUtil.isWalkingOnWaterSurface(p, data, result);

        double legitMaxDy = cfgDouble("ladderLegitMaxDy", LiquidLocomotionUtil.ladderLegitMaxDy(plugin));
        double legitMaxVOff = cfgDouble("ladderLegitMaxVOff", LiquidLocomotionUtil.ladderLegitMaxVOff(plugin));
        double blatantDy = cfgDouble("ladderBlatantDy", LiquidLocomotionUtil.ladderBlatantDy(plugin));

        if (result.onClimbable && !LiquidLocomotionUtil.isLegitLadderMotion(dy, vOff, legitMaxDy, legitMaxVOff)) {
            int gain = LiquidLocomotionUtil.isBlatantFastLadder(dy, vOff, legitMaxDy, blatantDy, legitMaxVOff) ? 2 : 1;
            if (flagBuffered(p, data, gain,
                    "fastLadder dy=" + r(dy) + " vOff=" + r(vOff) + " " + result.debug)) {
                if (LiquidLocomotionUtil.isBlatantFastLadder(dy, vOff, legitMaxDy, blatantDy, legitMaxVOff)) {
                    predictionSetback(p, data, "simulation");
                }
            }
            return;
        }

        double hThr = cfgDouble("engineHorizontalOffset", 0.08D);
        double surfaceThr = cfgDouble("surfaceHorizontalOffset", 0.065D);
        boolean horizontalViolation = hOff > hThr
                || (surfaceJesus && hOff > surfaceThr && !result.inWater);

        if (!horizontalViolation) {
            cool(p, 0.35D);
            return;
        }

        int gain = hOff > hThr * 2.5D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "hOff=" + r(hOff) + " water=" + result.inWater
                        + " climb=" + result.onClimbable + " surface=" + surfaceJesus
                        + " dy=" + r(dy) + " " + result.debug)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
