package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.MovementPlayer;
import com.colin.vezanticheat.prediction.PredictionProcessor;
import com.colin.vezanticheat.velocity.VelocityCorrectionContext;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocityProcessor;
import com.colin.vezanticheat.velocity.VelocitySession;
import com.colin.vezanticheat.velocity.VelocitySnapshot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.List;

/** Shared velocity-flag enforcement: animated knockback replay. */
public final class VelocityEnforcement {

    private VelocityEnforcement() {}

    public static void onVelocityFlag(VezAntiCheat plugin, Player player, PlayerData data,
                                      VelocityEvaluationResult result, String checkName) {
        if (plugin == null || player == null || data == null) return;
        // SAFETY KILL-SWITCH: anti-knockback corrections (Grim setback OR animated replay) are disabled by
        // default — they were teleporting players on legit knockback. Velocity checks still alert (VL).
        // Re-enable with prediction.setback.enforce: true once verified false-positive free.
        if (!plugin.getConfig().getBoolean("prediction.setback.enforce", false)) return;

        Vector knockback = data.getLastVelocity();
        if (knockback == null || knockback.lengthSquared() < 0.001D) {
            MovementPlayer mp = data.getMovementPlayer();
            if (mp != null && mp.pendingKnockback != null) {
                knockback = mp.pendingKnockback;
            }
        }
        if (result != null && result.snapshot != null && result.snapshot.velocity != null
                && result.snapshot.velocity.lengthSquared() > 0.001D) {
            knockback = result.snapshot.velocity.clone();
        }
        if (knockback == null) return;

        // Unified GrimAC velocity correction: set the player back to the last-known-good anchor and
        // re-apply the expected knockback (as carriedOverride) — the SAME Grim setback the movement checks
        // use, so the ignored knockback is enforced. The legacy animated replay below is opt-out only
        // (set velocity-engine.setback.use-grim-setback: false to fall back to it).
        if (plugin.getConfig().getBoolean("velocity-engine.setback.use-grim-setback", true)) {
            MovementEnforcement.executeSetback(plugin, player, data, "antikb-" + checkName, knockback.clone());
            return;
        }
        if (!isAnimateEnabled(plugin, checkName)) return;

        List<com.colin.vezanticheat.velocity.PredictedTick> predicted = Collections.emptyList();
        VelocitySnapshot snapshot = null;
        if (result != null) {
            if (result.predictedTicks != null && !result.predictedTicks.isEmpty()) {
                predicted = result.predictedTicks;
            }
            snapshot = result.snapshot;
        }

        if ((predicted == null || predicted.isEmpty()) || snapshot == null) {
            VelocityProcessor velocityProcessor = plugin.velocity();
            if (velocityProcessor != null) {
                VelocitySession session = velocityProcessor.getSession(player.getUniqueId());
                if (session != null) {
                    if (snapshot == null) snapshot = session.snapshot;
                    if (predicted == null || predicted.isEmpty()) {
                        predicted = session.predictedTicks;
                    }
                }
            }
        }

        if ((predicted == null || predicted.isEmpty())) {
            PredictionProcessor prediction = plugin.prediction();
            if (prediction != null) {
                VelocitySession active = prediction.getActiveVelocitySession(data);
                if (active != null) {
                    if (snapshot == null) snapshot = active.snapshot;
                    predicted = active.predictedTicks;
                }
            }
        }

        Location start = null;
        if (snapshot != null && snapshot.startLocation != null) {
            start = snapshot.startLocation.clone();
            if (player.getWorld() != null) {
                start.setWorld(player.getWorld());
            }
        }
        if (start == null && result != null && result.setbackLocation != null) {
            start = result.setbackLocation.clone();
        }
        if (start == null) {
            start = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        }

        VelocityCorrectionContext ctx = VelocityCorrectionContext.fromEvaluation(
                result, knockback, predicted, snapshot, checkName);
        if (ctx.startLoc == null && start != null) {
            ctx = new VelocityCorrectionContext(knockback, start, predicted, snapshot, checkName,
                    ctx.setbackConfidence, ctx.expectedHorizontal, ctx.impossiblePosition, ctx.maxOutsideDistance);
        }

        VelocityKnockbackAnimator.applyKnockbackAnimation(plugin, player, data, ctx);
    }

    public static void onVelocityFlagMinimal(VezAntiCheat plugin, Player player, PlayerData data,
                                             Vector knockback, String checkName) {
        if (plugin == null || player == null || data == null) return;
        if (!isAnimateEnabled(plugin, checkName)) return;
        if (knockback == null) {
            knockback = data.getLastVelocity();
        }
        Location start = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        VelocityCorrectionContext ctx = VelocityCorrectionContext.minimal(knockback, start, checkName);
        VelocityKnockbackAnimator.applyKnockbackAnimation(plugin, player, data, ctx);
    }

    private static boolean isAnimateEnabled(VezAntiCheat plugin, String checkName) {
        if (!plugin.getConfig().getBoolean("prediction.setback.antikb-animation-enabled", true)) {
            return false;
        }
        if (checkName != null && plugin.tierCfg() != null) {
            return plugin.tierCfg().checkBoolean(checkName, "animateOnFlag", true);
        }
        return true;
    }
}
