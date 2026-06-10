package com.colin.vezanticheat.tier.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.tier.AbstractMovementTierCheck;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.NoFallTracker;
import com.colin.vezanticheat.utils.NoFallUtil;
import org.bukkit.entity.Player;

/**
 * Blink nofall: large packet gap, significant drop, land on ground without fall damage.
 */
public final class PredictionNoFallBlink extends AbstractMovementTierCheck {

    public PredictionNoFallBlink(VezAntiCheat plugin) {
        super(plugin, "PredictionNoFallBlink", CheckTier.PREDICTION);
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
        if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            cool(p, 0.35D);
            return;
        }

        if (!NoFallTracker.hasPendingBlinkLand(data, nowMs, plugin)) {
            cool(p, 0.3D);
            return;
        }

        EngineResult er = engineResult(data);
        if (er == null || !er.clientGround) {
            cool(p, 0.3D);
            return;
        }

        long blinkMinGapMs = cfgLong("blinkMinGapMs",
                plugin.getConfig().getLong("nofall.blink-min-gap-ms", 120L));
        long interval = data.getLastFlyingIntervalMs();
        double minBlinkDrop = cfgDouble("minBlinkDrop",
                plugin.getConfig().getDouble("nofall.min-blink-drop", 3.0D));
        double blatantDrop = cfgDouble("blatantDrop", 6.0D);
        double drop = data.getNoFallBlinkDrop();

        if (interval < blinkMinGapMs || drop < minBlinkDrop) {
            cool(p, 0.3D);
            return;
        }

        if (data.isNoFallDamageResolved()) {
            data.setNoFallBlinkLandMs(0L);
            data.setNoFallBlinkDrop(0.0D);
            cool(p, 0.45D);
            return;
        }

        long windowMs = cfgLong("damageWindowMs",
                plugin.getConfig().getLong("nofall.damage-window-ms", 1000L));
        if (nowMs - data.getNoFallBlinkLandMs() < cfgLong("damageWaitMs", 140L)) {
            cool(p, 0.3D);
            return;
        }

        int gain = drop >= blatantDrop && interval >= 200L ? 2 : 1;
        if (flagBuffered(p, data, gain,
                "blink-nofall gap=" + interval + "ms drop=" + r(drop))) {
            if (drop >= blatantDrop && interval >= 200L) {
                requestBlatant(p, data, "blink-nofall");
            }
            data.setNoFallBlinkLandMs(0L);
            data.setNoFallBlinkDrop(0.0D);
            data.setPreBlinkPeakY(0.0D);
        } else if (nowMs - data.getNoFallBlinkLandMs() > windowMs) {
            data.setNoFallBlinkLandMs(0L);
            data.setNoFallBlinkDrop(0.0D);
        }
    }
}
