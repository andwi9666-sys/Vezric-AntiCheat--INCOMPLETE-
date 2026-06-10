package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;

import java.util.List;

public final class CharacteristicsRunner extends AbstractTierRunner {
    public CharacteristicsRunner(VezAntiCheat plugin, List<TierCheck> checks) {
        super(plugin, CheckTier.CHARACTERISTICS, checks);
    }
}
