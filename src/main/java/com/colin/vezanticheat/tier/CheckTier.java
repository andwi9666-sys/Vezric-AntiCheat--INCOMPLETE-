package com.colin.vezanticheat.tier;

/**
 * Polar-style check tiers ordered from fastest-ban (most heuristic) to slowest-ban (most lenient).
 */
public enum CheckTier {
    CHARACTERISTICS(25, 1.0D, false),
    PRISM(40, 1.0D, true),
    SIMULATION(90, 0.5D, true),
    PREDICTION(150, 0.35D, true);

    public final double defaultPunishVl;
    public final double defaultFailWeight;
    public final boolean defaultSetback;

    CheckTier(double defaultPunishVl, double defaultFailWeight, boolean defaultSetback) {
        this.defaultPunishVl = defaultPunishVl;
        this.defaultFailWeight = defaultFailWeight;
        this.defaultSetback = defaultSetback;
    }
}
