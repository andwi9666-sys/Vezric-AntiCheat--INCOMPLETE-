package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import org.bukkit.entity.Player;

import java.util.List;

public final class PredictionRunner extends AbstractTierRunner {
    public PredictionRunner(VezAntiCheat plugin, List<TierCheck> checks) {
        super(plugin, CheckTier.PREDICTION, checks);
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        // Legit server-granted flight (/fly, creative) is exempt from ALL movement prediction: the
        // ground/sprint envelopes and the offset engine do not model flight, so at max fly speed and while
        // hovering they false-flag (Speed/Offset/Fly/Step/Phase...). isFlying()/getAllowFlight() is
        // authoritative server-side state — a fly HACK never receives the allow-flight grant — so this
        // only exempts genuinely granted flight, matching MovementCheckRunner's own "flying" exemption.
        if (p != null && (p.isFlying() || p.getAllowFlight())) {
            return;
        }
        if (p != null && data != null && ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) {
            for (TierCheck check : checks) {
                if (check instanceof AbstractMovementTierCheck) {
                    ((AbstractMovementTierCheck) check).tickItemUseMovementGrace(p, data);
                }
            }
            return;
        }
        super.onFlyingPacket(p, data, nowMs);
    }
}
