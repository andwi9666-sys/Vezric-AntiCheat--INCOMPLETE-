package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;

/**
 * Retired scaffold compatibility stub.
 *
 * <p>The old behind-placement rule bucket was removed. Movement-placement
 * synchronization is now scored inside {@link PrismScaffoldA}'s silent analyzer.</p>
 */
public final class PrismScaffoldD extends TierCheck {
    public PrismScaffoldD(VezAntiCheat plugin) {
        super(plugin, "PrismScaffoldD", CheckTier.PRISM);
    }
}
