package com.colin.vezanticheat.utils;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Boat;

/**
 * Lightweight vehicle speed envelope for RT5-002. Engine prediction is skipped while mounted;
 * this util flags sustained impossible horizontal deltas vs vanilla boat/minecart limits.
 */
public final class VehicleMovementUtil {

    private VehicleMovementUtil() {}

    public static double maxHorizontalPerTick(Entity vehicle, double boatMax, double cartMax, double defaultMax) {
        if (vehicle instanceof Boat) return boatMax;
        if (vehicle instanceof Minecart) return cartMax;
        return defaultMax;
    }

    public static boolean exceedsEnvelope(double horizontalDelta, double maxPerTick, double tolerance) {
        if (horizontalDelta <= 0.0D || maxPerTick <= 0.0D) return false;
        return horizontalDelta > maxPerTick * Math.max(1.0D, tolerance);
    }

    public static int nextViolationStreak(int current, boolean exceeded, int resetBelow) {
        if (!exceeded) return 0;
        int next = current + 1;
        return Math.max(0, next);
    }

    public static boolean shouldFlag(int streak, int bufferToFlag) {
        return streak >= Math.max(1, bufferToFlag);
    }
}
