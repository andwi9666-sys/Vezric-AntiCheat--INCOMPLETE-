package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Throttled staff alerts for combat buffer thresholds.
 */
public final class CombatStaffAlerter {

    private static final class AlertState {
        private long lastAlertMs;
        private double bufferAtLastAlert;
    }

    private final VezAntiCheat plugin;
    private final Map<UUID, AlertState> alertStateByPlayer = new HashMap<UUID, AlertState>();

    public CombatStaffAlerter(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean tryAlert(Player attacker, CombatHitResult result, CombatAction action, double postBuffer) {
        if (attacker == null || result == null || plugin == null) {
            return false;
        }
        if (action != CombatAction.ALERT && action != CombatAction.PUNISH) {
            return false;
        }

        CombatAnalysisSettings settings = plugin.combatSettings();
        if (settings == null || !settings.isEnabled() || !settings.isAlertsEnabled()) {
            return false;
        }

        long nowMs = System.currentTimeMillis();
        UUID attackerId = attacker.getUniqueId();
        if (!shouldSendAlert(attackerId, postBuffer, nowMs, settings)) {
            return false;
        }

        String reason = resolveReason(result);
        String message = formatAlert(attacker.getName(), postBuffer, reason, settings);
        broadcastToStaff(message);

        AlertState state = alertStateByPlayer.get(attackerId);
        if (state == null) {
            state = new AlertState();
            alertStateByPlayer.put(attackerId, state);
        }
        state.lastAlertMs = nowMs;
        state.bufferAtLastAlert = postBuffer;

        if (settings.isDebug()) {
            logDebug(attacker, result, action, postBuffer, reason);
        }
        return true;
    }

    boolean shouldSendAlert(UUID attackerId, double postBuffer, long nowMs, CombatAnalysisSettings settings) {
        if (attackerId == null || settings == null) {
            return false;
        }

        AlertState state = alertStateByPlayer.get(attackerId);
        if (state == null) {
            return true;
        }

        long elapsed = nowMs - state.lastAlertMs;
        if (elapsed >= settings.getAlertCooldownMs()) {
            return true;
        }

        double spike = postBuffer - state.bufferAtLastAlert;
        return spike >= settings.getAlertBufferSpike();
    }

    String formatAlert(String playerName, double buffer, String reason, CombatAnalysisSettings settings) {
        String prefix = plugin.getConfig().getString("prefix", "&0&l[PE&7RPLEX&8ION] ");
        String fmt = settings == null ? CombatAnalysisSettings.fromPlugin(plugin).getAlertFormat() : settings.getAlertFormat();
        String resolved = fmt
                .replace("{prefix}", prefix)
                .replace("{player}", playerName == null ? "unknown" : playerName)
                .replace("{buffer}", formatBuffer(buffer))
                .replace("{reason}", reason == null ? "unknown" : reason);
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }

    static String resolveReason(CombatHitResult result) {
        if (result == null) {
            return "unknown";
        }

        List<String> reasons = result.getReasons();
        if (reasons != null) {
            for (String reason : reasons) {
                if (reason == null || reason.isEmpty()) {
                    continue;
                }
                if (isSuspiciousReason(reason, result.getClassification())) {
                    return reason;
                }
            }
            if (!reasons.isEmpty()) {
                String last = reasons.get(reasons.size() - 1);
                if (last != null && !last.isEmpty()) {
                    return last;
                }
            }
        }

        CombatHitClassification classification = result.getClassification();
        return classification == null ? "unknown" : classification.name();
    }

    private static boolean isSuspiciousReason(String reason, CombatHitClassification classification) {
        if (classification != null && classification.isSuspicious()) {
            return true;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("spike")
                || lower.contains("correlation")
                || lower.contains("pre-aim")
                || lower.contains("target switch")
                || lower.contains("los=false")
                || lower.contains("expansion")
                || lower.contains("distribution");
    }

    private void broadcastToStaff(String message) {
        com.colin.vezanticheat.data.PlayerDataManager dataManager = plugin.data();
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff == null || !staff.hasPermission("watchdog.staff")) {
                continue;
            }
            if (dataManager != null) {
                PlayerData data = dataManager.get(staff);
                if (data != null && !data.isFlagsEnabled()) {
                    continue;
                }
            }
            staff.sendMessage(message);
        }
    }

    private void logDebug(Player attacker, CombatHitResult result, CombatAction action,
                          double postBuffer, String reason) {
        Logger logger = plugin.getLogger();
        CombatEvidence evidence = plugin.combat().getEvidence(attacker.getUniqueId());

        double shellRatio = evidence == null ? 0.0D : evidence.getExpansionShellHitRatio();
        double edgeRatio = evidence == null ? 0.0D : evidence.getEdgeHitRatio();
        double centerRatio = evidence == null ? 0.0D : evidence.getCenterLikeHitRatio();

        logger.info("[CombatAnalysis] player=" + attacker.getName()
                + " action=" + action
                + " buffer=" + formatBuffer(postBuffer)
                + " baseScore=" + formatBuffer(result.getBaseScore())
                + " finalScore=" + formatBuffer(result.getFinalScore())
                + " classification=" + (result.getClassification() == null ? "UNKNOWN" : result.getClassification().name())
                + " shellRatio=" + formatPercent(shellRatio)
                + " edgeRatio=" + formatPercent(edgeRatio)
                + " centerRatio=" + formatPercent(centerRatio)
                + " reason=" + reason
                + " reasons=" + result.getReasons());
    }

    private static String formatBuffer(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0D);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId != null) {
            alertStateByPlayer.remove(playerId);
        }
    }

    void recordAlertForTest(UUID playerId, long nowMs, double buffer) {
        AlertState state = alertStateByPlayer.get(playerId);
        if (state == null) {
            state = new AlertState();
            alertStateByPlayer.put(playerId, state);
        }
        state.lastAlertMs = nowMs;
        state.bufferAtLastAlert = buffer;
    }

    String formatAlertForTest(String playerName, double buffer, String reason, CombatAnalysisSettings settings) {
        String fmt = settings == null ? DEFAULT_ALERT_FORMAT : settings.getAlertFormat();
        String resolved = fmt
                .replace("{prefix}", "&0&l[PE&7RPLEX&8ION] ")
                .replace("{player}", playerName == null ? "unknown" : playerName)
                .replace("{buffer}", formatBuffer(buffer))
                .replace("{reason}", reason == null ? "unknown" : reason);
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }

    private static final String DEFAULT_ALERT_FORMAT =
            "{prefix}&e{player} &7failed &cCombatAnalysis &7VL: &f{buffer} &7Reason: &f{reason}";
}
