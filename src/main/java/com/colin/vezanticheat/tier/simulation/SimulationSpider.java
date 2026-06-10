package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.SpiderUtil;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Simulation corroboration for solid-wall spider climbing.
 */
public final class SimulationSpider extends AbstractMovementTierCheck {

    public SimulationSpider(VezAntiCheat plugin) {
        super(plugin, "SimulationSpider", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;

        boolean spiderTick = SpiderUtil.isSpiderAscendTick(plugin, p, data, result, nowMs);
        SpiderUtil.observe(data, spiderTick);

        int minStreak = cfgInt("minStreak", 3);
        if (data.getSpiderAscendStreak() < minStreak) {
            cool(p, 0.3D);
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
        flagBuffered(p, data, gain,
                "streak=" + data.getSpiderAscendStreak()
                        + " dy=" + r(actual.getY())
                        + " off=" + r(result.offset)
                        + " vOff=" + r(result.verticalOffset)
                        + " " + result.debug);
    }
}
