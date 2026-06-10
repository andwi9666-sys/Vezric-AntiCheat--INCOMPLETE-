package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * PacketEvents combat history intake and hit classification pipeline.
 */
public final class CombatPacketHandler {

    private final VezAntiCheat plugin;
    private final CombatAnalyzer analyzer;

    public CombatPacketHandler(VezAntiCheat plugin, CombatAnalyzer analyzer) {
        this.plugin = plugin;
        this.analyzer = analyzer;
    }

    public void onFlyingPacket(Player player, PlayerData data, Location packetLoc, boolean hasPosition,
                               boolean hasLook, boolean onGround, long nowMs) {
        if (shouldSkip(player) || analyzer == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        analyzer.tickDecay(nowMs);

        if (hasLook && packetLoc != null) {
            analyzer.recordRotation(uuid, packetLoc.getYaw(), packetLoc.getPitch(), nowMs);
        }

        if (hasPosition && packetLoc != null && packetLoc.getWorld() != null) {
            analyzer.recordMovement(uuid, packetLoc, onGround, nowMs);
        }
    }

    public boolean onAttack(Player attacker, PlayerData attackerData, Player target, long nowMs) {
        if (shouldSkip(attacker) || target == null || analyzer == null || plugin == null) {
            return false;
        }
        if (shouldSkip(target) || target.getWorld() == null || attacker.getWorld() == null) {
            return false;
        }
        if (!attacker.getWorld().equals(target.getWorld())) {
            return false;
        }

        CombatAnalysisSettings settings = plugin.combatSettings();
        if (settings == null || !settings.isEnabled()) {
            return false;
        }

        try {
            int ping = Math.max(0, PingUtil.getPing(attacker));
            CombatSample sample = CombatSample.fromAttack(
                    analyzer, attacker, attackerData, target, ping, nowMs);
            if (sample == null) {
                return false;
            }

            // Pipeline: analyze → action → optional cancel → record buffer → staff alert.
            boolean skipScoring = CombatFalsePositiveGuard.shouldSkipCombatAnalysis(
                    plugin, attacker, target, nowMs);
            CombatHitResult result = skipScoring
                    ? analyzer.analyzeHitGeometryOnly(sample, plugin)
                    : analyzer.analyzeHit(sample, plugin);
            if (result == null) {
                return false;
            }

            CombatEvidence evidence = analyzer.getEvidence(attacker.getUniqueId());
            boolean lenientImpossible = CombatFalsePositiveGuard.shouldLenientImpossibleRaytrace(
                    plugin, attacker, target, nowMs);
            CombatAction action = analyzer.getRecommendedAction(result, evidence);
            CombatCancelDecision cancelDecision = analyzer.resolveCancellation(
                    result, evidence, lenientImpossible);

            CombatHitResult recordedResult = cancelDecision.shouldCancel()
                    ? CombatHitResult.withReason(result, cancelDecision.getReason())
                    : result;

            if (!skipScoring) {
                analyzer.recordResult(recordedResult);
                analyzer.recordAttack(
                        attacker.getUniqueId(),
                        target.getUniqueId(),
                        nowMs,
                        sample.getAttackerYaw(),
                        sample.getAttackerPitch(),
                        recordedResult.getReachDistance(),
                        recordedResult.getClassification());

                CombatEvidence updatedEvidence = analyzer.getEvidence(attacker.getUniqueId());
                double postBuffer = updatedEvidence == null ? 0.0D : updatedEvidence.getBuffer();

                if ((action == CombatAction.ALERT || action == CombatAction.PUNISH)
                        && !CombatFalsePositiveGuard.shouldSuppressAlerts(plugin)) {
                    CombatStaffAlerter alerter = plugin.combatAlerter();
                    if (alerter != null) {
                        alerter.tryAlert(attacker, recordedResult, action, postBuffer);
                    }
                }
            }

            return cancelDecision.shouldCancel();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[CombatAnalysis] Hit handling failed for "
                            + attacker.getName() + ": " + t.getMessage(),
                    t);
            return false;
        }
    }

    private boolean shouldSkip(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return true;
        }
        if (PlayerData.bypass(player)) {
            return true;
        }
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.getWorld() == null;
    }
}
