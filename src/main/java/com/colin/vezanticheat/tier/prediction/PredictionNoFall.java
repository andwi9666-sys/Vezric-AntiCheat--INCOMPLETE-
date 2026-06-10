package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.NoFallTracker;
import com.colin.vezanticheat.utils.NoFallUtil;
import org.bukkit.entity.Player;

/**
 * Validates fall damage after landing in worlds where fall damage is enabled.
 */
public final class PredictionNoFall extends AbstractMovementTierCheck {

    public PredictionNoFall(VezAntiCheat plugin) {
        super(plugin, "PredictionNoFall", CheckTier.PREDICTION);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;
        if (NoFallUtil.shouldSkip(p, data)) return;
        if (NoFallUtil.shouldSkipNoFallExpectations(plugin, p)) {
            cool(p, 0.35D);
            return;
        }

        long combatGraceMs = cfgLong("combatGraceMs", 700L);
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            cool(p, 0.35D);
            return;
        }

        if (!NoFallTracker.hasPendingDamageCheck(data, nowMs)) {
            cool(p, 0.3D);
            return;
        }

        long windowMs = cfgLong("damageWindowMs",
                plugin.getConfig().getLong("nofall.damage-window-ms", 1000L));
        double minFallDistance = cfgDouble("minFallDistance",
                plugin.getConfig().getDouble("nofall.min-fall-distance", 2.9D));
        if (data.getNoFallALandFall() < minFallDistance) {
            clearPending(data);
            cool(p, 0.3D);
            return;
        }

        if (data.isNoFallDamageResolved()) {
            double expected = NoFallUtil.expectedFallDamage(p, data.getNoFallALandFall());
            double tolerance = cfgDouble("damageTolerance", 0.35D);
            double minDamage = cfgDouble("minFallDamage", 0.5D);
            if (expected < minDamage
                    || NoFallUtil.damageMatchesExpected(expected, data.getLastFallDamageAmount(), tolerance)) {
                cool(p, 0.5D);
                clearPending(data);
                return;
            }
            if (flagBuffered(p, data, 1,
                    "bad-damage fall=" + r(data.getNoFallALandFall())
                            + " expected=" + r(expected)
                            + " got=" + r(data.getLastFallDamageAmount()))) {
                clearPending(data);
            }
            return;
        }

        if (nowMs - data.getNoFallALandAtMs() <= windowMs) {
            cool(p, 0.3D);
            return;
        }

        if (flagBuffered(p, data, 1,
                "no-damage fall=" + r(data.getNoFallALandFall())
                        + " expected=" + r(NoFallUtil.expectedFallDamage(p, data.getNoFallALandFall()))
                        + " got=0")) {
            clearPending(data);
        }
    }

    private void clearPending(PlayerData data) {
        data.setNoFallALandAtMs(0L);
        data.setNoFallALandFall(0.0D);
        data.setNoFallAExpectedDamageAfterMs(0L);
        data.setNoFallAHasPeakY(false);
        data.setNoFallAPeakY(0.0D);
    }
}
