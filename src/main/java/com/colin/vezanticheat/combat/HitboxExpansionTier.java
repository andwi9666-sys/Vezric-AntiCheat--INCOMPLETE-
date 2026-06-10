package com.colin.vezanticheat.combat;

/**
 * Tightest expansion shell that connected a combat ray hit.
 * Expansion tiers represent ping-tolerated hitbox shells, not proof of cheating:
 * a hit inside an expanded box is graded leniently rather than instantly BAD.
 */
public enum HitboxExpansionTier {

    NORMAL(0.0D, 0.0D),
    SMALL(0.03D, 0.25D),
    MEDIUM(0.06D, 0.75D),
    FULL(0.10D, 1.5D),
    MISS(Double.NaN, 5.0D);

    private final double expansionAmount;
    private final double baseSuspiciousScore;

    HitboxExpansionTier(double expansionAmount, double baseSuspiciousScore) {
        this.expansionAmount = expansionAmount;
        this.baseSuspiciousScore = baseSuspiciousScore;
    }

    public double getExpansionAmount() {
        return expansionAmount;
    }

    public double getBaseSuspiciousScore() {
        return baseSuspiciousScore;
    }

    public boolean isExpansionTier() {
        return this == SMALL || this == MEDIUM || this == FULL;
    }
}
