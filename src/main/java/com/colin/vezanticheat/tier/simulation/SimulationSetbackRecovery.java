package com.colin.vezanticheat.tier.simulation;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FallArcTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Post-setback recovery cheat: continued blatant offset after a pending setback was issued.
 *
 * <p>Normal walking while a setback is pending is handled by packet blocking, not VL.</p>
 */
public final class SimulationSetbackRecovery extends AbstractMovementTierCheck {

    public SimulationSetbackRecovery(VezAntiCheat plugin) {
        super(plugin, "SimulationSetbackRecovery", CheckTier.SIMULATION);
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, EngineResult result, long nowMs) {
        if (p == null || data == null || result == null || !result.checked) return;
        if (lagGated(p, data)) return;
        if (!data.isPendingSetback()) {
            cool(p, 0.3D);
            return;
        }

        long graceMs = cfgLong("graceMs", 350L);
        long since = nowMs - data.getPendingSetbackSinceMs();
        if (since < graceMs) {
            cool(p, 0.3D);
            return;
        }

        long windowMs = cfgLong("recoveryWindowMs", 2000L);
        if (since > windowMs) {
            data.clearPendingSetback();
            cool(p, 0.3D);
            return;
        }

        if (EngineMovementGrace.isLikelyLegitJumpArc(plugin, p, data, result, nowMs)) {
            data.clearPendingSetback();
            cool(p, 0.3D);
            return;
        }

        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, nowMs)) {
            data.clearPendingSetback();
            cool(p, 0.3D);
            return;
        }

        if (FallArcTracker.isLikelyLegitFallArc(plugin, data, result, nowMs)) {
            data.clearPendingSetback();
            cool(p, 0.3D);
            return;
        }

        if (EngineMovementGrace.shouldExemptEngineFlag(plugin, p, data, result, nowMs, name())) {
            cool(p, 0.3D);
            return;
        }

        double threshold = cfgDouble("threshold", 0.22D);
        Location target = data.getPendingSetbackTarget();
        double distFromTarget = 0.0D;
        if (target != null && p.getWorld().equals(target.getWorld()) && data.getLastLoc() != null) {
            distFromTarget = target.distance(data.getLastLoc());
        }

        double maxTargetDistance = cfgDouble("maxTargetDistance", 2.5D);
        if (result.offset <= threshold || distFromTarget < maxTargetDistance) {
            cool(p, 0.3D);
            return;
        }

        int gain = result.offset > threshold * 2.0D ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "setbackRecovery off=" + r(result.offset) + " dist=" + r(distFromTarget)
                        + " since=" + since + "ms " + result.debug)) {
            data.clearPendingSetback();
        }
    }
}
