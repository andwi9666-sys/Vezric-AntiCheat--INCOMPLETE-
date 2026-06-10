package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Hitbox ray validation helpers: packet-synced eyes, legit flick look candidates, and
 * knockback displacement tolerance for HitboxA / HitboxB.
 */
public final class HitboxUtil {

    private HitboxUtil() {}

    /** Eye position with yaw/pitch from the last client flying packet (not live Bukkit entity). */
    public static Location buildPacketSyncedEye(Player player, PlayerData data) {
        if (player == null) return null;
        Location eye = player.getEyeLocation().clone();
        if (data == null) return eye;

        Location packetLoc = data.getLastLoc();
        if (packetLoc != null && packetLoc.getWorld() != null && eye.getWorld() != null
                && eye.getWorld().equals(packetLoc.getWorld())) {
            eye.setYaw(packetLoc.getYaw());
            eye.setPitch(packetLoc.getPitch());
        }
        return eye;
    }

    /**
     * Candidate look directions for this attack. Legit PvP flicks often send a rotation packet
     * immediately before/after USE_ENTITY; testing only the post-flick look false-flags.
     */
    public static List<Location> attackLookCandidates(VezAntiCheat plugin, PlayerData data,
                                                      Location baseEye, long attackTimeMs,
                                                      String checkName) {
        List<Location> candidates = new ArrayList<Location>();
        if (baseEye == null) return candidates;
        candidates.add(baseEye);

        if (data == null || plugin == null) return candidates;

        float minYawDelta = (float) plugin.cfg().checkDouble(checkName, "alternateLookMinYawDelta", 6.0D);
        float yawDelta = data.getLastRotationYawDelta();
        float pitchDelta = data.getLastRotationPitchDelta();
        float flickYaw = (float) plugin.cfg().checkDouble(checkName, "flickYawThreshold", 10.0D);
        float flickPitch = (float) plugin.cfg().checkDouble(checkName, "flickPitchThreshold", 7.0D);

        boolean largeRotation = yawDelta >= minYawDelta || pitchDelta >= flickPitch * 0.6F;
        boolean recentFlick = isRecentHeadFlick(plugin, data, attackTimeMs, checkName);

        if (!largeRotation && !recentFlick) return candidates;

        Location prior = baseEye.clone();
        prior.setYaw(data.getPriorYaw());
        prior.setPitch(data.getPriorPitch());

        float lookSeparation = CombatUtil.angleDiff(baseEye.getYaw(), prior.getYaw())
                + Math.abs(baseEye.getPitch() - prior.getPitch()) * 0.35F;
        if (lookSeparation < minYawDelta * 0.5F) return candidates;

        boolean duplicate = false;
        for (Location existing : candidates) {
            if (CombatUtil.angleDiff(existing.getYaw(), prior.getYaw()) < 0.5F
                    && Math.abs(existing.getPitch() - prior.getPitch()) < 0.5F) {
                duplicate = true;
                break;
            }
        }
        if (!duplicate) {
            candidates.add(prior);
        }

        if (recentFlick && (yawDelta >= flickYaw || pitchDelta >= flickPitch)) {
            Location preFlick = baseEye.clone();
            float unwindYaw = wrapYaw(baseEye.getYaw() - yawDelta);
            float unwindPitch = clampPitch(baseEye.getPitch() - pitchDelta);
            preFlick.setYaw(unwindYaw);
            preFlick.setPitch(unwindPitch);
            if (CombatUtil.angleDiff(preFlick.getYaw(), prior.getYaw()) >= 1.0F) {
                candidates.add(preFlick);
            }
        }

        return candidates;
    }

    public static boolean isRecentHeadFlick(VezAntiCheat plugin, PlayerData data, long attackTimeMs,
                                            String checkName) {
        if (plugin == null || data == null) return false;

        float flickYaw = (float) plugin.cfg().checkDouble(checkName, "flickYawThreshold", 10.0D);
        float flickPitch = (float) plugin.cfg().checkDouble(checkName, "flickPitchThreshold", 7.0D);
        if (data.getLastRotationYawDelta() < flickYaw && data.getLastRotationPitchDelta() < flickPitch) {
            return false;
        }

        long rotAge = attackTimeMs - data.getLastRotationPacket();
        long maxAge = plugin.cfg().checkLong(checkName, "flickRotationMaxAgeMs", 100L);
        return rotAge >= 0L && rotAge <= maxAge;
    }

    /** Extra hitbox expansion for knockback / airborne displacement during trades. */
    public static double knockbackExpansion(VezAntiCheat plugin, PlayerData attackerData,
                                            PlayerData targetData,
                                            CombatContextAnalyzer.CombatContext combat,
                                            long nowMs, String checkName) {
        if (plugin == null) return 0.0D;

        double expansion = 0.0D;
        long kbGrace = plugin.getConfig().getLong("engine.knockback-grace-ms", 550L);

        if (attackerData != null && attackerData.getLastVelocityTime() > 0L
                && (nowMs - attackerData.getLastVelocityTime()) <= kbGrace) {
            expansion += plugin.cfg().checkDouble(checkName, "attackerKnockbackExpansion", 0.14D);
        }
        if (attackerData != null && attackerData.isVelocityExempt()) {
            expansion += plugin.cfg().checkDouble(checkName, "velocityExemptExpansion", 0.10D);
        }
        if (targetData != null && targetData.getLastVelocityTime() > 0L
                && (nowMs - targetData.getLastVelocityTime()) <= kbGrace) {
            expansion += plugin.cfg().checkDouble(checkName, "targetKnockbackExpansion", 0.20D);
        }
        if (combat != null) {
            if (combat.isAirborne()) {
                expansion += plugin.cfg().checkDouble(checkName, "attackerAirborneExpansion", 0.05D);
            }
            if (combat.isTargetAirborne()) {
                expansion += plugin.cfg().checkDouble(checkName, "targetAirborneExpansion", 0.10D);
            }
            if (combat.isTrade() || combat.isCombo()) {
                expansion += plugin.cfg().checkDouble(checkName, "tradeComboExpansion", 0.04D);
            }
            if (combat.isRecentJump()) {
                expansion += plugin.cfg().checkDouble(checkName, "jumpResetExpansion", 0.05D);
            }
        }
        return expansion;
    }

    /** Angular miss converted to approximate block miss for flick tolerance. */
    public static double flickMissGrace(VezAntiCheat plugin, PlayerData data, long attackTimeMs,
                                        double distance, String checkName) {
        if (plugin == null || data == null || !isRecentHeadFlick(plugin, data, attackTimeMs, checkName)) {
            return 0.0D;
        }
        float yawDelta = data.getLastRotationYawDelta();
        double factor = plugin.cfg().checkDouble(checkName, "flickMissFactor", 0.004D);
        double cap = plugin.cfg().checkDouble(checkName, "flickMissCap", 0.35D);
        double distScale = Math.max(1.0D, Math.min(4.0D, distance));
        return Math.min(cap, yawDelta * factor * distScale);
    }

    /**
     * Cast rays from every candidate look; return the best outcome (hit wins, else smallest miss).
     */
    public static CombatUtil.RayTraceResult bestRayTraceToHitbox(List<Location> eyes, Location base,
                                                                 double width, double height,
                                                                 double expansion, double maxDistance) {
        CombatUtil.RayTraceResult best = null;
        if (eyes == null || eyes.isEmpty()) {
            return new CombatUtil.RayTraceResult(false, Double.MAX_VALUE, 0.0);
        }

        for (Location eye : eyes) {
            if (eye == null) continue;
            CombatUtil.RayTraceResult result = CombatUtil.rayTraceToHitbox(
                    eye, base, width, height, expansion, maxDistance);
            if (result == null) {
                return null;
            }
            if (result.isHit()) {
                return result;
            }
            if (best == null || result.getMissDistance() < best.getMissDistance()) {
                best = result;
            }
        }
        return best == null ? new CombatUtil.RayTraceResult(false, Double.MAX_VALUE, 0.0) : best;
    }

    private static float wrapYaw(float yaw) {
        while (yaw <= -180.0F) yaw += 360.0F;
        while (yaw > 180.0F) yaw -= 360.0F;
        return yaw;
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }
}
