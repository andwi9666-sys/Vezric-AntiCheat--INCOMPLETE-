package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.AimSensitivityProcessor;
import org.bukkit.entity.Player;

/**
 * CharAimSensitivity — GrimAC {@code AimProcessor} analogue.
 *
 * <p>Recovers the client's mouse sensitivity from the GCD of rotation deltas
 * ({@link AimSensitivityProcessor}) and flags rotations whose recovered sensitivity is impossible
 * (outside the {@code [0,1]} slider, i.e. non-vanilla rotation math) — a signature of aimbots that
 * synthesise rotations without honouring the client's discrete mouse step. Evaluated on attack so it
 * only acts during combat; gated on a minimum number of conforming samples to stay FP-safe. The check
 * name begins with {@code CharAim}, so {@code TierCheck#fail} auto-exempts Bedrock/Geyser players
 * (touch/controller aim has no mouse GCD).</p>
 */
public final class CharAimSensitivity extends TierCheck {

    public CharAimSensitivity(VezAntiCheat plugin) {
        super(plugin, "CharAimSensitivity", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        AimSensitivityProcessor.Result res =
                AimSensitivityProcessor.analyze(data.getYawDeltas(), data.getPitchDeltas());

        int minSamples = plugin.tierCfg().checkInt(name(), "minSignificantSamples",
                AimSensitivityProcessor.SIGNIFICANT_SAMPLES);
        if (res.significantSamples < minSamples) {
            decay(p, 0.25D);
            return;
        }

        double suspicion = res.suspicion();
        double threshold = plugin.tierCfg().checkDouble(name(), "suspicionThreshold", 0.5D);
        if (suspicion < threshold) {
            decay(p, 0.25D);
            return;
        }

        int gain = suspicion >= 0.85D ? 2 : 1;
        if (incrementBuffer(p, gain)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "vl", 1.0D),
                    "non-vanilla sens=" + round3(res.sensitivity) + " gcdX=" + round3(res.dividerX)
                            + " samples=" + res.significantSamples + " susp=" + round3(suspicion));
            resetBuffer(p);
        }
    }
}
