package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.AttackRayContext;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.TierCheck;

/**
 * Tick/input/interact ordering checks (Grim packetorder subset).
 */
public final class PrismPacketOrderSupport {

    private PrismPacketOrderSupport() {}

    public enum OrderKind {
        ATTACK_WITHOUT_MOVE,
        ATTACK_WITHOUT_ROTATE,
        DIG_WITHOUT_MOVE,
        PLACE_WITHOUT_ROTATE
    }

    public static double score(VezAntiCheat plugin, TierCheck check, PlayerData data, long nowMs, OrderKind kind) {
        if (data == null) return 0.0D;
        switch (kind) {
            case ATTACK_WITHOUT_MOVE:
                return scoreAttackWithoutMove(plugin, check, data, nowMs);
            case ATTACK_WITHOUT_ROTATE:
                return scoreAttackWithoutRotate(plugin, check, data, nowMs);
            case DIG_WITHOUT_MOVE:
                if (inCombatGrace(plugin, data, nowMs)) return 0.0D;
                return scoreDigWithoutMove(plugin, check, data, nowMs);
            case PLACE_WITHOUT_ROTATE:
                if (inCombatGrace(plugin, data, nowMs)) return 0.0D;
                return scorePlaceWithoutRotate(plugin, check, data, nowMs);
            default:
                return 0.0D;
        }
    }

    private static boolean inCombatGrace(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null) return false;
        long combatMs = plugin.getConfig().getLong("engine.combat-movement-grace-ms",
                plugin.getConfig().getLong("movement-analysis.combat-window-ms", 450L));
        if (data.getLastUseEntityTime() > 0L && (nowMs - data.getLastUseEntityTime()) <= combatMs) {
            return true;
        }
        return data.getLastDamageTime() > 0L && (nowMs - data.getLastDamageTime()) <= combatMs;
    }

    public static long effectiveFlyingAgeMs(PlayerData data, long nowMs) {
        if (data == null) return Long.MAX_VALUE;
        long moveAge = data.getLastFlyingPacket() <= 0L ? Long.MAX_VALUE : nowMs - data.getLastFlyingPacket();
        long moveMs = data.getLastMoveMillis() <= 0L ? Long.MAX_VALUE : nowMs - data.getLastMoveMillis();
        return Math.min(moveAge, moveMs);
    }

    public static long effectiveRotationAgeMs(PlayerData data, long nowMs) {
        if (data == null) return Long.MAX_VALUE;
        long rotAge = data.getLastRotationPacket() <= 0L ? Long.MAX_VALUE : nowMs - data.getLastRotationPacket();
        long flyAge = effectiveFlyingAgeMs(data, nowMs);
        return Math.min(rotAge, flyAge);
    }

    public static long positionPacketAgeMs(PlayerData data, long nowMs) {
        if (data == null) return Long.MAX_VALUE;
        long posMs = data.badPackets().lastPositionPacketMs();
        return posMs <= 0L ? Long.MAX_VALUE : nowMs - posMs;
    }

    public static long dedicatedRotationAgeMs(PlayerData data, long nowMs) {
        if (data == null) return Long.MAX_VALUE;
        long rotMs = data.getLastDedicatedRotationPacketMs();
        if (rotMs <= 0L) {
            rotMs = data.getLastRotationPacket();
        }
        return rotMs <= 0L ? Long.MAX_VALUE : nowMs - rotMs;
    }

    private static double scoreAttackWithoutMove(VezAntiCheat plugin, TierCheck check, PlayerData data, long nowMs) {
        if (!data.wasLastUseEntityAttack()) return 0.0D;
        long attackAge = nowMs - data.getLastUseEntityTime();
        if (attackAge > plugin.tierCfg().checkLong(check.name(), "attackFreshnessMs", 180L)) return 0.0D;

        boolean requirePositionFirst = plugin.tierCfg().checkBoolean(check.name(), "requirePositionBeforeAttack", true);
        long maxPositionLead = plugin.tierCfg().checkLong(check.name(), "maxPositionLeadMs", 40L);
        long maxMoveAge = plugin.tierCfg().checkLong(check.name(), "maxMoveAgeMs", 120L);
        long flyAge = effectiveFlyingAgeMs(data, nowMs);
        long posAge = positionPacketAgeMs(data, nowMs);

        if (requirePositionFirst && data.isAttackBeforeLastPosition() && flyAge > maxPositionLead) {
            return 1.0D;
        }

        if (requirePositionFirst && posAge > maxPositionLead && flyAge > maxMoveAge) {
            return 1.0D;
        }

        long moveAge = Math.min(flyAge, posAge);
        if (moveAge <= maxMoveAge) return 0.0D;
        return Math.min(1.0D, moveAge / (double) Math.max(1L, maxMoveAge));
    }

    private static double scoreAttackWithoutRotate(VezAntiCheat plugin, TierCheck check, PlayerData data, long nowMs) {
        if (!data.wasLastUseEntityAttack()) return 0.0D;
        long attackAge = nowMs - data.getLastUseEntityTime();
        if (attackAge > plugin.tierCfg().checkLong(check.name(), "attackFreshnessMs", 180L)) return 0.0D;

        long rotAge = dedicatedRotationAgeMs(data, nowMs);
        long maxRotAge = plugin.tierCfg().checkLong(check.name(), "maxRotationAgeMs", 145L);
        if (rotAge <= maxRotAge) return 0.0D;

        AttackRayContext ray = data.getAttackRayContext();
        if (ray == null) {
            return 0.0D;
        }
        double cleanAngle = plugin.tierCfg().checkDouble(check.name(), "cleanAttackAngle", 38.0D);
        double cleanDot = plugin.tierCfg().checkDouble(check.name(), "cleanAttackDot", 0.55D);
        if (ray.angleDegrees <= cleanAngle && ray.lookDot >= cleanDot) {
            return 0.0D;
        }

        boolean requireBadRay = plugin.tierCfg().checkBoolean(check.name(), "requireBadAttackRay", true);
        double minBadAngle = plugin.tierCfg().checkDouble(check.name(), "minBadAttackAngle", 50.0D);
        double maxBadDot = plugin.tierCfg().checkDouble(check.name(), "maxBadAttackDot", 0.35D);
        if (requireBadRay && (ray.angleDegrees < minBadAngle || ray.lookDot > maxBadDot)) {
            return 0.0D;
        }
        return Math.min(1.0D, rotAge / (double) Math.max(1L, maxRotAge));
    }

    private static double scoreDigWithoutMove(VezAntiCheat plugin, TierCheck check, PlayerData data, long nowMs) {
        if (data.getLastDigStartMs() <= 0L) return 0.0D;
        long digAge = nowMs - data.getLastDigStartMs();
        if (digAge > plugin.tierCfg().checkLong(check.name(), "digFreshnessMs", 160L)) return 0.0D;
        long moveAge = effectiveFlyingAgeMs(data, nowMs);
        long maxMoveAge = plugin.tierCfg().checkLong(check.name(), "maxMoveAgeMs", 110L);
        if (moveAge <= maxMoveAge) return 0.0D;
        return Math.min(1.0D, moveAge / (double) Math.max(1L, maxMoveAge));
    }

    private static double scorePlaceWithoutRotate(VezAntiCheat plugin, TierCheck check, PlayerData data, long nowMs) {
        if (data.getLastBlockPlacePacketTime() <= 0L) return 0.0D;
        long placeAge = nowMs - data.getLastBlockPlacePacketTime();
        if (placeAge > plugin.tierCfg().checkLong(check.name(), "placeFreshnessMs", 160L)) return 0.0D;
        long rotAge = dedicatedRotationAgeMs(data, nowMs);
        long maxRotAge = plugin.tierCfg().checkLong(check.name(), "maxRotationAgeMs", 130L);
        if (rotAge <= maxRotAge) return 0.0D;
        return Math.min(1.0D, rotAge / (double) Math.max(1L, maxRotAge));
    }
}
