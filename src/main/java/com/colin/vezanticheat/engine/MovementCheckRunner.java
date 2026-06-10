package com.colin.vezanticheat.engine;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.EngineMovementGrace;
import com.colin.vezanticheat.utils.FallArcTracker;
import com.colin.vezanticheat.utils.FlyPatternUtil;
import com.colin.vezanticheat.utils.FlyPhysicsTracker;
import com.colin.vezanticheat.utils.GroundSpoofTracker;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import com.colin.vezanticheat.utils.NoFallTracker;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.PotionUtil;
import com.colin.vezanticheat.utils.SetbackUtil;
import com.colin.vezanticheat.utils.SpeedPatternUtil;
import com.colin.vezanticheat.utils.UseItemTracker;
import com.colin.vezanticheat.tier.prediction.PredictionVehicle;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * MovementCheckRunner — per-packet orchestration of the prediction engine.
 *
 * For each movement packet it commits the new position, rebuilds the {@link MovementPlayer}
 * state (environment, potions, friction, ping/tps, pending knockback), runs
 * {@link PredictionEngine#guessBestMovement}, computes and reduces the offset, decomposes it
 * into horizontal/vertical components, and publishes an {@link EngineResult} on
 * {@link PlayerData} for the movement sub-checks to consume.
 *
 * This is the GrimAC `MovementCheckRunner` analogue, adapted to this plugin's PacketEvents
 * intake and live-world-backed {@link CompensatedWorld}.
 */
public final class MovementCheckRunner {

    private final VezAntiCheat plugin;

    public MovementCheckRunner(VezAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("engine.enabled", true);
    }

    public void onTeleport(Player player, PlayerData data, Location to) {
        if (player == null || data == null || to == null) return;
        MovementPlayer mp = data.getMovementPlayer();
        mp.resetTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch(), true);
        mp.lastTeleportMs = System.currentTimeMillis();
        mp.ticksSinceTeleport = 0;
        mp.uncertaintyHandler.lastTeleportTicks = 0;
        data.setEngineInitialized(true);
        data.resetEngineUnverifiedTicks();
        data.getCompensatedWorld().setWorld(to.getWorld());
        // Pause the persistent clock-drift ledger across this teleport (corrections must not accrue).
        PlayerClock.onTeleport(plugin, data);
        SetbackUtil.seedValidGroundAnchor(data, to, System.currentTimeMillis());
    }

    public EngineResult onMovement(Player player, PlayerData data, Location from, Location to,
                                   boolean clientGround, boolean positionIncluded, long nowMs) {
        if (!isEnabled() || player == null || data == null) return null;

        long perfStartNs = 0L;
        if (plugin.perf() != null && plugin.perf().isEnabled()) {
            perfStartNs = System.nanoTime();
        }
        try {
            return onMovementInner(player, data, from, to, clientGround, positionIncluded, nowMs);
        } finally {
            if (perfStartNs > 0L) {
                plugin.perf().recordMovement(System.nanoTime() - perfStartNs);
            }
        }
    }

    private EngineResult onMovementInner(Player player, PlayerData data, Location from, Location to,
                                         boolean clientGround, boolean positionIncluded, long nowMs) {

        CompensatedWorld world = data.getCompensatedWorld();
        if (to != null && to.getWorld() != null) {
            world.setWorld(to.getWorld());
        }
        world.pruneExpired(nowMs);

        // Position-less flying packets carry no movement to validate; the 0.03 estimator on the
        // next real position packet accounts for any skipped sub-threshold tick.
        if (!positionIncluded) {
            observeHoverOnExempt(data, clientGround);
            return publishExempt(data, nowMs, "positionless");
        }

        MovementPlayer mp = data.getMovementPlayer();

        // Initialize on first packet or after world change.
        if (!data.isEngineInitialized() || from == null || to == null
                || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            if (to != null) {
                mp.resetTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch(), clientGround);
                data.setEngineInitialized(true);
            }
            return publishExempt(data, nowMs, "init");
        }

        // CHUNK-UNLOAD HANDLING: rather than skipping validation outright when surrounding chunks are
        // unloaded (a known phase/speed exploit vector), enter an "unverified" mode. We still accrue
        // the player's horizontal motion into the offset-advantage accumulator and force a resync
        // setback if the unverified streak persists beyond a configurable tick budget.
        if (chunkUnloaded(from, to)) {
            handleUnverifiedTick(player, data, mp, from, to, clientGround, nowMs);
            observeHoverOnExempt(data, clientGround, to.getY() - from.getY());
            return publishExempt(data, nowMs, "chunk-unverified");
        }
        data.resetEngineUnverifiedTicks();

        // Exemptions: skip prediction but keep state synced to the live position.
        String exempt = exemptReason(player, data, from, to);
        if (exempt != null) {
            syncToPosition(mp, to, clientGround);
            if ("vehicle".equals(exempt)) {
                handleVehicleMovement(player, data, from, to);
            }
            return publishExempt(data, nowMs, exempt);
        }

        // Commit current position; actualMovement = current - previous.
        mp.lastX = from.getX();
        mp.lastY = from.getY();
        mp.lastZ = from.getZ();
        mp.x = to.getX();
        mp.y = to.getY();
        mp.z = to.getZ();
        mp.yaw = to.getYaw();
        mp.pitch = to.getPitch();
        mp.lastOnGround = mp.onGround;
        mp.clientClaimsGround = clientGround;
        mp.actualMovement = new Vector(mp.x - mp.lastX, mp.y - mp.lastY, mp.z - mp.lastZ);

        // Populate per-tick state from the start-of-tick (from) position.
        populateState(player, data, mp, from, clientGround, positionIncluded, nowMs);
        noteLegitVerticalMotion(mp, world, data, nowMs);

        // Run the prediction.
        PredictionEngine.guessBestMovement(mp, world);
        Vector predicted = mp.predictedVelocity == null ? new Vector() : mp.predictedVelocity.vector;

        double rawOffset = predicted.distance(mp.actualMovement);
        double horizontalOffset = Math.hypot(predicted.getX() - mp.actualMovement.getX(),
                predicted.getZ() - mp.actualMovement.getZ());
        double verticalOffset = Math.abs(predicted.getY() - mp.actualMovement.getY());
        double reduced = mp.uncertaintyHandler.reduceOffset(rawOffset);

        // Carry small leftover offset as lenience for next tick (giveOffsetLenienceNextTick).
        mp.uncertaintyHandler.lastHorizontalOffset = Math.min(1.0D, horizontalOffset) * 0.6D;
        mp.uncertaintyHandler.lastVerticalOffset = Math.min(1.0D, verticalOffset) * 0.6D;
        mp.uncertaintyHandler.collidedHorizontally = mp.collisionX || mp.collisionZ;

        // LONG-WINDOW OFFSET ADVANTAGE — accumulate each tick's reduced offset above a small floor.
        // Unlike a hard rolling window this uses a SLOW per-clean-second decay so sustained
        // sub-threshold speed (ratio ~1.003-1.006) cannot hide below an instantaneous threshold.
        // It survives teleports (no reset here); only MovementEnforcement.executeSetback zeroes it.
        double advantageThreshold = plugin.getConfig().getDouble("engine.advantage-threshold", 0.001D);
        // During a legit fall-arc window the reduced offset must not accrue (treated as a clean tick
        // for accumulation purposes); the per-clean-second decay still applies.
        double accrualOffset = FallArcTracker.isInFallArcWindow(plugin, data, nowMs) ? 0.0D : reduced;
        if (ItemUseMovementUtil.suppressesMovementFlags(data, nowMs)) {
            data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.75D);
        } else if (EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs)) {
            data.setEngineOffsetAdvantage(data.getEngineOffsetAdvantage() * 0.5D);
        } else {
            // Clean ticks bleed off via a slow per-clean-second decay (default -25%/clean second),
            // NOT a hard window reset; the accumulator survives teleports and is zeroed only by a
            // setback (MovementEnforcement.executeSetback). See OffsetAdvantageAccumulator.
            double cleanDecayPerSec = plugin.getConfig().getDouble("engine.advantage-clean-decay-per-second", 0.25D);
            double advantageCap = plugin.getConfig().getDouble("engine.advantage-cap", 2.0D);
            double next = OffsetAdvantageAccumulator.advance(
                    data.getEngineOffsetAdvantage(),
                    accrualOffset,
                    advantageThreshold,
                    cleanDecayPerSec,
                    Math.max(0L, data.getLastFlyingIntervalMs()),
                    advantageCap);
            data.setEngineOffsetAdvantage(next);
        }

        double advantage = data.getEngineOffsetAdvantage();

        mp.ticksSinceTeleport = Math.min(1000, mp.ticksSinceTeleport + 1);
        mp.uncertaintyHandler.lastTeleportTicks = mp.ticksSinceTeleport;

        String debug = "off=" + r(reduced) + "/" + r(rawOffset)
                + " hOff=" + r(horizontalOffset) + " vOff=" + r(verticalOffset)
                + " pred=(" + r(predicted.getX()) + "," + r(predicted.getY()) + "," + r(predicted.getZ()) + ")"
                + " act=(" + r(mp.actualMovement.getX()) + "," + r(mp.actualMovement.getY()) + "," + r(mp.actualMovement.getZ()) + ")"
                + " best=" + mp.predictedVelocity.type
                + " g=" + mp.onGround + " cg=" + clientGround
                + " water=" + mp.inWater + " climb=" + mp.onClimbable
                + " kb=" + (mp.pendingKnockback != null) + " p3=" + mp.couldSkipTick
                + " adv=" + r(advantage);

        boolean predictedOnGround = mp.onGround;
        if (mp.uncertaintyHandler != null
                && (mp.uncertaintyHandler.stepUpTick || mp.uncertaintyHandler.slabEdgeTick)
                && clientGround
                && mp.actualMovement.getY() >= 0.015D) {
            predictedOnGround = true;
        }

        long flyingGapMs = Math.max(0L, data.getLastFlyingIntervalMs());
        double timerDebtMs = data.getTimerDebtMs();
        boolean legacyMovementActive = plugin.prediction() != null
                && !plugin.getConfig().getBoolean("engine.skip-legacy-movement-prediction", true);
        if (legacyMovementActive && plugin.prediction() != null) {
            com.colin.vezanticheat.prediction.PredictionResult legacy = plugin.prediction().getLastResult(data);
            if (legacy != null && timerDebtMs <= 0.0D) {
                timerDebtMs = legacy.timerDebtMs;
            }
        }
        // PlayerClock drift is handled by PredictionTimer only — do not mix into timerDebtMs
        // or SimulationBlinkDebt false-flags normal sprint from tx sync jitter.

        Vector actualMovement = mp.actualMovement == null ? new Vector() : mp.actualMovement;
        double distH = Math.hypot(actualMovement.getX(), actualMovement.getZ());

        double noSlowThr = plugin.getConfig().getDouble("prediction.no-slow.engine-horizontal", 0.025D);
        boolean activelyUsingItem = UseItemTracker.isUsingItem(player, data);
        boolean fakeReleaseUse = UseItemTracker.isFakeReleaseUse(player, data, nowMs);
        boolean compensatedUse = activelyUsingItem || fakeReleaseUse;
        double noSlowExcess = compensatedUse && horizontalOffset > noSlowThr
                ? horizontalOffset - noSlowThr : 0.0D;

        boolean kbActive = mp.pendingKnockback != null || mp.pendingExplosion != null;
        boolean combatGrace = EngineMovementGrace.isKnockbackOrCombatGrace(plugin, data, nowMs);
        double sprintThr = plugin.getConfig().getDouble("engine.illegal-sprint-threshold", 0.14D)
                + PotionUtil.combinedSpeedOffsetAllowance(player);
        boolean illegalSprint = mp.sprinting && !kbActive && !activelyUsingItem && !combatGrace
                && !mp.inWater && !mp.onClimbable && !mp.couldSkipTick
                && mp.onGround && distH >= 0.06D
                && horizontalOffset > sprintThr;
        if (illegalSprint && PotionUtil.hasSpeedBoost(player)) {
            double ratio = distH > 0.001D
                    ? Math.hypot(predicted.getX(), predicted.getZ()) / distH : 1.0D;
            double ratioCap = 1.14D + (PotionUtil.speedLevel(player) * 0.04D)
                    + Math.max(0.0D, (player.getWalkSpeed() / 0.2F - 1.0F) * 0.06D);
            if (horizontalOffset <= sprintThr + 0.10D && ratio < ratioCap) {
                illegalSprint = false;
            }
        }

        double sneakThr = plugin.getConfig().getDouble("engine.illegal-sneak-threshold", 0.04D);
        boolean illegalSneak = mp.sneaking && !kbActive && horizontalOffset > sneakThr;

        double entityPushOffset = 0.0D;
        if (mp.uncertaintyHandler != null) {
            if (mp.uncertaintyHandler.pistonPushTick) {
                entityPushOffset = reduced;
            } else if (mp.uncertaintyHandler.nearBoat && horizontalOffset > 0.04D) {
                entityPushOffset = horizontalOffset;
            }
        }

        debug += " gap=" + flyingGapMs + "ms debt=" + r(timerDebtMs)
                + " use=" + mp.usingItem + " sprintBad=" + illegalSprint + " sneakBad=" + illegalSneak;

        EngineResult result = EngineResult.builder()
                .timeMs(nowMs)
                .checked(true)
                .offset(reduced)
                .rawOffset(rawOffset)
                .horizontalOffset(horizontalOffset)
                .verticalOffset(verticalOffset)
                .predicted(predicted.clone())
                .actual(mp.actualMovement.clone())
                .predictedOnGround(predictedOnGround)
                .clientGround(clientGround)
                .collisionX(mp.collisionX)
                .collisionY(mp.collisionY)
                .collisionZ(mp.collisionZ)
                .bestType(mp.predictedVelocity.type)
                .knockbackTick(mp.pendingKnockback != null)
                .explosionTick(mp.pendingExplosion != null)
                .onIce(mp.onIce)
                .onSlime(mp.onSlime)
                .inWater(mp.inWater)
                .onClimbable(mp.onClimbable)
                .inWeb(mp.inWeb)
                .couldSkipTick(mp.couldSkipTick)
                .flyingGapMs(flyingGapMs)
                .timerDebtMs(timerDebtMs)
                .usingItem(activelyUsingItem || fakeReleaseUse)
                .illegalSprint(illegalSprint)
                .illegalSneak(illegalSneak)
                .noSlowExcess(noSlowExcess)
                .entityPushOffset(entityPushOffset)
                .debug(debug)
                .build();
        updateEngineAirState(data, result);
        FlyPatternUtil.observe(data, result);
        FlyPhysicsTracker.observe(plugin, data, result, nowMs);
        GroundSpoofTracker.observe(plugin, player, data, result, nowMs);
        NoFallTracker.observe(plugin, player, data, result, nowMs);
        SpeedPatternUtil.observe(plugin, player, data, result, nowMs);
        data.setLastEngineResult(result);
        if (plugin.tierChecks() != null) {
            plugin.tierChecks().onEngineResult(player, data, result, nowMs);
        }
        return result;
    }

    private void updateEngineAirState(PlayerData data, EngineResult result) {
        if (data == null || result == null || !result.checked) return;

        Vector actual = result.actual == null ? new Vector() : result.actual;
        double dy = actual.getY();
        boolean airborne = !result.clientGround && !result.predictedOnGround
                && !result.inWater && !result.onClimbable && !result.inWeb
                && !result.knockbackTick && !result.explosionTick;

        if (airborne) {
            data.setEngineAirborneTicks(data.getEngineAirborneTicks() + 1);
        } else {
            data.setEngineAirborneTicks(0);
        }

        boolean hoverLike = airborne && Math.abs(dy) < 0.03D;
        data.recordHoverDySample(hoverLike);
        if (hoverLike && result.verticalOffset > 0.04D) {
            data.setEngineHoverTicks(data.getEngineHoverTicks() + 1);
        } else {
            data.setEngineHoverTicks(0);
        }
    }

    private void populateState(Player player, PlayerData data, MovementPlayer mp, Location from,
                               boolean clientGround, boolean positionIncluded, long nowMs) {
        CompensatedWorld world = data.getCompensatedWorld();

        mp.sprinting = player.isSprinting();
        mp.sneaking = player.isSneaking();
        mp.blocking = player.isBlocking();
        mp.movementSpeed = PotionUtil.effectiveLandMovementSpeed(player);
        mp.itemInputScale = ItemUseMovementUtil.movementInputScale(data, nowMs);
        mp.usingItem = mp.blocking || data.getBowPullStart() > 0L
                || ItemUseMovementUtil.isEngineUsingItem(data, nowMs);
        mp.ping = Math.max(0, PingUtil.getPing(player));
        mp.tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0D;
        mp.world = world;

        mp.speedAmplifier = PotionUtil.speedLevel(player);
        mp.slowAmplifier = PotionUtil.slownessLevel(player);
        mp.jumpAmplifier = PotionUtil.jumpBoostLevel(player);

        int fx = Collisions.floor(from.getX());
        int fy = Collisions.floor(from.getY());
        int fz = Collisions.floor(from.getZ());

        Material feet = world.getType(fx, fy, fz);
        Material head = world.getType(fx, fy + 1, fz);
        mp.inWater = isWater(feet) || isWater(head);
        mp.inLava = isLava(feet) || isLava(head);
        mp.onClimbable = com.colin.vezanticheat.utils.LiquidLocomotionUtil.isOnClimbable(
                world, from.getX(), from.getY(), from.getZ());
        mp.inWeb = feet == Material.WEB || head == Material.WEB;

        Material below = world.getType(fx, fy - 1, fz);
        mp.onIce = below == Material.ICE || below == Material.PACKED_ICE;
        mp.onSlime = below == Material.SLIME_BLOCK;
        mp.uncertaintyHandler.influencedByBouncyBlock = mp.onSlime;
        mp.uncertaintyHandler.nearBoat = player.isInsideVehicle()
                || (player.getNearbyEntities(2.0D, 2.0D, 2.0D).size() > 0 && mp.inWater);
        mp.uncertaintyHandler.pistonPushTick = recentBlockChange(data, nowMs)
                && Math.abs(mp.actualMovement.getY()) > 0.01D
                && Math.abs(mp.actualMovement.getY()) < 0.5D;

        // Server-truth ground at the start-of-tick position drives friction and jumps.
        boolean serverGround = Collisions.isOnGround(world, from.getX(), from.getY(), from.getZ());
        // SERVER-SIDE GROUND TRUTH: publish the raw collision verdict alongside the client claim so
        // ground-spoof checks can compare. PointThree leniency still feeds prediction INPUT below.
        data.setServerGround(serverGround);
        mp.onGround = serverGround || (clientGround && Math.abs(mp.actualMovement.getY()) < 1.0E-4D);

        // COMPENSATION BUDGET CAP: clamp total stacked lenience so multiple compensation triggers
        // cannot be farmed together to hide a real offset (default 0.12; aggressive 0.08).
        mp.uncertaintyHandler.leniencyBudgetCap =
                plugin.getConfig().getDouble("engine.compensation.leniency-budget-cap", 0.12D);

        mp.friction = (float) (groundSlip(below) * 0.91D);

        // Pending knockback / explosion (folded once per impulse).
        mp.pendingKnockback = null;
        mp.pendingExplosion = null;
        Vector lastVel = data.getLastVelocity();
        long velTime = data.getLastVelocityTime();
        long kbWindow = plugin.getConfig().getLong("engine.knockback-window-ms", 450L);
        if (lastVel != null && velTime > 0L && (nowMs - velTime) <= kbWindow) {
            mp.pendingKnockback = lastVel.clone();
            if (velTime > mp.lastKnockbackConsumedMs) {
                mp.lastKnockbackConsumedMs = velTime;
            }
        }
        Vector explosion = data.getLastExplosionVelocity();
        if (explosion != null && (nowMs - data.getLastDamageTime()) <= kbWindow
                && data.getLastDamageTime() > mp.lastExplosionConsumedMs) {
            mp.pendingExplosion = explosion.clone();
            mp.lastExplosionConsumedMs = data.getLastDamageTime();
        }

        // 0.03 estimate + block-change uncertainty.
        mp.couldSkipTick = mp.pointThreeEstimator.determineCanSkipTick(positionIncluded);
        boolean blockChange = recentBlockChange(data, nowMs);
        if (blockChange) {
            int maxTicks = plugin.getConfig().getInt("engine.exemption-caps.block-change-max-ticks", 6);
            long windowMs = plugin.getConfig().getLong("engine.exemption-caps.block-change-window-ms", 2000L);
            blockChange = data.tryConsumeBlockChangeLenience(nowMs, maxTicks, windowMs);
        }
        mp.uncertaintyHandler.blockChangeTicks = blockChange ? 1 : 0;
    }

    private void handleVehicleMovement(Player player, PlayerData data, Location from, Location to) {
        com.colin.vezanticheat.tier.TierCheck check = plugin.tierChecks().registry().get("PredictionVehicle");
        if (check instanceof PredictionVehicle) {
            ((PredictionVehicle) check).evaluateMountMovement(player, data, from, to);
        }
    }

    private String exemptReason(Player player, PlayerData data, Location from, Location to) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return "gamemode";
        if (player.isFlying() || player.getAllowFlight()) return "flying";
        if (player.isInsideVehicle() || player.getVehicle() != null) return "vehicle";
        if (data.isTeleportExempt()) return "teleport";
        if (PlayerData.bypass(player)) return "bypass";
        if (to.getWorld() == null) return "world";
        // Chunk-unload is handled by the unverified-mode path in onMovement, not here.
        return null;
    }

    private boolean chunkUnloaded(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null) return false;
        if (!to.getWorld().isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)) return true;
        return !from.getWorld().isChunkLoaded(from.getBlockX() >> 4, from.getBlockZ() >> 4);
    }

    /**
     * Unverified-mode tick: collision data is unavailable, so we cannot run the full prediction.
     * We keep MovementPlayer state synced, accrue horizontal motion into the offset-advantage
     * accumulator (so a player cannot freely cheat in unloaded chunks), and force a resync setback
     * once the unverified streak exceeds the configured budget.
     */
    private void handleUnverifiedTick(Player player, PlayerData data, MovementPlayer mp,
                                      Location from, Location to, boolean clientGround, long nowMs) {
        syncToPosition(mp, to, clientGround);
        if (PlayerData.bypass(player) || data.isTeleportExempt()) {
            data.resetEngineUnverifiedTicks();
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.hypot(dx, dz);
        double accrual = plugin.getConfig().getDouble("engine.unverified.horizontal-accrual-floor", 0.30D);
        if (horizontal > accrual) {
            double advantageCap = plugin.getConfig().getDouble("engine.advantage-cap", 2.0D);
            double gain = (horizontal - accrual)
                    * plugin.getConfig().getDouble("engine.unverified.accrual-factor", 0.5D);
            data.setEngineOffsetAdvantage(Math.min(advantageCap, data.getEngineOffsetAdvantage() + gain));
        }

        int streak = data.incrementEngineUnverifiedTicks();
        int maxTicks = plugin.getConfig().getInt("engine.unverified.max-ticks", 20);
        if (streak >= maxTicks) {
            com.colin.vezanticheat.utils.MovementEnforcement.executeSetback(
                    plugin, player, data, "engine unverified-chunk resync ticks=" + streak);
            data.resetEngineUnverifiedTicks();
        }
    }

    private void syncToPosition(MovementPlayer mp, Location to, boolean clientGround) {
        if (to == null) return;
        mp.resetTo(to.getX(), to.getY(), to.getZ(), to.getYaw(), to.getPitch(), clientGround);
        mp.ticksSinceTeleport = 0;
        mp.uncertaintyHandler.lastTeleportTicks = 0;
    }

    private EngineResult publishExempt(PlayerData data, long nowMs, String reason) {
        EngineResult result = EngineResult.exempt(nowMs, reason);
        data.setLastEngineResult(result);
        return result;
    }

    /** Record hover window samples even on exempt ticks so hover cannot be reset by positionless gaps. */
    private void observeHoverOnExempt(PlayerData data, boolean clientGround) {
        observeHoverOnExempt(data, clientGround, data == null ? 0.0D : data.getLastMoveDy());
    }

    private void observeHoverOnExempt(PlayerData data, boolean clientGround, double dy) {
        if (data == null) return;
        boolean airborne = !clientGround && data.getEngineAirborneTicks() > 0;
        boolean hoverLike = airborne && Math.abs(dy) < 0.03D;
        data.recordHoverDySample(hoverLike);
    }

    private void noteLegitVerticalMotion(MovementPlayer mp, CompensatedWorld world, PlayerData data, long nowMs) {
        if (mp == null || mp.actualMovement == null || mp.uncertaintyHandler == null) return;

        double dy = mp.actualMovement.getY();
        double distH = Math.hypot(mp.actualMovement.getX(), mp.actualMovement.getZ());

        if (dy >= 0.12D && dy <= 0.62D && distH <= 0.85D) {
            mp.uncertaintyHandler.stepUpTick = true;
        }
        if (dy >= 0.015D && dy <= 0.40D && distH >= 0.01D && distH <= 0.85D && hasStepSurfaceBelow(world, mp)) {
            mp.uncertaintyHandler.slabEdgeTick = true;
        }
        if (dy >= -0.65D && dy <= 0.06D && distH <= 0.85D
                && (hasStepSurfaceBelow(world, mp) || dy <= -0.01D)) {
            mp.uncertaintyHandler.dropTick = true;
        }

        if (data != null) {
            long kbGrace = plugin.getConfig().getLong("engine.knockback-grace-ms",
                    Math.max(plugin.cfg().velocityExemptMs(), 500L));
            if (data.getLastVelocityTime() > 0L && (nowMs - data.getLastVelocityTime()) <= kbGrace) {
                mp.uncertaintyHandler.knockbackGraceTick = true;
            }
            long combatMs = plugin.getConfig().getLong("engine.combat-movement-grace-ms", 450L);
            boolean combat = (data.getLastUseEntityTime() > 0L && (nowMs - data.getLastUseEntityTime()) <= combatMs)
                    || (data.getLastDamageTime() > 0L && (nowMs - data.getLastDamageTime()) <= combatMs);
            if (combat) {
                int maxTicks = plugin.getConfig().getInt("engine.exemption-caps.combat-grace-max-ticks", 8);
                long windowMs = plugin.getConfig().getLong("engine.exemption-caps.combat-grace-window-ms", 2000L);
                if (data.tryConsumeCombatGraceTick(nowMs, maxTicks, windowMs)) {
                    mp.uncertaintyHandler.combatMotionTick = true;
                }
            }
        }
    }

    private boolean hasStepSurfaceBelow(CompensatedWorld world, MovementPlayer mp) {
        if (world == null) return false;
        int fx = Collisions.floor(mp.x);
        int fy = Collisions.floor(mp.y);
        int fz = Collisions.floor(mp.z);
        for (int yOff = 0; yOff <= 1; yOff++) {
            for (double ox = -0.3; ox <= 0.3; ox += 0.3) {
                for (double oz = -0.3; oz <= 0.3; oz += 0.3) {
                    Material below = world.getType(Collisions.floor(mp.x + ox), fy - yOff, Collisions.floor(mp.z + oz));
                    if (isStepLike(below)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isStepLike(Material material) {
        if (material == null || material == Material.AIR) return false;
        String name = material.name();
        return material.isSolid()
                || name.contains("STEP")
                || name.contains("SLAB")
                || name.contains("STAIRS")
                || name.contains("FENCE")
                || name.contains("WALL")
                || material == Material.SNOW
                || material == Material.CARPET;
    }

    private boolean recentBlockChange(PlayerData data, long nowMs) {
        java.util.Deque<PlayerData.BlockStateSample> history = data.getRecentBlockStateHistory();
        if (history == null || history.isEmpty()) return false;
        for (PlayerData.BlockStateSample sample : history) {
            if (sample != null && (nowMs - sample.getTime()) <= 250L) return true;
        }
        return false;
    }

    private static double groundSlip(Material below) {
        if (below == Material.ICE || below == Material.PACKED_ICE) return 0.98D;
        if (below == Material.SLIME_BLOCK) return 0.8D;
        return 0.6D;
    }

    private static boolean isWater(Material m) {
        return m == Material.WATER || m == Material.STATIONARY_WATER;
    }

    private static boolean isLava(Material m) {
        return m == Material.LAVA || m == Material.STATIONARY_LAVA;
    }

    private static double r(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }
}
