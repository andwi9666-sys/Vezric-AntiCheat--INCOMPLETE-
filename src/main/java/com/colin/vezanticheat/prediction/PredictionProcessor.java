package com.colin.vezanticheat.prediction;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.FlyUtil;
import com.colin.vezanticheat.utils.LagProfileUtil;
import com.colin.vezanticheat.utils.MovementContextAnalyzer;
import com.colin.vezanticheat.utils.PhaseUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.SetbackUtil;
import com.colin.vezanticheat.utils.SpeedUtil;
import com.colin.vezanticheat.velocity.PredictedTick;
import com.colin.vezanticheat.velocity.VelocityDebug;
import com.colin.vezanticheat.velocity.VelocityEvaluationResult;
import com.colin.vezanticheat.velocity.VelocityExemptions;
import com.colin.vezanticheat.velocity.VelocityPredictionEngine;
import com.colin.vezanticheat.velocity.VelocitySession;
import com.colin.vezanticheat.velocity.VelocitySnapshot;
import com.colin.vezanticheat.velocity.VelocitySource;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * PredictionProcessor — Hybrid heuristic + simulation movement validator.
 *
 * This is the central movement processing engine that runs for every position packet.
 * It combines two approaches:
 *
 * 1. Heuristic (fast path): Quick threshold checks for obvious violations
 *    - Expected horizontal speed from SpeedUtil (caps based on sprint/potion/surface)
 *    - Expected vertical range from FlyUtil (gravity curve prediction)
 *    - Hover/phase detection from environmental analysis
 *
 * 2. Simulation (accurate path): Full physics simulation via VelocityPredictionEngine
 *    - Explores all legal input branches (W/A/S/D/jump/sprint/sneak)
 *    - Produces a PredictedTick envelope of valid positions
 *    - Compares actual position against envelope
 *
 * The hybrid approach gives us the best of both worlds:
 * - Heuristics are cheap (O(1)) and catch blatant cheats instantly
 * - Simulation is expensive (O(branches)) but catches edge cases heuristics miss
 *
 * Compensation Systems:
 * =====================
 * - Lag compensation: Extra tolerance during packet bursts/gaps
 * - Block update compensation: Tolerance when nearby blocks change
 * - 0.03 packet handling: Position-less flying packets require state replay
 * - Chunk loading: Skip validation when world data is unavailable
 *
 * Velocity Tracking:
 * ==================
 * When the server sends a velocity packet (knockback, explosion):
 * 1. PredictionProcessor.onVelocity() creates a VelocitySession
 * 2. VelocityPredictionEngine.simulate() produces predicted KB trajectory
 * 3. Player movement is compared against trajectory during evaluation window
 * 4. If KB response doesn't match predictions, VelocityPrediction check flags
 *
 * Setback System:
 * ===============
 * When movement is flagged, trySetback() or tryVelocityCorrection() teleports
 * the player back to their last known good position. For anti-KB, the original
 * velocity is re-applied to produce authentic knockback animation.
 */
public final class PredictionProcessor {

    private final VezAntiCheat plugin;

    public PredictionProcessor(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("prediction.enabled", true);
    }

    public void onJoin(Player player, PlayerData data, Location spawn, long nowMs) {
        if (player == null || data == null) return;
        PredictionState state = data.getPredictionState();
        state.resetMotion();
        Location seed = spawn == null ? player.getLocation() : spawn;
        state.setLastKnownGoodLocation(seed, nowMs);
        SetbackUtil.seedValidGroundAnchor(data, seed, nowMs);
        state.setLastSetbackReason(null);
        state.setSetbackPending(false);
        state.setLastResult(null);
        state.setActiveVelocitySession(null);
        state.setLastVelocityResult(null);
    }

    public void onTeleport(Player player, PlayerData data, Location to, long nowMs) {
        onTeleport(player, data, to, nowMs, true);
    }

    public void onTeleport(Player player, PlayerData data, Location to, long nowMs, boolean seedSetbackAnchor) {
        if (player == null || data == null) return;
        PredictionState state = data.getPredictionState();
        Location destination = to == null ? player.getLocation() : to;
        state.resetMotion();
        state.setLastKnownGoodLocation(destination, nowMs);
        state.setSetbackPending(false);
        state.setLastResult(null);
        state.setActiveVelocitySession(null);
        if (seedSetbackAnchor) {
            SetbackUtil.seedValidGroundAnchor(data, destination, nowMs);
        }
    }

    public void onVelocity(Player player, PlayerData data, PlayerVelocityEvent event) {
        if (!isEnabled() || player == null || data == null || event == null) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (PlayerData.bypass(player)) return;

        Vector velocity = event.getVelocity();
        if (velocity == null) return;

        long now = System.currentTimeMillis();
        VelocitySnapshot snapshot = buildVelocitySnapshot(player, data, velocity, now);
        if (snapshot.expectedHorizontal < plugin.getConfig().getDouble("prediction.velocity.min-track-horizontal", 0.06D)
                && snapshot.expectedVertical < plugin.getConfig().getDouble("prediction.velocity.min-track-vertical", 0.08D)) {
            return;
        }

        List<PredictedTick> predicted = VelocityPredictionEngine.simulate(plugin, snapshot);
        long baseWindow = plugin.getConfig().getLong("prediction.velocity.evaluation-window-base-ms", 320L);
        double pingFactor = plugin.getConfig().getDouble("prediction.velocity.evaluation-window-ping-factor", 0.25D);
        long maxWindow = plugin.getConfig().getLong("prediction.velocity.evaluation-window-max-ms", 550L);
        int ping = Math.max(0, PingUtil.getPing(player));
        long windowMs = Math.min(maxWindow, baseWindow + Math.round(ping * pingFactor));

        VelocitySession session = new VelocitySession(snapshot, predicted, windowMs);
        long jumpWindow = plugin.getConfig().getLong("prediction.velocity.jump-reset-window-ms", 220L);
        session.jumpNearVelocity = now - data.getLastJumpTime() <= jumpWindow;
        long attackWindow = plugin.getConfig().getLong("prediction.velocity.attack-reset-window-ms", 175L);
        long blockhitWindow = plugin.getConfig().getLong("prediction.velocity.blockhit-reset-window-ms", 225L);
        session.attackNearVelocity = now - data.getLastUseEntityTime() <= attackWindow;
        session.combatInteractNearVelocity = now - data.getLastCombatInteractTime() <= blockhitWindow;

        PredictionState state = data.getPredictionState();
        state.setActiveVelocitySession(session);
        state.setLastVelocityResult(null);
        data.setLastVelocity(velocity);
        data.setLastVelocityTime(now);
        data.markVelocityExempt(plugin.cfg().velocityExemptMs());
    }

    /** Lightweight velocity-session tick when legacy movement prediction is skipped. */
    public void tickVelocitySession(Player player, PlayerData data, long nowMs) {
        if (!isEnabled() || player == null || data == null) return;
        Location to = data.getLastLoc();
        if (to == null || to.getWorld() == null) return;
        observeVelocitySession(player, data, data.getPredictionState(), to, nowMs);
    }

    public PredictionResult handleMovement(Player player, PlayerData data, boolean clientGround, long nowMs, boolean positionIncluded) {
        if (player == null || data == null) return null;
        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return null;
        if (!from.getWorld().equals(to.getWorld())) return null;

        // Chunk loading validation: skip prediction when chunks aren't loaded
        // If blocks around the player aren't available, collision detection is unreliable
        if (!to.getWorld().isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)
                || !from.getWorld().isChunkLoaded(from.getBlockX() >> 4, from.getBlockZ() >> 4)) {
            PredictionState state2 = data.getPredictionState();
            state2.setLastKnownGoodLocation(to, nowMs);
            return null;
        }

        PredictionState state = data.getPredictionState();
        state.recordSneakState(player.isSneaking());
        state.observePacketGround(clientGround, positionIncluded);
        if (!positionIncluded) {
            state.incrementRecentPositionlessTicks();
        }
        observeVelocitySession(player, data, state, to, nowMs);

        SpeedUtil.Context speed = SpeedUtil.analyze(plugin, player, data);
        FlyUtil.Context fly = FlyUtil.analyze(plugin, player, data);
        PhaseUtil.Context phase = PhaseUtil.analyze(plugin, player, data);
        MovementContextAnalyzer.Context movement = MovementContextAnalyzer.analyze(plugin, player, data);

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.hypot(dx, dz);

        boolean serverGround = isServerGround(from) || isServerGround(to);
        boolean inLiquid = isInLiquid(from) || isInLiquid(to);
        boolean climbable = isClimbable(from) || isClimbable(to);
        boolean inWeb = isWeb(from) || isWeb(to);
        boolean onIce = isOnIce(from) || isOnIce(to);
        boolean onSlime = isOnSlime(from) || isOnSlime(to);
        boolean weirdSurface = isWeirdSurface(from) || isWeirdSurface(to);
        boolean constrained = movement != null && movement.constrained;
        boolean usingItem = isUsingSlowItem(player, data);
        int nearbyBlockUpdates = countRecentNearbyBlockUpdates(data, from, to, nowMs);
        int nearbySupportUpdates = countRecentGroundAffectingBlockUpdates(data, from, to, nowMs);
        int nearbyCollisionUpdates = countRecentCollisionChangingBlockUpdates(data, from, to, nowMs);
        boolean recentBlockUpdate = nearbyBlockUpdates > 0;
        boolean supportUpdate = nearbySupportUpdates > 0;
        int pointThreeTicks = positionIncluded ? state.consumeRecentPositionlessTicks() : 0;
        int hiddenGroundTicks = state.getRecentGroundStateTicks();
        boolean lagCompensated = shouldCompensateForLag(data, nowMs);
        boolean replayCompensated = shouldUseReplayCompensation(pointThreeTicks, hiddenGroundTicks, recentBlockUpdate, lagCompensated);
        CompensationBudget budget = buildCompensationBudget(
                data, nowMs, nearbyBlockUpdates, nearbySupportUpdates, nearbyCollisionUpdates,
                pointThreeTicks, hiddenGroundTicks, lagCompensated, replayCompensated
        );
        boolean worldCompensated = budget.worldCompensated;
        boolean groundUncertain = supportUpdate
                || hiddenGroundTicks > 0
                || (clientGround && pointThreeTicks > 0)
                || state.isHiddenGroundPending()
                || state.isSetbackPending()
                || lagCompensated;
        double hiddenGroundMaxDy = plugin.getConfig().getDouble("prediction.compensation.hidden-ground-max-dy", 0.085D);
        boolean effectiveServerGround = serverGround;
        if (!effectiveServerGround && groundUncertain) {
            boolean gentleVertical = Math.abs(dy) <= hiddenGroundMaxDy;
            if (gentleVertical && (clientGround || state.wasLastResolvedGround() || supportUpdate)) {
                effectiveServerGround = true;
            }
        }
        double compensatedHorizontalExtra = budget.horizontalExtra;
        double compensatedVerticalExtra = budget.verticalExtra;

        double ticks = speed != null ? speed.ticks : Math.max(1.0D, Math.min(4.0D, data.getLastFlyingIntervalMs() / 50.0D));
        double baseExpectedH = 0.33D;
        if (speed != null) {
            baseExpectedH = speed.onGround ? speed.expectedGround : speed.expectedAir;
        }
        double priorMotionH = Math.hypot(state.getLastMotionX(), state.getLastMotionZ());
        priorMotionH = Math.min(priorMotionH, maxLegitCarryHorizontal(speed, effectiveServerGround));
        double carriedH = priorMotionH * (effectiveServerGround ? 0.60D : 0.91D);
        double horizontalUncertainty = 0.0D;
        if (pointThreeTicks > 0) {
            horizontalUncertainty += pointThreeTicks * plugin.getConfig().getDouble("prediction.uncertainty.point-three-horizontal", 0.031D);
        }
        if (player.isSneaking() && state.getConsecutiveSneakTicks() >= 2) {
            horizontalUncertainty += plugin.getConfig().getDouble("prediction.uncertainty.sneak-horizontal", 0.015D);
        }
        if (recentBlockUpdate) {
            horizontalUncertainty += plugin.getConfig().getDouble("prediction.uncertainty.block-update-horizontal", 0.05D);
        }
        double expectedHorizontal = Math.max(baseExpectedH, carriedH + 0.03D) + horizontalUncertainty + compensatedHorizontalExtra;
        if (usingItem) {
            expectedHorizontal = Math.min(expectedHorizontal,
                    (plugin.getConfig().getDouble("prediction.no-slow.max-horizontal", 0.21D) * ticks)
                            + horizontalUncertainty + compensatedHorizontalExtra);
        }
        VelocitySession activeVelocity = state.getActiveVelocitySession();
        if (activeVelocity != null) {
            expectedHorizontal += plugin.getConfig().getDouble("prediction.offset.velocity-extra-horizontal", 0.10D);
        }

        double horizontalThreshold = plugin.getConfig().getDouble("prediction.offset.horizontal-threshold", 0.06D);
        double heuristicHorizontalOffset = Math.max(0.0D, horizontal - expectedHorizontal);

        double priorDy = state.getLastMotionY();
        double predictedDy = effectiveServerGround && dy >= -hiddenGroundMaxDy && dy <= 0.42D
                ? 0.0D
                : FlyUtil.expectedNextDy(priorDy);
        double jumpRise = fly != null ? fly.maxJumpRise : 0.42D;
        double verticalUncertainty = 0.0D;
        if (pointThreeTicks > 0) {
            verticalUncertainty += plugin.getConfig().getDouble("prediction.uncertainty.point-three-vertical", 0.06D);
        }
        if (recentBlockUpdate) {
            verticalUncertainty += plugin.getConfig().getDouble("prediction.uncertainty.block-update-vertical", 0.08D);
        }
        if (player.isSneaking() && state.getConsecutiveSneakTicks() >= 2) {
            verticalUncertainty += plugin.getConfig().getDouble("prediction.uncertainty.sneak-vertical", 0.02D);
        }
        double minExpectedDy = predictedDy - plugin.getConfig().getDouble("prediction.offset.vertical-threshold", 0.08D) - verticalUncertainty - compensatedVerticalExtra;
        double maxExpectedDy = predictedDy + plugin.getConfig().getDouble("prediction.offset.vertical-threshold", 0.08D) + verticalUncertainty + compensatedVerticalExtra;
        if (effectiveServerGround || clientGround || (movement != null && movement.recentJump)) {
            maxExpectedDy = Math.max(maxExpectedDy, jumpRise + 0.04D);
        }
        if (activeVelocity != null && activeVelocity.snapshot != null) {
            maxExpectedDy = Math.max(maxExpectedDy, activeVelocity.snapshot.expectedVertical + 0.10D);
        }
        if (inLiquid || climbable || inWeb || weirdSurface || onSlime) {
            minExpectedDy -= 0.18D;
            maxExpectedDy += 0.18D;
        }

        double heuristicVerticalOffset = 0.0D;
        if (dy < minExpectedDy) {
            heuristicVerticalOffset = minExpectedDy - dy;
        } else if (dy > maxExpectedDy) {
            heuristicVerticalOffset = dy - maxExpectedDy;
        }
        PredictedTick simulatedTick = null;
        List<Location> replayStarts = collectReplayStartLocations(data, from, nowMs, replayCompensated);
        double simulationToleranceH = plugin.getConfig().getDouble("prediction.simulation.horizontal-tolerance", 0.031D)
                + horizontalUncertainty + budget.horizontalTolerance;
        double simulationToleranceV = plugin.getConfig().getDouble("prediction.simulation.vertical-tolerance", 0.060D)
                + verticalUncertainty + budget.verticalTolerance;
        for (Location start : replayStarts) {
            PredictedTick candidate = VelocityPredictionEngine.simulateMovementTick(
                    plugin,
                    buildMovementSimulationSnapshot(player, data, state, start, effectiveServerGround, inLiquid, onIce, onSlime,
                            inWeb, weirdSurface, usingItem, activeVelocity, nowMs),
                    simulationToleranceH,
                    simulationToleranceV
            );
            if (candidate != null) {
                simulatedTick = simulatedTick == null ? candidate : simulatedTick.union(candidate);
            }
        }
        boolean simulated = simulatedTick != null;
        double simulationHorizontalOffset = simulated ? simulatedTick.horizontalDistanceOutside(to.getX(), to.getZ()) : 0.0D;
        double simulationVerticalOffset = simulated ? simulatedTick.verticalDistanceOutside(to.getY()) : 0.0D;
        double positionOffset = simulated ? simulatedTick.distanceOutside(to.getX(), to.getY(), to.getZ()) : Math.sqrt(
                (heuristicHorizontalOffset * heuristicHorizontalOffset) + (heuristicVerticalOffset * heuristicVerticalOffset)
        );
        if (simulated) {
            expectedHorizontal = Math.max(expectedHorizontal, simulatedTick.maxHorizontalDistanceFrom(from.getX(), from.getZ()));
            minExpectedDy = Math.min(minExpectedDy, simulatedTick.minY - from.getY());
            maxExpectedDy = Math.max(maxExpectedDy, simulatedTick.maxY - from.getY());
        }

        double horizontalOffset = simulated ? simulationHorizontalOffset : heuristicHorizontalOffset;
        double verticalOffset = simulated ? simulationVerticalOffset : heuristicVerticalOffset;
        boolean horizontalViolation = movement != null && movement.isSpeedUsable()
                && !data.isTeleportExempt()
                && !data.isVelocityExempt()
                && !data.isPotionExempt()
                && !SpeedUtil.isRecentExplosion(plugin, data, nowMs)
                && !inLiquid
                && !weirdSurface
                && !onSlime
                && (simulated
                ? simulationHorizontalOffset > plugin.getConfig().getDouble("prediction.simulation.horizontal-outside-threshold", 0.035D) + compensatedHorizontalExtra
                : horizontal > expectedHorizontal + horizontalThreshold);
        boolean verticalViolation = movement != null && movement.isFlightUsable()
                && !data.isTeleportExempt()
                && !data.isVelocityExempt()
                && !data.isPotionExempt()
                && !SpeedUtil.isRecentExplosion(plugin, data, nowMs)
                && !inLiquid
                && !climbable
                && !inWeb
                && !weirdSurface
                && !onSlime
                && (simulated
                ? simulationVerticalOffset > plugin.getConfig().getDouble("prediction.simulation.vertical-outside-threshold", 0.055D) + compensatedVerticalExtra
                : verticalOffset > plugin.getConfig().getDouble("prediction.offset.vertical-threshold", 0.08D));

        boolean hoverViolation = fly != null
                && movement != null
                && movement.isFlightUsable()
                && !data.isPotionExempt()
                && !SpeedUtil.isRecentExplosion(plugin, data, nowMs)
                && !fly.onGround
                && !effectiveServerGround
                && fly.airBelow
                && !fly.lowCeiling
                && Math.abs(dy) <= plugin.getConfig().getDouble("prediction.fly.hover-dy", 0.0045D)
                && !data.isVelocityExempt()
                && !groundUncertain;

        int fromSolid = phase == null ? 0 : phase.fromSolid;
        int toSolid = phase == null ? 0 : phase.toSolid;
        int pathSolid = phase == null ? 0 : phase.pathSolid;
        boolean phaseViolation = phase != null
                && movement != null
                && movement.isClean()
                && !phase.inLiquid
                && !phase.climbable
                && !phase.inWeb
                && !phase.onSlime
                && !phase.weirdSurface
                && !worldCompensated
                && !groundUncertain
                && ((toSolid >= plugin.getConfig().getInt("prediction.phase.min-solid-overlaps", 3) && fromSolid == 0)
                || pathSolid >= plugin.getConfig().getInt("prediction.phase.min-path-solids", 2));

        double timerDebt = updateTimerDebt(player, data, nowMs);
        boolean timerViolation = timerDebt >= plugin.getConfig().getDouble("prediction.timer.min-debt-ms", 120.0D)
                && data.getLastFlyingIntervalMs() <= plugin.getConfig().getLong("prediction.timer.max-fast-interval-ms", 42L);

        boolean cleanMovement = movement != null && movement.isClean();
        String debug = "h=" + r(horizontal)
                + "/" + r(expectedHorizontal)
                + " dy=" + r(dy)
                + " range=[" + r(minExpectedDy) + "," + r(maxExpectedDy) + "]"
                + " sim=" + simulated
                + " comp=" + r(budget.score)
                + " posOff=" + r(positionOffset)
                + " hOff=" + r(horizontalOffset)
                + " vOff=" + r(verticalOffset)
                + " pos=" + positionIncluded
                + " p3=" + pointThreeTicks
                + " hg=" + hiddenGroundTicks
                + " blk=" + nearbyBlockUpdates
                + " sup=" + nearbySupportUpdates
                + " col=" + nearbyCollisionUpdates
                + " lag=" + lagCompensated
                + " rep=" + replayCompensated
                + " cg=" + clientGround
                + " sg=" + serverGround
                + " eg=" + effectiveServerGround
                + " use=" + usingItem
                + " liquid=" + inLiquid
                + " solid=" + toSolid
                + " debt=" + r(timerDebt)
                + (movement == null ? "" : " " + movement.debugSummary());

        PredictionResult result = new PredictionResult(
                nowMs,
                from,
                to,
                clientGround,
                serverGround,
                effectiveServerGround,
                usingItem,
                inLiquid,
                climbable,
                inWeb,
                onIce,
                onSlime,
                weirdSurface,
                constrained,
                cleanMovement,
                positionIncluded,
                pointThreeTicks,
                hiddenGroundTicks,
                recentBlockUpdate,
                supportUpdate,
                worldCompensated,
                groundUncertain,
                lagCompensated,
                replayCompensated,
                simulated,
                dx,
                dy,
                dz,
                horizontal,
                expectedHorizontal,
                horizontalUncertainty,
                horizontalOffset,
                minExpectedDy,
                maxExpectedDy,
                verticalUncertainty,
                verticalOffset,
                positionOffset,
                budget.score,
                horizontalViolation,
                verticalViolation,
                hoverViolation,
                phaseViolation,
                fromSolid,
                toSolid,
                pathSolid,
                timerDebt,
                timerViolation,
                debug
        );

        state.setLastResult(result);
        state.resolveGround(effectiveServerGround);
        if (!horizontalViolation && !verticalViolation && !phaseViolation && !data.isTeleportExempt()) {
            state.setLastKnownGoodLocation(to, nowMs);
            state.setSetbackPending(false);
        }

        // Store observed motion (raw packet delta, never clamped) for accurate friction carry
        state.setLastObservedMotionX(dx);
        state.setLastObservedMotionY(dy);
        state.setLastObservedMotionZ(dz);

        // Store validated motion (clamped on violation) for setback consistency
        Vector clampedCarry = clampObservedMotionForCarry(dx, dy, dz, expectedHorizontal, minExpectedDy, maxExpectedDy,
                horizontalViolation, verticalViolation);
        state.setLastMotionX(clampedCarry.getX());
        state.setLastMotionY(clampedCarry.getY());
        state.setLastMotionZ(clampedCarry.getZ());
        state.setLastMovementTimeMs(nowMs);
        return result;
    }

    public PredictionResult handleMovement(Player player, PlayerData data, boolean clientGround, long nowMs) {
        return handleMovement(player, data, clientGround, nowMs, true);
    }

    public PredictionResult getLastResult(PlayerData data) {
        return data == null ? null : data.getPredictionState().getLastResult();
    }

    public VelocityEvaluationResult getLastVelocityResult(PlayerData data) {
        return data == null ? null : data.getPredictionState().getLastVelocityResult();
    }

    public VelocitySession getActiveVelocitySession(PlayerData data) {
        return data == null ? null : data.getPredictionState().getActiveVelocitySession();
    }

    public boolean trySetback(Player player, PlayerData data, String reason) {
        if (player == null || data == null) return false;
        return trySetback(player, data, resolveSetbackTarget(player, data), reason);
    }

    /**
     * Corrects anti-knockback by replaying the original velocity packet.
     *
     * Strategy: Teleport the player back to where they were when KB was applied,
     * then re-apply the ORIGINAL knockback velocity. This produces authentic
     * knockback animation visible to both the cheater and opponents:
     *
     * - The cheater's client receives a velocity packet → renders natural KB motion
     * - Opponents see the server move the player → looks identical to normal KB
     * - No weird multi-step pushes or glitchy corrections
     *
     * The result is indistinguishable from legitimate knockback on all screens.
     */
    public boolean tryVelocityCorrection(Player player, PlayerData data, VelocityEvaluationResult result) {
        if (!isEnabled() || player == null || data == null || result == null) return false;
        if (PlayerData.bypass(player)) return false;

        com.colin.vezanticheat.utils.VelocityEnforcement.onVelocityFlag(
                plugin, player, data, result, "PredictionVelocity");
        return true;
    }

    public boolean trySetback(Player player, PlayerData data, Location target, String reason) {
        if (!isEnabled() || player == null || data == null) return false;
        if (!plugin.getConfig().getBoolean("prediction.setback.enabled", true)) return false;
        if (PlayerData.bypass(player)) return false;

        Location resolved = SetbackUtil.resolveSetbackTarget(plugin, player, data);
        if (resolved == null) {
            return false;
        }

        PredictionState state = data.getPredictionState();
        long now = System.currentTimeMillis();

        state.invalidateVelocityCorrection();
        if (!SetbackUtil.executeSetback(plugin, player, data, reason)) {
            return false;
        }

        Location setback = resolved.clone();
        setback.setWorld(player.getWorld());
        setback.setYaw(player.getLocation().getYaw());
        setback.setPitch(player.getLocation().getPitch());

        state.setLastSetbackMs(now);
        state.setLastSetbackReason(reason);
        onTeleport(player, data, setback, now, false);
        return true;
    }

    private Location resolveSetbackTarget(Player player, PlayerData data) {
        return SetbackUtil.resolveSetbackTarget(plugin, player, data);
    }

    private boolean isSameWorld(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        return a.getWorld().equals(b.getWorld());
    }

    private boolean shouldAnimateAntikbCorrection(Player player, VelocityEvaluationResult result, Location target) {
        if (player == null || result == null || target == null) return false;
        Location live = player.getLocation();
        if (live == null || live.getWorld() == null || target.getWorld() == null
                || !live.getWorld().equals(target.getWorld())) {
            return false;
        }

        double dx = target.getX() - live.getX();
        double dy = target.getY() - live.getY();
        double dz = target.getZ() - live.getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        double maxHorizontalDistance = plugin.getConfig()
                .getDouble("prediction.setback.antikb-animation-max-distance", 1.70D);
        double maxVerticalDistance = plugin.getConfig()
                .getDouble("prediction.setback.antikb-animation-max-vertical-distance", 0.85D);
        double hardTeleportConfidence = plugin.getConfig()
                .getDouble("prediction.setback.antikb-hard-teleport-confidence", 0.93D);
        double hardTeleportOutside = plugin.getConfig()
                .getDouble("prediction.setback.antikb-hard-teleport-outside", 0.70D);

        if (horizontalDistance > maxHorizontalDistance || Math.abs(dy) > maxVerticalDistance) {
            return false;
        }
        if (result.setbackConfidence >= hardTeleportConfidence) {
            return false;
        }
        return !(result.impossiblePosition && result.maxOutsideDistance >= hardTeleportOutside);
    }

    private Vector clampCorrectionVelocity(Vector step, double maxHorizontal, double maxVertical) {
        if (step == null) return new Vector(0.0D, 0.0D, 0.0D);
        double horizontal = Math.hypot(step.getX(), step.getZ());
        if (horizontal > maxHorizontal && horizontal > 1.0E-6D) {
            double scale = maxHorizontal / horizontal;
            step.setX(step.getX() * scale);
            step.setZ(step.getZ() * scale);
        }
        step.setY(Math.max(-maxVertical, Math.min(maxVertical, step.getY())));
        return step;
    }

    private void finishVelocityCorrection(Player player, PlayerData data, Location target, double finalSnapDistance) {
        if (player == null || data == null) return;
        PredictionState state = data.getPredictionState();
        Location live = player.getLocation();
        if (target != null && live != null && live.getWorld() != null && target.getWorld() != null
                && live.getWorld().equals(target.getWorld())
                && live.distanceSquared(target) > (finalSnapDistance * finalSnapDistance)) {
            try {
                player.teleport(target);
                onTeleport(player, data, target, System.currentTimeMillis(), false);
            } catch (Throwable ignored) {
                state.setSetbackPending(false);
            }
            return;
        }
        state.setSetbackPending(false);
        if (live != null) {
            state.setLastKnownGoodLocation(live, System.currentTimeMillis());
        }
    }

    private void observeVelocitySession(Player player, PlayerData data, PredictionState state, Location to, long nowMs) {
        VelocitySession session = state.getActiveVelocitySession();
        if (session == null || session.evaluated) return;

        session.observe(to, nowMs);
        session.lagCover = VelocityExemptions.shouldReduceConfidence(plugin, player, data, nowMs);
        if ((nowMs - session.snapshot.timeMs) < session.windowMs) {
            return;
        }

        VelocityEvaluationResult result = evaluateVelocity(player, data, session, nowMs);
        state.setLastVelocityResult(result);
        state.setActiveVelocitySession(null);
    }

    private VelocityEvaluationResult evaluateVelocity(Player player, PlayerData data, VelocitySession session, long nowMs) {
        session.evaluated = true;
        session.evaluatedAtMs = nowMs;

        String exempt = VelocityExemptions.evaluate(plugin, player, data, session, nowMs);
        if (exempt != null) {
            return VelocityEvaluationResult.builder()
                    .evaluatedAtMs(nowMs)
                    .exemptReason(exempt)
                    .debugSummary(VelocityDebug.format(session, null) + " exempt=" + exempt)
                    .build();
        }

        double expectedH = session.snapshot.expectedHorizontal;
        double expectedV = session.snapshot.expectedVertical;

        double minVerticalRatio = plugin.getConfig().getDouble("prediction.velocity.min-vertical-ratio", 0.22D);
        double minVertical = Math.max(
                plugin.getConfig().getDouble("prediction.velocity.base-min-vertical", 0.04D),
                expectedV * minVerticalRatio
        );
        boolean zeroVertical = expectedV >= plugin.getConfig().getDouble("prediction.velocity.min-expected-vertical", 0.12D)
                && !session.snapshot.verticalBlocked
                && session.verticalGain() < minVertical;

        double minHorizontalRatio = plugin.getConfig().getDouble("prediction.velocity.min-horizontal-ratio", 0.38D);
        double minHorizontal = Math.max(
                plugin.getConfig().getDouble("prediction.velocity.base-min-horizontal", 0.04D),
                expectedH * minHorizontalRatio
        );
        if (session.attackNearVelocity) {
            minHorizontal *= plugin.getConfig().getDouble("prediction.velocity.attack-reset-horizontal-factor", 0.88D);
        }
        if (session.combatInteractNearVelocity) {
            minHorizontal *= plugin.getConfig().getDouble("prediction.velocity.blockhit-reset-horizontal-factor", 0.82D);
        }
        if (session.attackNearVelocity && session.combatInteractNearVelocity) {
            minHorizontal *= plugin.getConfig().getDouble("prediction.velocity.combined-reset-horizontal-factor", 0.94D);
        }
        minHorizontal = Math.max(
                plugin.getConfig().getDouble("prediction.velocity.base-min-horizontal", 0.04D),
                minHorizontal
        );
        boolean reducedHorizontal = expectedH >= plugin.getConfig().getDouble("prediction.velocity.min-expected-horizontal", 0.12D)
                && !session.snapshot.horizontalBlocked
                && session.maxHorizontal < minHorizontal;

        double minProjectedRatio = plugin.getConfig().getDouble("prediction.velocity.min-projected-ratio", 0.24D);
        double minProjected = Math.max(
                plugin.getConfig().getDouble("prediction.velocity.base-min-projected", 0.03D),
                expectedH * minProjectedRatio
        );
        if (session.attackNearVelocity) {
            minProjected *= plugin.getConfig().getDouble("prediction.velocity.attack-reset-projected-factor", 0.90D);
        }
        if (session.combatInteractNearVelocity) {
            minProjected *= plugin.getConfig().getDouble("prediction.velocity.blockhit-reset-projected-factor", 0.84D);
        }
        if (session.attackNearVelocity && session.combatInteractNearVelocity) {
            minProjected *= plugin.getConfig().getDouble("prediction.velocity.combined-reset-projected-factor", 0.94D);
        }
        minProjected = Math.max(
                plugin.getConfig().getDouble("prediction.velocity.base-min-projected", 0.03D),
                minProjected
        );
        boolean weakForwardResponse = expectedH >= plugin.getConfig().getDouble("prediction.velocity.min-expected-horizontal", 0.12D)
                && !session.snapshot.horizontalBlocked
                && !reducedHorizontal
                && session.maxHorizontal >= Math.max(minHorizontal,
                plugin.getConfig().getDouble("prediction.velocity.min-forward-check-horizontal", 0.09D))
                && session.projectedHorizontal < minProjected;

        boolean reverseKnockback = expectedH >= plugin.getConfig().getDouble("prediction.velocity.min-expected-horizontal", 0.12D)
                && session.maxHorizontal >= plugin.getConfig().getDouble("prediction.velocity.min-reverse-speed", 0.08D)
                && session.directionDot < plugin.getConfig().getDouble("prediction.velocity.reverse-dot-threshold", -0.35D)
                && !session.snapshot.horizontalBlocked;

        boolean impossiblePosition = session.impossibleTicks >= plugin.getConfig().getInt("prediction.velocity.min-impossible-ticks", 2);
        double setbackConfidence = 0.0D;
        if (impossiblePosition && session.maxOutsideDistance >= plugin.getConfig().getDouble("prediction.velocity.blatant-outside-blocks", 0.35D)) {
            setbackConfidence = 0.95D;
        } else if (impossiblePosition) {
            setbackConfidence = 0.76D;
        } else if (zeroVertical && reducedHorizontal) {
            setbackConfidence = 0.84D;
        } else if (weakForwardResponse && reducedHorizontal) {
            setbackConfidence = 0.83D;
        } else if (weakForwardResponse) {
            setbackConfidence = 0.72D;
        } else if (reverseKnockback && reducedHorizontal) {
            setbackConfidence = 0.80D;
        }
        if (session.hasCombatInputWindow() && !impossiblePosition) {
            setbackConfidence *= plugin.getConfig().getDouble("prediction.velocity.combat-input-setback-factor", 0.78D);
        }
        if (session.lagCover) {
            setbackConfidence *= 0.55D;
        }

        Location setbackLocation = null;
        if (setbackConfidence > 0.0D) {
            Location observed = data.getLastLoc();
            if (observed != null) {
                Location predicted = VelocityPredictionEngine.closestValidLocation(
                        session.predictedTicks,
                        session.lastObservedTick,
                        observed.getX(),
                        observed.getY(),
                        observed.getZ()
                );
                if (predicted != null) {
                    predicted.setWorld(observed.getWorld());
                    predicted.setYaw(player.getLocation().getYaw());
                    predicted.setPitch(player.getLocation().getPitch());
                    setbackLocation = predicted;
                }
            }

            if (setbackLocation == null) {
                PredictionState state = data.getPredictionState();
                setbackLocation = state.getLastKnownGoodLocation();
                if (setbackLocation == null && session.snapshot.startLocation != null) {
                    setbackLocation = session.snapshot.startLocation.clone();
                }
            }
        }

        return VelocityEvaluationResult.builder()
                .evaluatedAtMs(nowMs)
                .zeroVertical(zeroVertical)
                .verticalGain(session.verticalGain())
                .expectedVertical(expectedV)
                .minVerticalRequired(minVertical)
                .reducedHorizontal(reducedHorizontal)
                .maxHorizontal(session.maxHorizontal)
                .expectedHorizontal(expectedH)
                .minHorizontalRequired(minHorizontal)
                .weakForwardResponse(weakForwardResponse)
                .minProjectedRequired(minProjected)
                .reverseKnockback(reverseKnockback)
                .directionDot(session.directionDot)
                .projectedHorizontal(session.projectedHorizontal)
                .impossiblePosition(impossiblePosition)
                .impossibleTicks(session.impossibleTicks)
                .maxOutsideDistance(session.maxOutsideDistance)
                .evaluatedTickIndex(session.lastObservedTick)
                .setbackConfidence(setbackConfidence)
                .setbackLocation(setbackLocation)
                .predictedTicks(session.predictedTicks)
                .snapshot(session.snapshot)
                .debugSummary(VelocityDebug.format(session, null)
                        + " atkNear=" + session.attackNearVelocity
                        + " bhNear=" + session.combatInteractNearVelocity)
                .build();
    }

    private double updateTimerDebt(Player player, PlayerData data, long nowMs) {
        if (data.isTeleportExempt() || data.isVelocityExempt() || data.isBlockStateExempt()) {
            data.setTimerDebtMs(0L);
            data.setTimerBalanceStart(0L);
            return 0.0D;
        }

        long interval = data.getLastFlyingIntervalMs();
        if (interval <= 0L) {
            return data.getTimerDebtMs();
        }
        long focusResetGap = plugin.getConfig().getLong("prediction.timer.focus-reset-gap-ms", 225L);
        if (interval >= focusResetGap
                || LagProfileUtil.activeScore(plugin, player, data, nowMs)
                >= plugin.getConfig().getDouble("prediction.timer.focus-reset-lag-score", 0.9D)) {
            data.setTimerDebtMs(0L);
            data.setTimerBalanceStart(nowMs);
            return 0.0D;
        }
        long expected = 50L;
        long credit = Math.max(0L, expected - interval);
        long debt = Math.max(0L, data.getTimerDebtMs() + credit - Math.max(0L, interval - expected));
        long maxDebt = plugin.getConfig().getLong("prediction.timer.max-tracked-debt-ms", 450L);
        debt = Math.min(maxDebt, debt);

        if (data.getTimerBalanceStart() == 0L || interval > plugin.getConfig().getLong("prediction.timer.reset-gap-ms", 160L)) {
            data.setTimerBalanceStart(nowMs);
            data.setTimerDebtMs(0L);
            return 0.0D;
        }

        data.setTimerDebtMs(debt);
        return debt;
    }

    private VelocitySnapshot buildVelocitySnapshot(Player player, PlayerData data, Vector velocity, long nowMs) {
        Location loc = player.getLocation().clone();
        SpeedUtil.Context speedCtx = SpeedUtil.analyze(plugin, player, data);
        boolean onGround = speedCtx != null && speedCtx.onGround;
        boolean inLiquid = speedCtx != null && speedCtx.inLiquid;
        boolean onIce = speedCtx != null && speedCtx.onIce;
        boolean onSlime = speedCtx != null && speedCtx.onSlime;
        boolean weird = speedCtx != null && speedCtx.weirdSurface;

        // Phase 2: Merge explosion velocity if available
        Vector explosionVel = data.getLastExplosionVelocity();
        long explosionMergeWindowMs = plugin.getConfig().getLong("prediction.explosion.velocityMergeWindowMs", 100L);
        if (explosionVel != null && (nowMs - data.getLastDamageTime()) <= explosionMergeWindowMs) {
            velocity = velocity.clone().add(explosionVel);
            data.setLastExplosionVelocity(null); // Consume explosion velocity
        }

        // CRITICAL FIX: No scale multiplier - PlayerVelocityEvent velocity is already final
        double expectedH = Math.hypot(velocity.getX(), velocity.getZ());
        double expectedV = Math.max(0.0D, velocity.getY());

        return new VelocitySnapshot(
                nowMs,
                velocity,
                loc,
                classifyVelocitySource(data, nowMs),
                expectedH,
                expectedV,
                onGround,
                player.isSprinting(),
                player.isSneaking(),
                player.isBlocking(),
                inLiquid,
                onIce,
                onSlime,
                isWeb(loc),
                weird,
                potionLevel(player, PotionEffectType.SPEED),
                potionLevel(player, PotionEffectType.SLOW),
                potionLevel(player, PotionEffectType.JUMP),
                Math.max(0, PingUtil.getPing(player)),
                plugin.tps() != null ? plugin.tps().getTps() : 20.0D,
                false,
                false,
                weird || player.isSneaking() || player.isBlocking(),
                data.getLastAttackerUuid(),
                data.wasLastAttackerSprinting(),
                data.getLastAttackerVelocity(),
                data.getLastAttackCooldown(),
                data.getLastAttackerKnockbackLevel()
        );
    }

    private VelocitySnapshot buildMovementSimulationSnapshot(Player player, PlayerData data, PredictionState state, Location from,
                                                            boolean onGround, boolean inLiquid, boolean onIce, boolean onSlime,
                                                            boolean inWeb, boolean weirdSurface, boolean usingItem,
                                                            VelocitySession activeVelocity, long nowMs) {
        if (player == null || data == null || state == null || from == null) return null;

        double carryFactor = onGround ? 0.60D : 0.91D;
        SpeedUtil.Context speed = SpeedUtil.analyze(plugin, player, data);
        FlyUtil.Context fly = FlyUtil.analyze(plugin, player, data);
        double clampedCarryHorizontal = maxLegitCarryHorizontal(speed, onGround);
        double clampedCarryVertical = clampLegitCarryVertical(state.getLastObservedMotionY(), fly, onGround);
        Vector carried = clampHorizontalMagnitude(new Vector(
                state.getLastObservedMotionX() * carryFactor,
                FlyUtil.expectedNextDy(clampedCarryVertical),
                state.getLastObservedMotionZ() * carryFactor
        ), clampedCarryHorizontal);

        long velocityCarryMs = plugin.getConfig().getLong("prediction.simulation.velocity-carry-ms", 175L);
        if (activeVelocity != null && activeVelocity.snapshot != null && (nowMs - activeVelocity.snapshot.timeMs) <= velocityCarryMs) {
            carried.add(activeVelocity.snapshot.velocity);
        }

        double expectedH = Math.hypot(carried.getX(), carried.getZ());
        double expectedV = Math.max(0.0D, carried.getY());
        return new VelocitySnapshot(
                nowMs,
                carried,
                from,
                VelocitySource.OTHER,
                expectedH,
                expectedV,
                onGround,
                player.isSprinting(),
                player.isSneaking(),
                usingItem,
                inLiquid,
                onIce,
                onSlime,
                inWeb,
                weirdSurface,
                potionLevel(player, PotionEffectType.SPEED),
                Math.max(potionLevel(player, PotionEffectType.SLOW), usingItem ? 1 : 0),
                potionLevel(player, PotionEffectType.JUMP),
                Math.max(0, PingUtil.getPing(player)),
                plugin.tps() != null ? plugin.tps().getTps() : 20.0D,
                false,
                false,
                weirdSurface || player.isSneaking() || usingItem,
                null, // No attacker for movement simulation
                false,
                null,
                1.0,
                0
        );
    }

    private VelocitySource classifyVelocitySource(PlayerData data, long nowMs) {
        long combatMs = plugin.getConfig().getLong("prediction.velocity.combat-velocity-ms", 350L);
        if ((nowMs - data.getLastDamageTime()) > combatMs) {
            return VelocitySource.OTHER;
        }
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

    private boolean isUsingSlowItem(Player player, PlayerData data) {
        if (player == null || data == null) return false;
        ItemStack hand = player.getItemInHand();
        if (hand == null) return false;
        Material type = hand.getType();
        return player.isBlocking()
                || (type == Material.BOW && data.getBowPullStart() > 0L)
                || (type != null && type.isEdible() && data.getLastEatStart() > 0L);
    }

    private static int potionLevel(Player player, PotionEffectType type) {
        if (player == null || type == null) return 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect != null && effect.getType() == type) {
                return effect.getAmplifier() + 1;
            }
        }
        return 0;
    }

    private Vector clampObservedMotionForCarry(double dx, double dy, double dz,
                                               double expectedHorizontal, double minExpectedDy, double maxExpectedDy,
                                               boolean horizontalViolation, boolean verticalViolation) {
        Vector clamped = new Vector(dx, dy, dz);
        if (horizontalViolation) {
            clamped = clampHorizontalMagnitude(clamped, Math.max(0.0D, expectedHorizontal));
        }
        if (verticalViolation) {
            clamped.setY(Math.max(minExpectedDy, Math.min(maxExpectedDy, dy)));
        }
        return clamped;
    }

    private Vector clampHorizontalMagnitude(Vector vector, double maxHorizontal) {
        if (vector == null) return new Vector(0.0D, 0.0D, 0.0D);
        double horizontal = Math.hypot(vector.getX(), vector.getZ());
        if (horizontal > maxHorizontal && horizontal > 1.0E-6D) {
            double scale = maxHorizontal / horizontal;
            vector.setX(vector.getX() * scale);
            vector.setZ(vector.getZ() * scale);
        }
        return vector;
    }

    private double maxLegitCarryHorizontal(SpeedUtil.Context speed, boolean onGround) {
        double base = speed == null
                ? (onGround ? 0.34D : 0.36D)
                : (onGround ? speed.expectedGround : speed.expectedAir);
        double extra = plugin.getConfig().getDouble("prediction.simulation.carry-horizontal-extra", 0.02D);
        if (speed != null && speed.recentJump) {
            extra += plugin.getConfig().getDouble("prediction.simulation.jump-carry-horizontal-extra", 0.015D);
        }
        return Math.max(0.0D, base + extra);
    }

    private double clampLegitCarryVertical(double rawVertical, FlyUtil.Context fly, boolean onGround) {
        if (onGround) {
            return Math.max(-0.08D, Math.min(0.0D, rawVertical));
        }
        double maxVertical = (fly == null ? 0.42D : fly.maxJumpRise)
                + plugin.getConfig().getDouble("prediction.simulation.carry-vertical-extra", 0.04D);
        double minVertical = plugin.getConfig().getDouble("prediction.simulation.carry-vertical-min", -0.98D);
        return Math.max(minVertical, Math.min(maxVertical, rawVertical));
    }

    private boolean isServerGround(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (double ox = -0.3D; ox <= 0.3D; ox += 0.3D) {
            for (double oz = -0.3D; oz <= 0.3D; oz += 0.3D) {
                Material below = loc.clone().add(ox, -0.1D, oz).getBlock().getType();
                if (below.isSolid() || below.name().contains("FENCE") || below.name().contains("WALL")
                        || below.name().contains("STEP") || below.name().contains("STAIRS")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInLiquid(Location loc) {
        if (loc == null) return false;
        Material type = loc.getBlock().getType();
        return type == Material.WATER || type == Material.STATIONARY_WATER
                || type == Material.LAVA || type == Material.STATIONARY_LAVA;
    }

    private boolean isClimbable(Location loc) {
        if (loc == null) return false;
        Material type = loc.getBlock().getType();
        return type == Material.LADDER || type == Material.VINE;
    }

    private boolean isWeb(Location loc) {
        return loc != null && loc.getBlock().getType() == Material.WEB;
    }

    private boolean isOnIce(Location loc) {
        if (loc == null) return false;
        Material type = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return type == Material.ICE || type == Material.PACKED_ICE;
    }

    private boolean isOnSlime(Location loc) {
        return loc != null && loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.SLIME_BLOCK;
    }

    private boolean isWeirdSurface(Location loc) {
        if (loc == null) return false;
        Material type = loc.clone().subtract(0, 1, 0).getBlock().getType();
        String name = type.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS") || type == Material.SOUL_SAND;
    }

    private int countRecentNearbyBlockUpdates(PlayerData data, Location from, Location to, long nowMs) {
        if (data == null || from == null || to == null) return 0;
        long windowMs = plugin.getConfig().getLong("prediction.uncertainty.block-update-window-ms", 250L);
        int radius = plugin.getConfig().getInt("prediction.uncertainty.block-update-radius", 1);
        Deque<PlayerData.BlockStateSample> history = data.getRecentBlockStateHistory();
        if (history == null || history.isEmpty()) return 0;
        int count = 0;
        for (PlayerData.BlockStateSample sample : history) {
            if (sample == null) continue;
            if ((nowMs - sample.getTime()) > windowMs) continue;
            if (!matchesNearby(sample, from, radius) && !matchesNearby(sample, to, radius)) continue;
            count++;
        }
        return count;
    }

    private int countRecentGroundAffectingBlockUpdates(PlayerData data, Location from, Location to, long nowMs) {
        if (data == null || from == null || to == null) return 0;
        long windowMs = plugin.getConfig().getLong("prediction.compensation.ground-update-window-ms", 400L);
        int radius = plugin.getConfig().getInt("prediction.compensation.ground-update-radius", 1);
        Deque<PlayerData.BlockStateSample> history = data.getRecentBlockStateHistory();
        if (history == null || history.isEmpty()) return 0;
        int count = 0;
        for (PlayerData.BlockStateSample sample : history) {
            if (sample == null) continue;
            if ((nowMs - sample.getTime()) > windowMs) continue;
            if (!changesGroundSupport(sample)) continue;
            if (!matchesNearby(sample, from, radius) && !matchesNearby(sample, to, radius)) continue;
            int minY = Math.min(from.getBlockY(), to.getBlockY()) - 1;
            int maxY = Math.max(from.getBlockY(), to.getBlockY()) + 1;
            if (sample.getY() < minY || sample.getY() > maxY) continue;
            count++;
        }
        return count;
    }

    private int countRecentCollisionChangingBlockUpdates(PlayerData data, Location from, Location to, long nowMs) {
        if (data == null || from == null || to == null) return 0;
        long windowMs = plugin.getConfig().getLong("prediction.compensation.collision-update-window-ms", 500L);
        int radius = plugin.getConfig().getInt("prediction.compensation.collision-update-radius", 2);
        Deque<PlayerData.BlockStateSample> history = data.getRecentBlockStateHistory();
        if (history == null || history.isEmpty()) return 0;
        int count = 0;
        for (PlayerData.BlockStateSample sample : history) {
            if (sample == null) continue;
            if ((nowMs - sample.getTime()) > windowMs) continue;
            if (!matchesNearby(sample, from, radius) && !matchesNearby(sample, to, radius)) continue;
            if (!changesCollisionShape(sample)) continue;
            count++;
        }
        return count;
    }

    private boolean changesGroundSupport(PlayerData.BlockStateSample sample) {
        if (sample == null) return false;
        return materialAffectsGround(sample.getOldType()) || materialAffectsGround(sample.getNewType());
    }

    private boolean changesCollisionShape(PlayerData.BlockStateSample sample) {
        if (sample == null) return false;
        return isCollisionRelevant(sample.getOldType()) != isCollisionRelevant(sample.getNewType())
                || (isCollisionRelevant(sample.getOldType()) && isCollisionRelevant(sample.getNewType())
                && sample.getOldType() != sample.getNewType());
    }

    private boolean materialAffectsGround(Material material) {
        if (material == null) return false;
        if (material.isSolid()) return true;
        String name = material.name();
        return name.contains("STEP")
                || name.contains("SLAB")
                || name.contains("STAIRS")
                || name.contains("FENCE")
                || name.contains("WALL")
                || name.contains("DOOR")
                || name.contains("TRAP")
                || material == Material.SOUL_SAND
                || material == Material.WEB
                || material == Material.LADDER
                || material == Material.VINE
                || material == Material.CARPET
                || material == Material.SNOW
                || material == Material.SLIME_BLOCK
                || material == Material.ICE
                || material == Material.PACKED_ICE;
    }

    private boolean isCollisionRelevant(Material material) {
        if (material == null) return false;
        if (material.isSolid()) return true;
        String name = material.name();
        return name.contains("FENCE")
                || name.contains("WALL")
                || name.contains("PANE")
                || name.contains("DOOR")
                || name.contains("TRAP")
                || name.contains("STEP")
                || name.contains("SLAB")
                || name.contains("STAIRS")
                || material == Material.WEB
                || material == Material.LADDER
                || material == Material.VINE
                || material == Material.CARPET
                || material == Material.SNOW
                || material == Material.SOUL_SAND
                || material == Material.CACTUS
                || material == Material.SLIME_BLOCK
                || material == Material.ICE
                || material == Material.PACKED_ICE;
    }

    private boolean shouldCompensateForLag(PlayerData data, long nowMs) {
        if (data == null) return false;
        long lagWindowMs = plugin.getConfig().getLong("prediction.compensation.lag-window-ms", 600L);
        if ((nowMs - data.getLastLagEvidenceTime()) <= lagWindowMs) return true;
        if ((nowMs - data.getLastLagSpikeTime()) <= lagWindowMs) return true;
        if ((nowMs - data.getLastLagBurstTime()) <= lagWindowMs) return true;
        if (data.getLagProfileScore() >= plugin.getConfig().getDouble("prediction.compensation.lag-profile-threshold", 0.75D)) return true;
        return data.getSuspiciousLagBursts() >= plugin.getConfig().getInt("prediction.compensation.lag-burst-threshold", 2);
    }

    private boolean shouldUseReplayCompensation(int pointThreeTicks, int hiddenGroundTicks, boolean recentBlockUpdate, boolean lagCompensated) {
        return pointThreeTicks > 0 || hiddenGroundTicks > 0 || recentBlockUpdate || lagCompensated;
    }

    private CompensationBudget buildCompensationBudget(PlayerData data, long nowMs,
                                                       int nearbyBlockUpdates, int nearbySupportUpdates, int nearbyCollisionUpdates,
                                                       int pointThreeTicks, int hiddenGroundTicks,
                                                       boolean lagCompensated, boolean replayCompensated) {
        double horizontal = 0.0D;
        double vertical = 0.0D;
        double toleranceH = 0.0D;
        double toleranceV = 0.0D;
        double score = 0.0D;

        if (nearbyBlockUpdates > 0) {
            horizontal += Math.min(0.12D, nearbyBlockUpdates * plugin.getConfig().getDouble("prediction.compensation.block-horizontal-per-change", 0.012D));
            vertical += Math.min(0.16D, nearbyBlockUpdates * plugin.getConfig().getDouble("prediction.compensation.block-vertical-per-change", 0.018D));
            score += nearbyBlockUpdates * 0.18D;
        }
        if (nearbySupportUpdates > 0) {
            horizontal += Math.min(0.12D, nearbySupportUpdates * plugin.getConfig().getDouble("prediction.compensation.support-horizontal-per-change", 0.018D));
            vertical += Math.min(0.18D, nearbySupportUpdates * plugin.getConfig().getDouble("prediction.compensation.support-vertical-per-change", 0.024D));
            toleranceV += nearbySupportUpdates * 0.010D;
            score += nearbySupportUpdates * 0.28D;
        }
        if (nearbyCollisionUpdates > 0) {
            horizontal += Math.min(0.10D, nearbyCollisionUpdates * plugin.getConfig().getDouble("prediction.compensation.collision-horizontal-per-change", 0.014D));
            vertical += Math.min(0.12D, nearbyCollisionUpdates * plugin.getConfig().getDouble("prediction.compensation.collision-vertical-per-change", 0.015D));
            toleranceH += nearbyCollisionUpdates * 0.008D;
            toleranceV += nearbyCollisionUpdates * 0.006D;
            score += nearbyCollisionUpdates * 0.22D;
        }
        if (pointThreeTicks > 0 || hiddenGroundTicks > 0) {
            horizontal += plugin.getConfig().getDouble("prediction.compensation.replay-horizontal", 0.022D);
            vertical += plugin.getConfig().getDouble("prediction.compensation.replay-vertical", 0.035D);
            toleranceH += 0.010D;
            toleranceV += 0.015D;
            score += 0.35D + (pointThreeTicks * 0.08D) + (hiddenGroundTicks * 0.06D);
        }
        if (lagCompensated) {
            horizontal += plugin.getConfig().getDouble("prediction.compensation.lag-horizontal", 0.030D);
            vertical += plugin.getConfig().getDouble("prediction.compensation.lag-vertical", 0.045D);
            toleranceH += 0.012D;
            toleranceV += 0.018D;
            score += 0.55D;
        }
        if (data != null && data.isBlockStateExempt()) {
            horizontal += 0.015D;
            vertical += 0.020D;
            score += 0.25D;
        }
        return new CompensationBudget(
                horizontal,
                vertical,
                toleranceH,
                toleranceV,
                score,
                replayCompensated,
                score > 0.0D
        );
    }

    private List<Location> collectReplayStartLocations(PlayerData data, Location from, long nowMs, boolean replayCompensated) {
        List<Location> starts = new ArrayList<Location>();
        if (from == null) return starts;
        starts.add(from);
        if (!replayCompensated || data == null || from.getWorld() == null) {
            return starts;
        }

        long replayWindow = plugin.getConfig().getLong("prediction.compensation.replay-window-ms", 175L);
        int maxSamples = plugin.getConfig().getInt("prediction.compensation.max-replay-samples", 3);
        Deque<PlayerData.PositionSample> history = data.getPositionHistory();
        if (history == null || history.isEmpty()) return starts;

        int added = 0;
        java.util.Iterator<PlayerData.PositionSample> iterator = history.descendingIterator();
        while (iterator.hasNext()) {
            PlayerData.PositionSample sample = iterator.next();
            if (sample == null || !sample.matchesWorld(from.getWorld())) continue;
            long age = nowMs - sample.getTime();
            if (age <= 0L || age > replayWindow) continue;
            Location loc = sample.toLocation(from.getWorld());
            if (loc == null) continue;
            if (sameBlockAndClose(loc, from)) continue;
            starts.add(loc);
            added++;
            if (added >= maxSamples) break;
        }
        return starts;
    }

    private boolean sameBlockAndClose(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.distanceSquared(b) <= 0.0125D;
    }

    private static final class CompensationBudget {
        final double horizontalExtra;
        final double verticalExtra;
        final double horizontalTolerance;
        final double verticalTolerance;
        final double score;
        final boolean replayCompensated;
        final boolean worldCompensated;

        private CompensationBudget(double horizontalExtra, double verticalExtra,
                                   double horizontalTolerance, double verticalTolerance,
                                   double score, boolean replayCompensated, boolean worldCompensated) {
            this.horizontalExtra = horizontalExtra;
            this.verticalExtra = verticalExtra;
            this.horizontalTolerance = horizontalTolerance;
            this.verticalTolerance = verticalTolerance;
            this.score = score;
            this.replayCompensated = replayCompensated;
            this.worldCompensated = worldCompensated;
        }
    }

    private boolean matchesNearby(PlayerData.BlockStateSample sample, Location location, int radius) {
        if (sample == null || location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equals(sample.getWorldName())) return false;
        return Math.abs(sample.getX() - location.getBlockX()) <= radius
                && Math.abs(sample.getY() - location.getBlockY()) <= radius
                && Math.abs(sample.getZ() - location.getBlockZ()) <= radius;
    }

    private double r(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
