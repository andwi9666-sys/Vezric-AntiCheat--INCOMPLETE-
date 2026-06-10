package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;

import java.util.List;

public final class PrismRunner extends AbstractTierRunner {
    public PrismRunner(VezAntiCheat plugin, List<TierCheck> checks) {
        super(plugin, CheckTier.PRISM, checks);
    }
}
