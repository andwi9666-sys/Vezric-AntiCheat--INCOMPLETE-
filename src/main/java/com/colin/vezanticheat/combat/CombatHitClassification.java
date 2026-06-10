package com.colin.vezanticheat.combat;

/**
 * Per-hit reach and aim geometry grade from CLEAN to IMPOSSIBLE.
 * Middle tiers (LENIENT, VERY_LENIENT) are gray-zone evidence: they add buffer
 * over time instead of triggering immediate punishment on a single hit.
 */
public enum CombatHitClassification {

    CLEAN,
    LENIENT,
    VERY_LENIENT,
    BAD,
    IMPOSSIBLE;

    public boolean isSuspicious() {
        return this == BAD || this == IMPOSSIBLE;
    }
}
