package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.SpiderUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Solid-wall spider climb: sustained upward motion while colliding with a non-climbable wall.
 */
public final class PredictionSpider extends AbstractMovementTierCheck {

    public PredictionSpider(VezAntiCheat plugin) {
        super(plugin, "PredictionSpider", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (!engineActive()) return;

        EngineResult result = engineResult(data);
        if (result == null) {
            cool(p, 0.35D);
            return;
        }

        boolean spiderTick = SpiderUtil.isSpiderAscendTick(plugin, p, data, result, nowMs);
        SpiderUtil.observe(data, spiderTick);

        int minStreak = cfgInt("minStreak", 3);
        if (data.getSpiderAscendStreak() < minStreak) {
            cool(p, 0.35D);
            return;
        }

        double minVOff = cfgDouble("minVerticalOffset", 0.035D);
        double minOff = cfgDouble("minOffset", 0.04D);
        boolean violation = result.verticalOffset >= minVOff || result.offset >= minOff;
        if (!violation) {
            cool(p, 0.3D);
            return;
        }

        Vector actual = result.actual == null ? new Vector() : result.actual;
        int gain = data.getSpiderAscendStreak() >= minStreak + 2 ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "streak=" + data.getSpiderAscendStreak()
                        + " dy=" + r(actual.getY())
                        + " off=" + r(result.offset)
                        + " vOff=" + r(result.verticalOffset)
                        + " hOff=" + r(result.horizontalOffset)
                        + " " + result.debug)) {
            predictionSetback(p, data, "simulation");
        }
    }
}
