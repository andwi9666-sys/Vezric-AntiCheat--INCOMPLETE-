package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.entity.Player;

/**
 * PrismAimModulo360 — GrimAC {@code AimModulo360} analogue.
 *
 * <p>Detects clients that wrap yaw with {@code % 360}: a sudden RAW (unwrapped) yaw delta greater than
 * ~320 degrees — the wrap discontinuity — while the player was rotating normally on the previous packet
 * (raw delta &lt; ~30) and the current yaw sits within a single rotation. Vanilla clients accumulate yaw
 * continuously and never produce this jump. Uses the raw packet-to-packet subtraction (not the wrapped
 * {@code angleDiff} delta, which would hide the wrap), carrying the previous raw delta on PlayerData.</p>
 */
public final class PrismAimModulo360 extends TierCheck {

    public PrismAimModulo360(VezAntiCheat plugin) {
        super(plugin, "PrismAimModulo360", CheckTier.PRISM);
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        if (p == null || data == null) return;

        // Raw (unwrapped) delta of this packet vs the previous one, and the previous raw delta.
        float rawDelta = yaw - data.getPriorYaw();
        float lastRawDelta = data.getAimModuloLastRawYawDelta();
        data.setAimModuloLastRawYawDelta(rawDelta);  // keep the sequence intact even when lag-gated

        if (lagGated(p, data)) return;

        double wrapDeltaMin = plugin.tierCfg().checkDouble(name(), "wrapDeltaMin", 320.0D);
        double priorDeltaMax = plugin.tierCfg().checkDouble(name(), "priorDeltaMax", 30.0D);

        if (yaw < 360.0F && yaw > -360.0F
                && Math.abs(rawDelta) > wrapDeltaMin
                && Math.abs(lastRawDelta) < priorDeltaMax) {
            if (incrementBuffer(p, 1)) {
                fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.0D),
                        "yaw-wrap rawDelta=" + round3(rawDelta) + " lastRawDelta=" + round3(lastRawDelta)
                                + " yaw=" + round3(yaw));
                resetBuffer(p);
            }
        }
    }
}
