package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Punitive mitigation for player-vs-player combat cheats: drop the attack packet and snap
 * the attacker away from the victim. Does not use movement-packet correction or valid-ground reset.
 */
public final class CombatMitigationPolicy {

    private CombatMitigationPolicy() {}

    public static boolean isCombatPlayerHitCheck(String checkName) {
        return isCombatPlayerHitCheck(null, checkName);
    }

    public static boolean isCombatPlayerHitCheck(VezAntiCheat plugin, String checkName) {
        if (checkName == null) return false;
        boolean registryDefault = isCombatPlayerHitCheckRegistry(checkName);
        if (plugin == null) return registryDefault;
        return plugin.tierCfg().checkBoolean(checkName, "combatMitigation", registryDefault);
    }

    private static boolean isCombatPlayerHitCheckRegistry(String checkName) {
        if ("CharSilentAim".equals(checkName)) return true;
        if ("CharAimSensitivity".equals(checkName)) return true;
        if ("CharAimSnap".equals(checkName) || "CharAimReset".equals(checkName)
                || "CharAimCenter".equals(checkName) || "CharAimCorrelation".equals(checkName)) {
            return true;
        }
        if ("CharCombatTiming".equals(checkName)) return true;

        if ("PrismInteractionLegality".equals(checkName)) return true;
        if (checkName.startsWith("PrismReach")) return true;
        if (checkName.startsWith("PrismHitbox")) return true;
        if ("PrismBackTrack".equals(checkName)) return true;
        if ("PrismRotationRay".equals(checkName)) return true;
        if (checkName.startsWith("PrismNoRotation")) return true;
        if ("PrismLagRange".equals(checkName)) return true;
        if ("PrismBlockSight".equals(checkName)) return true;
        return false;
    }

    public static void apply(VezAntiCheat plugin, Player attacker, PlayerData data, Entity target,
                             String checkName, String reason, PrismMitigationPolicy.Confidence confidence) {
        if (plugin == null || attacker == null || data == null || confidence == null) return;
        if (PlayerData.bypass(attacker)) return;
        if (confidence == PrismMitigationPolicy.Confidence.LOW) return;
        if (!plugin.getConfig().getBoolean("combat-mitigation.enabled", true)) return;

        if (plugin.getConfig().getBoolean("combat-mitigation.drop-hits-on-flag", true)) {
            data.setBlockCurrentAttackPacket(true);
            data.setBlockedAttackReason(checkName + (reason == null ? "" : " " + reason));
            if (plugin.diagnostics() != null) {
                plugin.diagnostics().record(attacker.getUniqueId(), checkName, "combat-drop-hit", reason);
            }
        }

        // Combat setbacks removed by request EXCEPT for silent aim: only CharSilentAim may punitively
        // teleport (setback) the attacker. Every other combat check still flags and may drop the hit
        // above, but never setbacks the player.
        if (!"CharSilentAim".equals(checkName)) return;

        if (!shouldPunitiveSetback(plugin, confidence)) return;
        if (!plugin.getConfig().getBoolean("combat-mitigation.punitive-setback", true)) return;

        double pushBlocks = plugin.getConfig().getDouble("combat-mitigation.push-blocks", 0.40D);
        Location snap = resolvePunitiveLocation(attacker, target, data, pushBlocks);
        if (snap == null) return;

        long exemptMs = plugin.getConfig().getLong("combat-mitigation.teleport-exempt-ms", 250L);
        MovementEnforcement.executePunitiveTeleport(
                plugin, attacker, data, snap, checkName + (reason == null ? "" : " " + reason), exemptMs);
    }

    public static Location resolvePunitiveLocation(Player attacker, Entity target, PlayerData data,
                                                   double pushBlocks) {
        Location current = attacker != null ? attacker.getLocation() : null;
        if ((current == null || current.getWorld() == null) && data != null) {
            current = data.getLastLoc();
        }
        if (current == null || current.getWorld() == null) return null;

        Location snap = current.clone();
        double push = Math.max(0.05D, pushBlocks);

        if (target != null && target.getWorld() != null && target.getWorld().equals(current.getWorld())) {
            Vector away = current.toVector().subtract(target.getLocation().toVector());
            away.setY(0.0D);
            if (away.lengthSquared() > 1.0E-6) {
                away.normalize().multiply(push);
                snap.add(away);
            } else {
                Vector backward = current.getDirection();
                backward.setY(0.0D);
                if (backward.lengthSquared() > 1.0E-6) {
                    backward.normalize().multiply(-push);
                    snap.add(backward);
                }
            }
        } else if (data != null) {
            Location from = data.getLastMoveFrom();
            if (from != null && from.getWorld() != null && from.getWorld().equals(current.getWorld())) {
                snap = from.clone();
            }
        }

        snap.setYaw(current.getYaw());
        snap.setPitch(current.getPitch());
        return snap;
    }

    private static boolean shouldPunitiveSetback(VezAntiCheat plugin, PrismMitigationPolicy.Confidence confidence) {
        String min = plugin.getConfig().getString("combat-mitigation.min-confidence-for-setback", "moderate");
        return shouldPunitiveSetback(min, confidence);
    }

    static boolean shouldPunitiveSetback(String min, PrismMitigationPolicy.Confidence confidence) {
        if (confidence == null) return false;
        if ("blatant".equalsIgnoreCase(min)) {
            return confidence == PrismMitigationPolicy.Confidence.BLATANT;
        }
        if ("high".equalsIgnoreCase(min)) {
            return confidence == PrismMitigationPolicy.Confidence.HIGH
                    || confidence == PrismMitigationPolicy.Confidence.BLATANT;
        }
        return confidence == PrismMitigationPolicy.Confidence.MODERATE
                || confidence == PrismMitigationPolicy.Confidence.HIGH
                || confidence == PrismMitigationPolicy.Confidence.BLATANT;
    }

    /** Resolve attack target when not passed explicitly. */
    public static Entity resolveAttackTarget(Player attacker, PlayerData data) {
        if (data == null) return null;
        return CombatUtil.resolveTarget(attacker, data.getLastTargetUuid());
    }
}
