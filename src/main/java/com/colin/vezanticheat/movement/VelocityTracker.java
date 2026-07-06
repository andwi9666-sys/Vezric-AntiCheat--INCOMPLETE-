package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.util.Vector;

public final class VelocityTracker {

    private VelocityTracker() {}

    public static Vector pendingVelocity(PlayerMovementState state, PlayerData data, long nowMs, long windowMs) {
        if (state == null || data == null) return null;
        Vector velocity = data.getLastVelocity();
        long time = data.getLastVelocityTime();
        if (velocity == null || time <= 0L || nowMs - time > windowMs) return null;
        if (time <= state.lastVelocityConsumedMs && state.velocityTicks > 3) return null;
        state.lastVelocityConsumedMs = Math.max(state.lastVelocityConsumedMs, time);
        return velocity.clone();
    }

    public static MovementViolation violation(PlayerMovementState state, SimulationResult partial,
                                              Vector expectedVelocity) {
        if (state == null || partial == null || expectedVelocity == null) return null;
        double expectedH = Math.hypot(expectedVelocity.getX(), expectedVelocity.getZ());
        double actualH = Math.hypot(partial.actualMotion.getX(), partial.actualMotion.getZ());
        if (expectedH < 0.06D) return null;
        double ratio = actualH / expectedH;
        if (ratio >= 0.62D && partial.verticalOffset < 0.18D) return null;
        double confidence = Math.min(1.0D, (0.70D - Math.min(0.70D, ratio)) + partial.verticalOffset);
        return new MovementViolation(MovementFamily.VELOCITY, confidence, partial.offset,
                partial.expectedMotion, partial.actualMotion, state.velocityTicks,
                "ratio=" + round(ratio) + " expectedH=" + round(expectedH),
                confidence >= 0.45D);
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
