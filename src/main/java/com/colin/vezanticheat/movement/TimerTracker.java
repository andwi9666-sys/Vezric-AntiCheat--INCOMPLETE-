package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.data.PlayerData;

public final class TimerTracker {

    private TimerTracker() {}

    public static double update(PlayerMovementState state, PlayerData data, boolean positionIncluded,
                                long intervalMs, MovementPhysicsConfig cfg) {
        if (state == null) return 0.0D;
        long interval = intervalMs <= 0L ? 50L : intervalMs;
        if (interval < 45L) {
            state.timerDebtMs += 50L - interval;
        } else {
            state.timerDebtMs = Math.max(0.0D, state.timerDebtMs - Math.min(15.0D, interval - 50L));
        }
        if (!positionIncluded && data != null) {
            state.blinkTicks++;
        } else if (positionIncluded) {
            state.blinkTicks = Math.max(0, state.blinkTicks - 1);
        }
        state.timerDebtMs = Math.min(1000.0D, state.timerDebtMs);
        if (data != null) {
            data.setTimerDebtMs(Math.round(state.timerDebtMs));
        }
        return state.timerDebtMs;
    }

    public static MovementViolation violation(PlayerMovementState state, SimulationResult partial,
                                              MovementPhysicsConfig cfg) {
        if (state == null || partial == null) return null;
        if (state.timerDebtMs < cfg.timerDebtThresholdMs && state.blinkTicks < 4) return null;
        double confidence = Math.min(1.0D, Math.max(state.timerDebtMs / 350.0D, state.blinkTicks / 12.0D));
        MovementFamily family = state.blinkTicks >= 4 ? MovementFamily.BLINK : MovementFamily.TIMER;
        return new MovementViolation(family, confidence, partial.offset,
                partial.expectedMotion, partial.actualMotion, 0,
                "debt=" + round(state.timerDebtMs) + "ms blinkTicks=" + state.blinkTicks,
                confidence >= 0.65D);
    }

    private static double round(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
