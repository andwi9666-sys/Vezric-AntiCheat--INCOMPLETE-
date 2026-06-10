package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import org.bukkit.entity.Player;

/**
 * Multi-family corroboration gate: counts distinct engine sub-signals firing on one tick.
 *
 * <p>Requires {@code minFamilies} independent signals (horizontal, vertical, noslow, sprint,
 * phase collision, timer debt, etc.) before flagging. Intended as a high-confidence SIMULATION
 * umbrella when multiple decomposed checks would agree.</p>
 */
public final class SimulationCombo extends AbstractMovementTierCheck {

    public SimulationCombo(VezAntiCheat plugin) {
        super(plugin, "SimulationCombo", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!cfgBool("publicFlag", true)) return;
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())
                || EngineMovementGrace.shouldExemptHorizontalSpeedFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        int families = countActiveFamilies(result);
        int minFamilies = cfgInt("minFamilies", 3);
        double offsetThr = cfgDouble("offsetThreshold", 0.06D);

        if (families < minFamilies || result.offset <= offsetThr) {
            cool(p, 0.3D);
            return;
        }

        int gain = families >= minFamilies + 1 ? 2 : 1;
        flagBuffered(p, data, gain,
                "families=" + families + "/" + minFamilies + " off=" + r(result.offset) + " " + result.debug);
    }

    private int countActiveFamilies(EngineResult result) {
        int count = 0;
        double hThr = cfgDouble("horizontalThreshold", 0.07D);
        double vThr = cfgDouble("verticalThreshold", 0.09D);

        if (result.horizontalOffset > hThr) count++;
        if (result.verticalOffset > vThr) count++;
        if (result.usingItem && result.noSlowExcess > cfgDouble("noSlowThreshold", 0.03D)) count++;
        if (result.illegalSprint()) count++;
        if (result.illegalSneak()) count++;
        if ((result.collisionX || result.collisionZ) && result.horizontalOffset > 0.08D) count++;
        if (result.timerDebt() >= cfgDouble("timerDebtMs", 100.0D)) count++;
        if (result.inWeb && result.offset > 0.05D) count++;
        if (result.inWater && result.horizontalOffset > 0.08D) count++;
        if (result.onClimbable && result.offset > 0.07D) count++;
        if (result.frictionMismatch()) count++;
        if (result.entityPushOffset() > 0.04D) count++;
        return count;
    }
}
