package com.colin.vezanticheat.velocity;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.SetbackUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.SpeedUtil;
import com.colin.vezanticheat.utils.VelocityUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns knockback sessions, prediction, evaluation, and setbacks.
 */
public final class VelocityProcessor {

    private final VezAntiCheat plugin;
    private final Map<UUID, VelocitySession> sessions = new ConcurrentHashMap<UUID, VelocitySession>();
    private final Map<UUID, VelocityEvaluationResult> pendingResults = new ConcurrentHashMap<UUID, VelocityEvaluationResult>();
    private final Map<UUID, Long> lastSetbackMs = new ConcurrentHashMap<UUID, Long>();

    public VelocityProcessor(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("velocity-engine.enabled", true);
    }

    public void onVelocity(Player player, PlayerData data, PlayerVelocityEvent event) {
        if (!isEnabled() || player == null || data == null || event == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (PlayerData.bypass(player)) return;

        long now = System.currentTimeMillis();
        Vector velocity = event.getVelocity();
        if (velocity == null) return;

        // CRITICAL FIX: Do NOT multiply by kbScale - PlayerVelocityEvent already contains
        // the FINAL knockback calculated by server (includes sprint, enchants, momentum).
        // Multiplying again causes expected KB to be artificially inflated.
        double expectedH = Math.hypot(velocity.getX(), velocity.getZ());
        double expectedV = Math.max(0.0, velocity.getY());
        double minTrackH = plugin.getConfig().getDouble("velocity-engine.min-track-horizontal", 0.06);
        if (expectedH < minTrackH && expectedV < plugin.getConfig().getDouble("velocity-engine.min-track-vertical", 0.08)) {
            return;
        }

        VelocitySnapshot snapshot = buildSnapshot(player, data, velocity, now);
        long stackWindow = plugin.getConfig().getLong("velocity-engine.stack-window-ms", 120L);

        UUID id = player.getUniqueId();
        VelocitySession existing = sessions.get(id);
        if (existing != null && !existing.evaluated && (now - existing.snapshot.timeMs) <= stackWindow) {
            existing.stackCount++;
            if (velocity.lengthSquared() > existing.snapshot.velocity.lengthSquared()) {
                List<PredictedTick> predicted = VelocityPredictionEngine.simulate(plugin, snapshot);
                sessions.put(id, new VelocitySession(snapshot, predicted, existing.windowMs));
            }
            return;
        }

        long baseWindow = plugin.getConfig().getLong("velocity-engine.evaluation-window-base-ms", 320L);
        double pingFactor = plugin.getConfig().getDouble("velocity-engine.evaluation-window-ping-factor", 0.25D);
        long maxWindow = plugin.getConfig().getLong("velocity-engine.evaluation-window-max-ms", 550L);
        int ping = Math.max(0, PingUtil.getPing(player));
        long windowMs = Math.min(maxWindow, baseWindow + Math.round(ping * pingFactor));

        List<PredictedTick> predicted = VelocityPredictionEngine.simulate(plugin, snapshot);
        VelocitySession session = new VelocitySession(snapshot, predicted, windowMs);
        long jumpWindow = plugin.getConfig().getLong("velocity-engine.jump-reset-window-ms", 220L);
        session.jumpNearVelocity = now - data.getLastJumpTime() <= jumpWindow;
        sessions.put(id, session);

        syncLegacyKbFields(data, player, snapshot, session);
    }

    public void onTick(Player player, PlayerData data, long nowMs) {
        if (!isEnabled() || player == null || data == null) return;

        UUID id = player.getUniqueId();
        VelocitySession session = sessions.get(id);
        if (session == null || session.evaluated) return;

        Location loc = data.getLastLoc();
        if (loc == null) loc = player.getLocation();
        session.observe(loc, nowMs);
        session.lagCover = VelocityExemptions.shouldReduceConfidence(plugin, player, data, nowMs);

        if ((nowMs - session.snapshot.timeMs) >= session.windowMs) {
            evaluate(player, data, session, nowMs);
        }
    }

    public VelocityEvaluationResult getPendingResult(UUID uuid) {
        return uuid == null ? null : pendingResults.get(uuid);
    }

    public void clearPendingResult(UUID uuid) {
        if (uuid != null) pendingResults.remove(uuid);
    }

    public VelocitySession getSession(UUID uuid) {
        return uuid == null ? null : sessions.get(uuid);
    }

    public void removePlayer(UUID uuid) {
        if (uuid == null) return;
        sessions.remove(uuid);
        pendingResults.remove(uuid);
        lastSetbackMs.remove(uuid);
    }

    public boolean trySetback(Player player, PlayerData data, VelocityEvaluationResult result) {
        if (player == null || data == null || result == null) return false;
        if (!plugin.getConfig().getBoolean("velocity-engine.setback.enabled", true)) return false;
        if (result.setbackConfidence < plugin.getConfig()
                .getDouble("velocity-engine.setback.min-confidence", 0.82D)) {
            return false;
        }

        long now = System.currentTimeMillis();

        Location target = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        if (target == null) {
            return false;
        }
        if (!SetbackUtil.executeSetback(plugin, player, data, "velocity")) {
            return false;
        }
        lastSetbackMs.put(player.getUniqueId(), now);
        return true;
    }

    private void evaluate(Player player, PlayerData data, VelocitySession session, long nowMs) {
        if (session.evaluated) return;
        session.evaluated = true;
        session.evaluatedAtMs = nowMs;

        String exempt = VelocityExemptions.evaluate(plugin, player, data, session, nowMs);
        if (exempt != null) {
            VelocityEvaluationResult result = VelocityEvaluationResult.builder()
                    .evaluatedAtMs(nowMs)
                    .exemptReason(exempt)
                    .debugSummary(VelocityDebug.format(session, null) + " exempt=" + exempt)
                    .build();
            publishResult(player.getUniqueId(), session, result);
            sessions.remove(player.getUniqueId());
            return;
        }

        // CRITICAL FIX: Remove kbScale multipliers - snapshot already has final KB values
        double expectedH = session.snapshot.expectedHorizontal;
        double expectedV = session.snapshot.expectedVertical;

        double minExpectedV = plugin.cfg().checkDouble("VelocityA", "minExpectedV", 0.12D);
        double minVerticalRatio = plugin.cfg().checkDouble("VelocityA", "minVerticalRatio", 0.22D);
        double minVertical = Math.max(
                plugin.cfg().checkDouble("VelocityA", "baseMinV", 0.04D),
                expectedV * minVerticalRatio
        );
        boolean zeroVertical = expectedV >= minExpectedV
                && !session.snapshot.verticalBlocked
                && session.verticalGain() < minVertical;

        double minExpectedH = plugin.cfg().checkDouble("VelocityB", "minExpectedH", 0.12D);
        double minHorizontalRatio = plugin.cfg().checkDouble("VelocityB", "minHorizontalRatio", 0.38D);
        double minHorizontal = Math.max(
                plugin.cfg().checkDouble("VelocityB", "baseMinH", 0.04D),
                expectedH * minHorizontalRatio
        );

        // ALWAYS use early-window measurement to avoid false positives from player movement.
        // Total displacement over 320-550ms includes player's own walking/sprinting which
        // inflates actH massively. Early window (first 150ms) captures pure KB response.
        double actualHorizontal = session.maxHorizontal;
        boolean useEarlyWindow = session.earlyTickCount >= 2 && expectedH >= minExpectedH;

        if (useEarlyWindow) {
            actualHorizontal = session.earlyMaxHorizontal;
            // Relax threshold for early window (fewer samples, captures immediate response)
            double earlyWindowMultiplier = 0.65;
            if (session.snapshot.sprinting) {
                earlyWindowMultiplier *= 0.80;
            }
            if (session.snapshot.speedAmp > 0) {
                earlyWindowMultiplier *= (1.0 - (session.snapshot.speedAmp * 0.15));
            }
            minHorizontalRatio *= earlyWindowMultiplier;
            minHorizontal = Math.max(
                    plugin.cfg().checkDouble("VelocityB", "baseMinH", 0.04D),
                    expectedH * minHorizontalRatio
            );
        }

        boolean reducedHorizontal = expectedH >= minExpectedH
                && !session.snapshot.horizontalBlocked
                && actualHorizontal < minHorizontal;

        double reverseDotThreshold = plugin.cfg().checkDouble("VelocityC", "reverseDotThreshold", -0.35D);
        double minReverseSpeed = plugin.cfg().checkDouble("VelocityC", "minReverseSpeed", 0.08D);

        // For reverse KB check, also prefer early window when available
        double reverseCheckHorizontal = (useEarlyWindow && session.earlyMaxHorizontal >= minReverseSpeed)
                ? session.earlyMaxHorizontal : session.maxHorizontal;

        boolean reverseKnockback = expectedH >= minExpectedH
                && reverseCheckHorizontal >= minReverseSpeed
                && session.directionDot < reverseDotThreshold
                && !session.snapshot.horizontalBlocked;

        int minImpossibleTicks = plugin.cfg().checkInt("VelocityD", "minImpossibleTicks", 2);
        double blatantOutside = plugin.cfg().checkDouble("VelocityD", "blatantOutsideBlocks", 0.35D);
        boolean impossiblePosition = session.impossibleTicks >= minImpossibleTicks;
        boolean blatantImpossible = session.maxOutsideDistance >= blatantOutside;

        double setbackConfidence = 0.0;
        if (blatantImpossible) setbackConfidence = 0.95;
        else if (impossiblePosition) setbackConfidence = 0.75;
        else if (zeroVertical && reducedHorizontal) setbackConfidence = 0.85;
        else if (reverseKnockback && reducedHorizontal) setbackConfidence = 0.80;

        // Reduce confidence when player was moving in same direction as KB
        // (their own movement inflates the displacement measurement)
        if (session.directionDot > 0.3 && !reverseKnockback) {
            setbackConfidence *= 0.50;
            reducedHorizontal = false;
        }

        if (session.lagCover) setbackConfidence *= 0.55;

        Location setbackLoc = null;
        if (setbackConfidence > 0.0 && data.getLastLoc() != null) {
            Location raw = VelocityPredictionEngine.closestValidLocation(
                    session.predictedTicks,
                    session.lastObservedTick,
                    data.getLastLoc().getX(),
                    data.getLastLoc().getY(),
                    data.getLastLoc().getZ()
            );
            if (raw != null) {
                setbackLoc = raw.clone();
                setbackLoc.setWorld(data.getLastLoc().getWorld());
                if (player != null) {
                    setbackLoc.setYaw(player.getLocation().getYaw());
                    setbackLoc.setPitch(player.getLocation().getPitch());
                }
            } else if (session.snapshot.startLocation != null) {
                setbackLoc = session.snapshot.startLocation.clone();
            }
        }

        VelocityEvaluationResult result = VelocityEvaluationResult.builder()
                .evaluatedAtMs(nowMs)
                .zeroVertical(zeroVertical)
                .verticalGain(session.verticalGain())
                .expectedVertical(expectedV)
                .minVerticalRequired(minVertical)
                .reducedHorizontal(reducedHorizontal)
                .maxHorizontal(actualHorizontal)  // Use the actual measurement (early or full window)
                .expectedHorizontal(expectedH)
                .minHorizontalRequired(minHorizontal)
                .reverseKnockback(reverseKnockback)
                .directionDot(session.directionDot)
                .projectedHorizontal(session.projectedHorizontal)
                .impossiblePosition(impossiblePosition)
                .impossibleTicks(session.impossibleTicks)
                .maxOutsideDistance(session.maxOutsideDistance)
                .evaluatedTickIndex(session.lastObservedTick)
                .setbackConfidence(setbackConfidence)
                .setbackLocation(setbackLoc)
                .predictedTicks(session.predictedTicks)
                .snapshot(session.snapshot)
                .debugSummary(VelocityDebug.format(session, null))
                .build();

        publishResult(player.getUniqueId(), session, result);
        sessions.remove(player.getUniqueId());
    }

    private void publishResult(UUID id, VelocitySession session, VelocityEvaluationResult result) {
        pendingResults.put(id, result);
        boolean debug = plugin.getConfig().getBoolean("velocity-engine.debug", false);
        if (debug && result != null) {
            plugin.getLogger().info("[Velocity] " + VelocityDebug.format(session, result));
        }
    }

    private VelocitySnapshot buildSnapshot(Player player, PlayerData data, Vector velocity, long now) {
        Location loc = player.getLocation().clone();
        SpeedUtil.Context speedCtx = SpeedUtil.analyze(plugin, player, data);

        boolean onGround = speedCtx != null ? speedCtx.onGround : player.isOnGround();
        boolean inLiquid = speedCtx != null && speedCtx.inLiquid;
        boolean onIce = speedCtx != null && speedCtx.onIce;
        boolean onSlime = speedCtx != null && speedCtx.onSlime;
        boolean weird = speedCtx != null && speedCtx.weirdSurface;
        boolean inWeb = isInWeb(loc);

        Vector dir = VelocityUtil.horizontalDirection(velocity);
        // CRITICAL FIX: No scale multiplier - event velocity is already final
        double expectedH = Math.hypot(velocity.getX(), velocity.getZ());
        double expectedV = Math.max(0.0, velocity.getY());

        boolean horizontalBlocked = dir != null && VelocityUtil.hasHorizontalBlock(loc, dir, Math.max(0.55, expectedH + 0.25));
        boolean verticalBlocked = expectedV > 0.12 && VelocityUtil.hasCeiling(loc, Math.max(0.45, expectedV + 0.20));
        boolean restrictive = VelocityUtil.isRestrictive(loc) || weird;

        return new VelocitySnapshot(
                now,
                velocity,
                loc,
                classifySource(data, now),
                expectedH,
                expectedV,
                onGround,
                player.isSprinting(),
                player.isSneaking(),
                player.isBlocking(),
                inLiquid,
                onIce,
                onSlime,
                inWeb,
                weird,
                potionLevel(player, PotionEffectType.SPEED),
                potionLevel(player, PotionEffectType.SLOW),
                potionLevel(player, PotionEffectType.JUMP),
                Math.max(0, PingUtil.getPing(player)),
                plugin.tps() != null ? plugin.tps().getTps() : 20.0,
                horizontalBlocked,
                verticalBlocked,
                restrictive,
                data.getLastAttackerUuid(),
                data.wasLastAttackerSprinting(),
                data.getLastAttackerVelocity(),
                data.getLastAttackCooldown(),
                data.getLastAttackerKnockbackLevel()
        );
    }

    private VelocitySource classifySource(PlayerData data, long now) {
        long combatMs = plugin.getConfig().getLong("velocity-engine.combat-velocity-ms", 350L);
        if (now - data.getLastDamageTime() <= combatMs) {
            EntityDamageEvent.DamageCause cause = data.getLastDamageCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                return VelocitySource.EXPLOSION;
            }
            if (cause == EntityDamageEvent.DamageCause.PROJECTILE) {
                return VelocitySource.PROJECTILE;
            }
            return VelocitySource.COMBAT;
        }
        return VelocitySource.OTHER;
    }

    private void syncLegacyKbFields(PlayerData data, Player player, VelocitySnapshot snapshot, VelocitySession session) {
        data.setLastVelocity(snapshot.velocity);
        data.setLastVelocityTime(snapshot.timeMs);
        data.setKbWindow(true);
        data.setKbWindowStart(snapshot.timeMs);
        data.setKbStartLoc(snapshot.startLocation == null ? player.getLocation().clone() : snapshot.startLocation.clone());
        data.setKbExpectedH(snapshot.expectedHorizontal);
        data.setKbExpectedV(snapshot.expectedVertical);
        data.setKbMovedH(0.0);
        data.setKbMovedV(0.0);
    }

    private static int potionLevel(Player player, PotionEffectType type) {
        if (player == null || type == null) return 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect != null && effect.getType() == type) return effect.getAmplifier() + 1;
        }
        return 0;
    }

    private static boolean isInWeb(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block block = loc.getBlock();
        return block != null && block.getType() == Material.WEB;
    }
}
