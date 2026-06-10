package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.prediction.PredictionResult;
import com.colin.vezanticheat.utils.FallArcTracker;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Shared base for PREDICTION and SIMULATION tier movement checks.
 *
 * <p>These checks consume either the Grim {@link EngineResult} stored on {@link PlayerData}
 * each flying packet, or the legacy {@link PredictionResult} when the engine is disabled.
 * Each tier check owns an independent buffer and VL pool via {@link TierCheck}.</p>
 *
 * <p>Subclasses implement {@link #onFlyingPacket} (prediction tier) or
 * {@link #onEngineResult} (simulation tier) with domain-specific thresholds and grace paths
 * ported from the legacy prediction sub-checks.</p>
 */
public abstract class AbstractMovementTierCheck extends TierCheck {

    protected AbstractMovementTierCheck(VezAntiCheat plugin, String name, CheckTier tier) {
        super(plugin, name, tier);
    }

    /** True when the Grim movement engine is enabled and should drive detection. */
    protected final boolean engineActive() {
        return plugin.engine() != null && plugin.engine().isEnabled();
    }

    /**
     * Latest checked engine result for this player, or null when the engine is off, the tick
     * was exempt, or no result exists yet.
     */
    protected final EngineResult engineResult(PlayerData data) {
        if (!engineActive() || data == null) return null;
        EngineResult result = data.getLastEngineResult();
        if (result == null || !result.checked) return null;
        return result;
    }

    /** Legacy prediction envelope when the engine is inactive or for corroboration signals. */
    protected final PredictionResult predictionResult(PlayerData data) {
        if (plugin.prediction() == null || data == null) return null;
        return plugin.prediction().getLastResult(data);
    }

    protected final double cfgDouble(String key, double def) {
        return plugin.tierCfg().checkDouble(name(), key, def);
    }

    protected final int cfgInt(String key, int def) {
        return plugin.tierCfg().checkInt(name(), key, def);
    }

    protected final long cfgLong(String key, long def) {
        return plugin.tierCfg().checkLong(name(), key, def);
    }

    protected final boolean cfgBool(String key, boolean def) {
        return plugin.tierCfg().checkBoolean(name(), key, def);
    }

    /** Decay buffer and per-check VL on clean ticks. */
    protected final void cool(Player p, double decayAmount) {
        coolBuffer(p, 1);
        decay(p, decayAmount);
    }

    /**
     * Increment the per-check buffer; when it reaches {@link #bufferToFlag()}, apply VL and reset.
     *
     * @return true when a flag was emitted
     */
    protected final boolean flagBuffered(Player p, PlayerData data, int gain, double failVl, String debug) {
        if (incrementBuffer(p, gain)) {
            fail(p, data, failVl, debug);
            resetBuffer(p);
            return true;
        }
        return false;
    }

    protected final boolean flagBuffered(Player p, PlayerData data, int gain, String debug) {
        return flagBuffered(p, data, gain, cfgDouble("failVl", 1.0D), debug);
    }

    protected final void requestBlatant(Player p, PlayerData data, String reason) {
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return;
        }
        MovementEnforcement.requestBlatantEnforcement(plugin, p, data, reason);
    }

    protected final void blockMovementPacket(PlayerData data, String reason) {
        MovementEnforcement.blockCurrentMovementPacket(plugin, data, reason);
    }

    protected final void predictionSetback(Player p, PlayerData data, String reason) {
        if (!setbackEnabled()) return;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return;
        }
        data.setEngineOffsetAdvantage(0.0D);
        requestBlatant(p, data, name() + " " + reason);
    }

    protected final void predictionSetback(Player p, PlayerData data, Location to, String reason) {
        if (!setbackEnabled()) return;
        if (FallArcTracker.shouldSuppressLegitFallSetback(plugin, data, System.currentTimeMillis())) {
            return;
        }
        requestBlatant(p, data, name() + " " + reason);
    }

    protected static double r(double value) {
        return round3(value);
    }

    /** Called by tier runners when item-use movement grace suppresses detection for this tick. */
    public final void tickItemUseMovementGrace(Player p, PlayerData data) {
        ItemUseMovementUtil.applyMovementGrace(data);
        cool(p, 0.4D);
    }
}
