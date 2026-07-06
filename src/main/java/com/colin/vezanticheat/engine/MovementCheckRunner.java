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
        // movement-engine.detection-enabled defaults FALSE: the from-scratch offset engine false-flagged
        // legit movement, so its SIMULATION + PREDICTION-offset detection is off by default. Deterministic
        // movement checks (timer via PlayerClock, nofall via the fall tracker) do not use this engine and
        // keep working. Set true to re-enable offset prediction once it is verified false-positive free.
        return plugin.getConfig().getBoolean("engine.enabled", true)
                && plugin.getConfig().getBoolean("movement-engine.detection-enabled", false);
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
        data.getMovementState().reset(to, true, System.currentTimeMillis());
        // Pause the persistent clock-drift ledger across this teleport (corrections must not accrue).
        PlayerClock.onTeleport(plugin, data);
        SetbackUtil.seedValidGroundAnchor(data, to, System.currentTimeMillis());
    }

    public EngineResult onMovement(Player player, PlayerData data, Location from, Location to,
                                   boolean clientGround, boolean positionIncluded, long nowMs) {
        if (!isEnabled() || player == null || data == null) return null;
        // Master toggle (/vez off → anticheat.enabled=false): fully skip the prediction engine so no offset
        // work, EngineResult publishing, tier-check dispatch, or setback runs while the AC is disabled.
        if (plugin.tierCfg() == null || !plugin.tierCfg().enabled()) return null;

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
        data.getCompensatedWorld().pruneExpired(nowMs);
        com.colin.vezanticheat.movement.SimulationResult simulation =
                com.colin.vezanticheat.movement.MovementSimulator.simulate(
                        plugin, player, data, from, to, clientGround, positionIncluded, nowMs);
        data.setLastSimulationResult(simulation);
        data.recordMovementDebugTrace(new com.colin.vezanticheat.movement.DebugTrace(simulation));

        EngineResult result = simulation.toEngineResult();
        data.setEngineOffsetAdvantage(simulation.advantage);
        updateEngineAirState(data, result);
        data.setLastEngineResult(result);
        if (plugin.tierChecks() != null) {
            plugin.tierChecks().onEngineResult(player, data, result, nowMs);
        }
        com.colin.vezanticheat.movement.SetbackManager.enforceIfNeeded(plugin, player, data, simulation);
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
        // EntityIndex snapshot instead of getNearbyEntities (Netty thread); inWater first
        // so the proximity scan only runs for the rare swimming case.
        com.colin.vezanticheat.utils.EntityIndex entityIndex = com.colin.vezanticheat.utils.EntityIndex.active();
        mp.uncertaintyHandler.nearBoat = player.isInsideVehicle()
                || (mp.inWater && entityIndex != null && entityIndex.anyOtherEntityNear(player, 2.0D));
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
