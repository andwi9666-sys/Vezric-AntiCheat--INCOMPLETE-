package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.MovementEnforcement;
import org.bukkit.entity.Player;

/**
 * Polar-style Prism mitigation: drop invalid packets and setback within ~5ms of flag.
 * Combat player-hit checks delegate to {@link CombatMitigationPolicy}.
 */
public final class PrismMitigationPolicy {

    public enum Confidence {
        LOW, MODERATE, HIGH, BLATANT
    }

    private PrismMitigationPolicy() {}

    public static void apply(VezAntiCheat plugin, Player player, PlayerData data,
                               String checkName, Confidence confidence, String reason) {
        if (plugin == null || player == null || data == null || confidence == null) return;
        if (PlayerData.bypass(player)) return;
        if (confidence == Confidence.LOW) return;

        if (CombatMitigationPolicy.isCombatPlayerHitCheck(plugin, checkName)) {
            CombatMitigationPolicy.apply(plugin, player, data,
                    CombatMitigationPolicy.resolveAttackTarget(player, data),
                    checkName, reason, confidence);
            return;
        }

        String mitigation = plugin.tierCfg().checkString(checkName, "mitigation",
                confidence == Confidence.BLATANT ? "blatant" : "moderate");
        boolean cancelOnFlag = plugin.tierCfg().checkBoolean(checkName, "cancelOnFlag", true);
        boolean setbackOnFlag = plugin.tierCfg().checkBoolean(checkName, "setbackOnFlag",
                confidence == Confidence.BLATANT || confidence == Confidence.HIGH);

        if ("none".equalsIgnoreCase(mitigation) && confidence != Confidence.BLATANT) return;

        if (cancelOnFlag) {
            if (reason != null && (reason.contains("attack") || reason.contains("range")
                    || reason.contains("sight") || reason.contains("hitbox"))) {
                data.setBlockCurrentAttackPacket(true);
                data.setBlockedAttackReason(checkName + " " + reason);
            }
            if (reason != null && reason.contains("dig")) {
                data.setBlockCurrentDigPacket(true);
            }
            if (reason != null && reason.contains("place")) {
                data.setBlockCurrentPlacePacket(true);
            }
        }

        if (setbackOnFlag && (confidence == Confidence.BLATANT
                || confidence == Confidence.HIGH
                || "blatant".equalsIgnoreCase(mitigation))) {
            long delayMs = plugin.getConfig().getLong("prism.mitigation-delay-ms", 5L);
            MovementEnforcement.requestImmediateSetback(plugin, player, data, checkName + " " + reason, delayMs);
        }
    }

    public static Confidence fromInteraction(double confidence) {
        if (confidence >= 0.95D) return Confidence.BLATANT;
        if (confidence >= 0.70D) return Confidence.HIGH;
        if (confidence >= 0.45D) return Confidence.MODERATE;
        return Confidence.LOW;
    }

    public static Confidence fromPattern(double confidence) {
        if (confidence >= 0.85D) return Confidence.HIGH;
        if (confidence >= 0.55D) return Confidence.MODERATE;
        return Confidence.LOW;
    }
}
