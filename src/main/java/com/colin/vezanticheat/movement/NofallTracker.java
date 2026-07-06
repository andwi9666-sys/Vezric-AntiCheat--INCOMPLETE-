package com.colin.vezanticheat.movement;

public final class NofallTracker {

    private NofallTracker() {}

    public static MovementViolation violation(PlayerMovementState state, SimulationResult partial) {
        if (state == null || partial == null) return null;
        if (partial.inWater || partial.inLava || partial.inWeb || partial.onClimbable || partial.onSlime) {
            return null;
        }
        if (partial.clientGround && !partial.predictedGround && state.fallDistance > 2.0D) {
            return new MovementViolation(MovementFamily.GROUND_SPOOF, 0.78D, partial.verticalOffset,
                    partial.expectedMotion, partial.actualMotion, state.airborneTicks,
                    "clientGround airborne fall=" + round(state.fallDistance), true);
        }
        if (state.fallDistance > 3.0D && partial.clientGround && partial.verticalOffset > 0.08D) {
            return new MovementViolation(MovementFamily.NOFALL, 0.72D, partial.verticalOffset,
                    partial.expectedMotion, partial.actualMotion, state.airborneTicks,
                    "fallDistance=" + round(state.fallDistance), true);
        }
        return null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
