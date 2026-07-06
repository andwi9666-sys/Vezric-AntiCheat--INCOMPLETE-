package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.util.Vector;

public final class ExplosionTracker {

    private ExplosionTracker() {}

    public static Vector pendingExplosion(PlayerMovementState state, PlayerData data, long nowMs, long windowMs) {
        if (state == null || data == null) return null;
        Vector explosion = data.getLastExplosionVelocity();
        long time = data.getLastDamageTime();
        if (explosion == null || time <= 0L || nowMs - time > windowMs) return null;
        if (time <= state.lastExplosionConsumedMs && state.explosionTicks > 4) return null;
        state.lastExplosionConsumedMs = Math.max(state.lastExplosionConsumedMs, time);
        return explosion.clone();
    }

    public static MovementViolation violation(PlayerMovementState state, SimulationResult partial,
                                              Vector expectedExplosion) {
        if (state == null || partial == null || expectedExplosion == null) return null;
        double expected = expectedExplosion.length();
        if (expected < 0.08D) return null;
        double actualDot = partial.actualMotion.dot(expectedExplosion.clone().normalize());
        double ratio = Math.max(0.0D, actualDot / expected);
        if (ratio >= 0.55D && partial.offset < 0.25D) return null;
        double confidence = Math.min(1.0D, (0.65D - Math.min(0.65D, ratio)) + partial.offset * 0.6D);
        return new MovementViolation(MovementFamily.EXPLOSION, confidence, partial.offset,
                partial.expectedMotion, partial.actualMotion, state.explosionTicks,
                "ratio=" + round(ratio) + " expected=" + round(expected),
                confidence >= 0.45D);
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
