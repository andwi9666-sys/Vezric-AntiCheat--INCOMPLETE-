package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Timer debt corroboration sub-signal for blink/timer cheats.
 * Requires packet-gap blink patterns — not normal sprint or tx-clock jitter.
 */
public final class SimulationBlinkDebt extends AbstractMovementTierCheck {

    public SimulationBlinkDebt(VezAntiCheat plugin) {
        super(plugin, "SimulationBlinkDebt", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }
        if (MovementContextAnalyzer.isLikelyLegitSprintJump(plugin, p, data)) {
            cool(p, 0.3D);
            return;
        }
        if (EngineMovementGrace.isLikelyLegitGroundLocomotion(result)) {
            cool(p, 0.3D);
            return;
        }

        double debtThreshold = cfgDouble("timerDebtMs", 280.0D);
        long gapThreshold = cfgLong("gapMs", 320L);
        double offsetThreshold = cfgDouble("offsetThreshold", 0.28D);

        double debt = result.timerDebt();
        long gap = result.flyingGapMs;

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());

        // Normal sprint/walk: steady ~50ms gaps, low timer debt, moderate horizontal motion.
        if (gap > 0L && gap < gapThreshold && debt < debtThreshold) {
            cool(p, 0.3D);
            return;
        }
        if (result.clientGround && result.predictedOnGround && distH <= 0.42D && gap < gapThreshold * 2L) {
            cool(p, 0.3D);
            return;
        }

        boolean suspiciousOffset = result.offset >= offsetThreshold;
        boolean largeGap = gap >= gapThreshold;
        boolean suspiciousDebt = debt >= debtThreshold;

        // Primary: blink flush (large gap + offset). Secondary: debt only with very large gap.
        boolean blinkPattern = largeGap && suspiciousOffset && gap >= gapThreshold * 1.5D;
        boolean debtWithGap = suspiciousDebt && suspiciousOffset && gap >= gapThreshold;

        if (!blinkPattern && !debtWithGap) {
            cool(p, 0.3D);
            return;
        }

        int gain = blinkPattern && debtWithGap ? 3 : 2;
        if (debt >= debtThreshold * 1.5D && gap >= gapThreshold * 2L) {
            gain = Math.max(gain, 3);
        }

        flagBuffered(p, data, gain,
                "debt=" + r(debt) + "ms gap=" + gap + "ms off=" + r(result.offset) + " " + result.debug);
    }
}
