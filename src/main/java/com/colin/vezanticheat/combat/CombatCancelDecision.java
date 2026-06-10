package com.colin.vezanticheat.combat;

/**
 * Outcome of combat hit cancellation evaluation for one attack.
 * Cancellation uses pre-hit buffer plus classification: a single suspicious hit
 * rarely cancels unless IMPOSSIBLE or buffer already exceeded thresholds.
 */
public final class CombatCancelDecision {

    public static final String REASON_IMPOSSIBLE_RAYTRACE = "Hit cancelled due to impossible raytrace";
    public static final String REASON_HIGH_COMBAT_BUFFER = "Hit cancelled due to high combat buffer";

    public static final CombatCancelDecision ALLOW = new CombatCancelDecision(false, null);

    private final boolean cancel;
    private final String reason;

    public CombatCancelDecision(boolean cancel, String reason) {
        this.cancel = cancel;
        this.reason = reason;
    }

    public boolean shouldCancel() {
        return cancel;
    }

    public String getReason() {
        return reason;
    }

    public static CombatCancelDecision resolve(CombatHitResult result,
                                               double preBuffer,
                                               CombatConfig config,
                                               boolean cancelImpossibleHits) {
        return resolve(result, preBuffer, config, cancelImpossibleHits, false);
    }

    public static CombatCancelDecision resolve(CombatHitResult result,
                                               double preBuffer,
                                               CombatConfig config,
                                               boolean cancelImpossibleHits,
                                               boolean lenientImpossibleRaytrace) {
        if (result == null || config == null) {
            return ALLOW;
        }

        CombatHitClassification classification = result.getClassification();
        if (classification == null) {
            classification = CombatHitClassification.CLEAN;
        }

        if (classification == CombatHitClassification.CLEAN
                || classification == CombatHitClassification.LENIENT) {
            return ALLOW;
        }

        if (cancelImpossibleHits
                && !lenientImpossibleRaytrace
                && classification == CombatHitClassification.IMPOSSIBLE) {
            return new CombatCancelDecision(true, REASON_IMPOSSIBLE_RAYTRACE);
        }

        // VERY_LENIENT with high buffer: sustained gray-zone pattern, not a one-off miss.
        if (classification == CombatHitClassification.VERY_LENIENT
                && preBuffer >= config.getFlagBuffer()) {
            return new CombatCancelDecision(true, REASON_HIGH_COMBAT_BUFFER);
        }

        if (preBuffer >= config.getCancelHitBuffer() && classification.isSuspicious()) {
            return new CombatCancelDecision(true, REASON_HIGH_COMBAT_BUFFER);
        }

        return ALLOW;
    }
}
