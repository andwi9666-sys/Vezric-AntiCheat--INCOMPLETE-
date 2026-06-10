package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.EngineResult;
import org.bukkit.util.Vector;

/**
 * Detects cheat fly patterns: hover, slow glide (-0.08/tick), bobbing hover, and sustained airborne travel.
 */
public final class FlyPatternUtil {

    private FlyPatternUtil() {}

    public static void observe(PlayerData data, EngineResult er) {
        if (data == null || er == null || !er.checked) return;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());
        boolean airborne = !er.clientGround && !er.predictedOnGround
                && !er.inWater && !er.onClimbable && !er.inWeb
                && !er.knockbackTick && !er.explosionTick;

        if (airborne && dy <= -0.008D && dy >= -0.22D && distH >= 0.04D) {
            data.setEngineGlideTicks(data.getEngineGlideTicks() + 1);
        } else {
            data.setEngineGlideTicks(Math.max(0, data.getEngineGlideTicks() - 1));
        }
    }

    public static boolean isSuspiciousHover(PlayerData data, EngineResult er) {
        if (data == null || er == null) return false;
        Vector actual = er.actual == null ? new Vector() : er.actual;
        return data.getEngineHoverTicks() >= 3
                && Math.abs(actual.getY()) < 0.03D
                && !er.clientGround;
    }

    /** Bobbing hover: oscillating Y while airborne (delegates to physics tracker evidence). */
    public static boolean isSuspiciousBobbingHover(PlayerData data, EngineResult er) {
        if (data == null || er == null || er.clientGround) return false;
        return FlyPhysicsTracker.hasActiveBobbingEvidence(data);
    }

    /** Grim-style glide fly: slow constant descent while moving horizontally. */
    public static boolean isSuspiciousGlide(PlayerData data, EngineResult er, long nowMs) {
        if (data == null || er == null) return false;
        if (er.clientGround || er.inWater || er.onClimbable || er.inWeb) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double dy = actual.getY();
        double distH = Math.hypot(actual.getX(), actual.getZ());

        boolean recentJump = data.getLastJumpTime() > 0L && nowMs - data.getLastJumpTime() <= 450L;
        if (recentJump && dy > 0.05D) return false;

        return data.getEngineGlideTicks() >= 5
                && data.getEngineAirborneTicks() >= 8
                && dy <= -0.008D && dy >= -0.22D
                && distH >= 0.06D;
    }

    /** Airborne travel without a recent jump or fall arc (classic hover/fly). */
    public static boolean isSuspiciousAirborneTravel(PlayerData data, EngineResult er, long nowMs) {
        if (data == null || er == null) return false;
        if (er.clientGround || er.inWater || er.onClimbable || er.inWeb) return false;
        if (data.isFallArcActive()) return false;

        long sinceJump = data.getLastJumpTime() > 0L ? nowMs - data.getLastJumpTime() : Long.MAX_VALUE;
        if (sinceJump <= 350L) return false;

        Vector actual = er.actual == null ? new Vector() : er.actual;
        double distH = Math.hypot(actual.getX(), actual.getZ());
        double dy = actual.getY();

        return data.getEngineAirborneTicks() >= 8
                && distH >= 0.06D
                && dy > -0.35D && dy < 0.25D
                && er.verticalOffset > 0.028D;
    }
}
