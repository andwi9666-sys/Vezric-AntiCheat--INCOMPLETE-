package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.BlockProvider;
import com.colin.vezanticheat.engine.Collisions;
import com.colin.vezanticheat.utils.ItemUseMovementUtil;
import com.colin.vezanticheat.utils.UseItemTracker;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class MovementSimulator {

    private MovementSimulator() {}

    public static SimulationResult simulate(VezAntiCheat plugin, Player player, PlayerData data,
                                            Location from, Location to, boolean clientGround,
                                            boolean positionIncluded, long nowMs) {
        MovementPhysicsConfig cfg = MovementPhysicsConfig.from(plugin);
        PlayerMovementState state = data.getMovementState();
        if (to != null && to.getWorld() != null) {
            data.getCompensatedWorld().setWorld(to.getWorld());
        }

        if (player == null || data == null || to == null || to.getWorld() == null) {
            return exempt(nowMs, state, "invalid");
        }
        if (PlayerData.bypass(player) || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR || player.isFlying()
                || player.getAllowFlight() || player.isInsideVehicle()) {
            state.reset(to, clientGround, nowMs);
            return exempt(nowMs, state, "bypass-or-flight");
        }
        if (!state.initialized || from == null || from.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            state.reset(to, clientGround, nowMs);
            return exempt(nowMs, state, "init");
        }

        state.tick++;
        TimerTracker.update(state, data, positionIncluded, data.getLastFlyingIntervalMs(), cfg);

        Vector actual = positionIncluded
                ? new Vector(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ())
                : new Vector();

        Environment env = Environment.read(data.getCompensatedWorld(), from, cfg);
        boolean serverGround = CollisionResolver.isOnGround(data.getCompensatedWorld(), from.getX(), from.getY(), from.getZ());
        data.setServerGround(serverGround);

        Vector pendingVelocity = VelocityTracker.pendingVelocity(state, data, nowMs,
                plugin.getConfig().getLong("movement-engine.velocity-window-ms", 450L));
        Vector pendingExplosion = ExplosionTracker.pendingExplosion(state, data, nowMs,
                plugin.getConfig().getLong("movement-engine.explosion-window-ms", 900L));

        // Use the CURRENT packet's yaw (to) for movement input — vanilla rotates then moves within a tick,
        // so the movement to this position used this packet's rotation (matches Grim's current player.yaw).
        // Using from.getYaw() (previous yaw) rotated every candidate off-axis whenever the player turned
        // while moving, producing a persistent offset and false-flagging all legit movement.
        List<Candidate> candidates = enumerate(plugin, player, data, state, from, to.getYaw(), env, serverGround,
                pendingVelocity, pendingExplosion, cfg, nowMs);
        Candidate best = best(candidates, actual);
        if (best == null) {
            best = new Candidate(new Vector(), new Vector(), serverGround, false, false, false, "empty");
        }

        double dx = best.displacement.getX() - actual.getX();
        double dy = best.displacement.getY() - actual.getY();
        double dz = best.displacement.getZ() - actual.getZ();
        double rawOffset = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontalOffset = Math.hypot(dx, dz);
        double verticalOffset = Math.abs(dy);
        double offset = Math.max(0.0D, rawOffset - cfg.collisionTolerance);

        double advantage = advanceAdvantage(state.offsetAdvantage, offset, cfg);
        boolean usingItem = UseItemTracker.isUsingItem(player, data) || ItemUseMovementUtil.isEngineUsingItem(data, nowMs);
        double noSlowExcess = usingItem && horizontalOffset > cfg.minorOffset
                ? horizontalOffset - cfg.minorOffset : 0.0D;

        SimulationResult partial = SimulationResult.builder()
                .timeMs(nowMs)
                .tick(state.tick)
                .checked(true)
                .worldName(to.getWorld().getName())
                .position(to)
                .expectedMotion(best.displacement)
                .actualMotion(actual)
                .nextMotion(best.nextMotion)
                .offset(offset)
                .rawOffset(rawOffset)
                .horizontalOffset(horizontalOffset)
                .verticalOffset(verticalOffset)
                .clientGround(clientGround)
                .predictedGround(best.onGround)
                .collisionX(best.collisionX)
                .collisionY(best.collisionY)
                .collisionZ(best.collisionZ)
                .inWater(env.inWater)
                .inLava(env.inLava)
                .inWeb(env.inWeb)
                .onClimbable(env.onClimbable)
                .onIce(env.onIce)
                .onSlime(env.onSlime)
                .usingItem(usingItem)
                .illegalSprint(player.isSprinting() && usingItem && horizontalOffset > cfg.minorOffset)
                .illegalSneak(player.isSneaking() && horizontalOffset > cfg.minorOffset)
                .couldSkipTick(!positionIncluded)
                .velocityTick(pendingVelocity != null)
                .explosionTick(pendingExplosion != null)
                .blockBelow(env.blockBelow)
                .surface(env.surface)
                .speedAmplifier(PotionResolver.speedLevel(player))
                .jumpAmplifier(PotionResolver.jumpLevel(player))
                .slowAmplifier(PotionResolver.slownessLevel(player))
                .flyingGapMs(data.getLastFlyingIntervalMs())
                .timerDebtMs(state.timerDebtMs)
                .noSlowExcess(noSlowExcess)
                .entityPushOffset(0.0D)
                .advantage(advantage)
                .debug("")
                .build();

        List<MovementViolation> violations = classify(state, partial, env, cfg, pendingVelocity, pendingExplosion);
        SimulationResult result = rebuildWithViolations(partial, violations, debug(partial, violations, best));
        state.applyResult(result);
        state.lastPosition = to.clone();
        return result;
    }

    private static List<Candidate> enumerate(VezAntiCheat plugin, Player player, PlayerData data,
                                             PlayerMovementState state, Location from, float moveYaw, Environment env,
                                             boolean serverGround, Vector pendingVelocity, Vector pendingExplosion,
                                             MovementPhysicsConfig cfg, long nowMs) {
        List<Vector> starts = new ArrayList<Vector>();
        Vector base = state.carriedMotion == null ? new Vector() : state.carriedMotion.clone();
        starts.add(base.clone());
        if (pendingVelocity != null) starts.add(base.clone().add(pendingVelocity));
        if (pendingExplosion != null) starts.add(base.clone().add(pendingExplosion));

        List<Vector> withJump = new ArrayList<Vector>(starts);
        if (serverGround && !env.inWater && !env.inLava && !env.inWeb && !env.onClimbable) {
            for (Vector start : starts) {
                Vector jump = start.clone();
                jump.setY(PotionResolver.jumpVelocity(player, cfg));
                if (player.isSprinting()) {
                    // Grim JumpPower: sprint-jump adds a FLAT +0.2 in the (current) yaw direction.
                    double rad = Math.toRadians(moveYaw);
                    jump.setX(jump.getX() - Math.sin(rad) * 0.2D);
                    jump.setZ(jump.getZ() + Math.cos(rad) * 0.2D);
                }
                withJump.add(jump);
            }
        }

        double accel = inputAcceleration(player, env, serverGround, cfg);
        double inputScale = inputScale(player, data, nowMs);
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (Vector start : withJump) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                for (int forward = -1; forward <= 1; forward++) {
                    Vector motion = start.clone().add(inputVector(strafe * inputScale, forward * inputScale, accel, moveYaw));
                    candidates.add(move(data.getCompensatedWorld(), from, motion, env, serverGround, cfg));
                }
            }
        }
        return candidates;
    }

    private static Candidate move(BlockProvider provider, Location from, Vector motion, Environment env,
                                  boolean serverGround, MovementPhysicsConfig cfg) {
        Vector pre = motion.clone();
        if (env.inWeb) {
            pre.setX(pre.getX() * cfg.webHorizontalMultiplier);
            pre.setY(pre.getY() * cfg.webVerticalMultiplier);
            pre.setZ(pre.getZ() * cfg.webHorizontalMultiplier);
        }
        if (env.onClimbable) {
            pre.setX(clamp(pre.getX(), -cfg.ladderHorizontalClamp, cfg.ladderHorizontalClamp));
            pre.setZ(clamp(pre.getZ(), -cfg.ladderHorizontalClamp, cfg.ladderHorizontalClamp));
            if (pre.getY() < cfg.ladderDownClamp) pre.setY(cfg.ladderDownClamp);
        }

        Collisions.Result collision = CollisionResolver.collide(provider, from.getX(), from.getY(), from.getZ(),
                pre.getX(), pre.getY(), pre.getZ(), serverGround);
        Vector displacement = new Vector(collision.movedX, collision.movedY, collision.movedZ);

        double nextX = collision.collisionX ? 0.0D : pre.getX();
        double nextY = collision.collisionY ? 0.0D : pre.getY();
        double nextZ = collision.collisionZ ? 0.0D : pre.getZ();
        boolean onGround = collision.onGround || (collision.collisionY && pre.getY() < 0.0D);

        if (env.onClimbable && (collision.collisionX || collision.collisionZ) && pre.getY() < cfg.ladderClimbUp) {
            nextY = cfg.ladderClimbUp;
        } else if (env.inWater || env.inLava) {
            double drag = env.inLava ? cfg.lavaDrag : cfg.waterDrag;
            nextX *= drag;
            nextY = nextY * drag - 0.02D;
            nextZ *= drag;
            if (collision.collisionX || collision.collisionZ) nextY = 0.3D;
        } else {
            if (env.onSlime && collision.collisionY && pre.getY() < -0.1D) {
                nextY = -pre.getY() * 0.9D;
            } else {
                nextY = (nextY - cfg.gravity) * cfg.verticalDrag;
            }
            double friction = FrictionResolver.slipperiness(env.blockBelow, cfg) * cfg.groundFrictionFactor;
            if (!onGround && env.previousIce) {
                friction = Math.max(friction, cfg.iceSlipperiness * cfg.groundFrictionFactor);
            }
            nextX *= friction;
            nextZ *= friction;
        }
        return new Candidate(displacement, new Vector(nextX, nextY, nextZ), onGround,
                collision.collisionX, collision.collisionY, collision.collisionZ, env.surface);
    }

    private static List<MovementViolation> classify(PlayerMovementState state, SimulationResult result,
                                                    Environment env, MovementPhysicsConfig cfg,
                                                    Vector pendingVelocity, Vector pendingExplosion) {
        List<MovementViolation> out = new ArrayList<MovementViolation>();
        if (result.offset > cfg.minorOffset) {
            MovementFamily family = primaryFamily(result, env);
            double confidence = Math.min(1.0D, result.offset / cfg.blatantOffset);
            out.add(new MovementViolation(family, confidence, result.offset,
                    result.expectedMotion, result.actualMotion, state.violationTicks,
                    "offset h=" + round(result.horizontalOffset) + " v=" + round(result.verticalOffset),
                    result.offset >= cfg.moderateOffset));
            if (result.horizontalOffset > cfg.minorOffset && (env.inWater || env.inLava)) {
                out.add(familyViolation(MovementFamily.LIQUID, result, cfg));
            }
            if (result.horizontalOffset > cfg.minorOffset && env.inWeb) {
                out.add(familyViolation(MovementFamily.WEB, result, cfg));
            }
            if (result.verticalOffset > cfg.minorOffset && env.onClimbable) {
                out.add(familyViolation(MovementFamily.CLIMBABLE, result, cfg));
            }
            if (result.horizontalOffset > cfg.minorOffset && env.onIce) {
                out.add(familyViolation(MovementFamily.ICE, result, cfg));
            }
            if (result.verticalOffset > cfg.minorOffset && env.onSlime) {
                out.add(familyViolation(MovementFamily.SLIME, result, cfg));
            }
            if (result.noSlowExcess > 0.0D) {
                out.add(familyViolation(MovementFamily.NOSLOW, result, cfg));
            }
        }
        MovementViolation timer = TimerTracker.violation(state, result, cfg);
        if (timer != null) out.add(timer);
        MovementViolation velocity = VelocityTracker.violation(state, result, pendingVelocity);
        if (velocity != null) out.add(velocity);
        MovementViolation explosion = ExplosionTracker.violation(state, result, pendingExplosion);
        if (explosion != null) out.add(explosion);
        MovementViolation nofall = NofallTracker.violation(state, result);
        if (nofall != null) out.add(nofall);

        if (out.size() >= 2 && result.offset > cfg.moderateOffset) {
            out.add(new MovementViolation(MovementFamily.COMBO, 0.85D, result.offset,
                    result.expectedMotion, result.actualMotion, state.violationTicks,
                    "families=" + out.size(), true));
        }
        return out;
    }

    private static MovementFamily primaryFamily(SimulationResult result, Environment env) {
        if (!result.clientGround && result.verticalOffset > result.horizontalOffset) return MovementFamily.FLY;
        if (result.clientGround && !result.predictedGround) return MovementFamily.GROUND_SPOOF;
        if (env.inWater || env.inLava) return MovementFamily.LIQUID;
        if (env.inWeb) return MovementFamily.WEB;
        if (env.onClimbable) return MovementFamily.CLIMBABLE;
        if (env.onIce) return MovementFamily.FRICTION;
        if (env.onSlime) return MovementFamily.SLIME;
        if (result.verticalOffset > 0.08D) return MovementFamily.JUMP_FALL;
        return MovementFamily.SPEED;
    }

    private static MovementViolation familyViolation(MovementFamily family, SimulationResult result,
                                                     MovementPhysicsConfig cfg) {
        return new MovementViolation(family, Math.min(1.0D, result.offset / cfg.moderateOffset),
                result.offset, result.expectedMotion, result.actualMotion, 0,
                family.name().toLowerCase() + " mismatch", result.offset >= cfg.moderateOffset);
    }

    private static SimulationResult rebuildWithViolations(SimulationResult p, List<MovementViolation> violations,
                                                          String debug) {
        return SimulationResult.builder()
                .timeMs(p.timeMs).tick(p.tick).checked(p.checked).exemptReason(p.exemptReason)
                .worldName(p.worldName).position(p.position).expectedMotion(p.expectedMotion)
                .actualMotion(p.actualMotion).nextMotion(p.nextMotion).offset(p.offset)
                .rawOffset(p.rawOffset).horizontalOffset(p.horizontalOffset).verticalOffset(p.verticalOffset)
                .clientGround(p.clientGround).predictedGround(p.predictedGround)
                .collisionX(p.collisionX).collisionY(p.collisionY).collisionZ(p.collisionZ)
                .inWater(p.inWater).inLava(p.inLava).inWeb(p.inWeb).onClimbable(p.onClimbable)
                .onIce(p.onIce).onSlime(p.onSlime).usingItem(p.usingItem)
                .illegalSprint(p.illegalSprint).illegalSneak(p.illegalSneak).couldSkipTick(p.couldSkipTick)
                .velocityTick(p.velocityTick).explosionTick(p.explosionTick).blockBelow(p.blockBelow)
                .surface(p.surface).speedAmplifier(p.speedAmplifier).jumpAmplifier(p.jumpAmplifier)
                .slowAmplifier(p.slowAmplifier).flyingGapMs(p.flyingGapMs).timerDebtMs(p.timerDebtMs)
                .noSlowExcess(p.noSlowExcess).entityPushOffset(p.entityPushOffset).advantage(p.advantage)
                .violations(violations).debug(debug).build();
    }

    private static Candidate best(List<Candidate> candidates, Vector actual) {
        Candidate best = null;
        double bestDist = Double.MAX_VALUE;
        for (Candidate candidate : candidates) {
            double d = candidate.displacement.distanceSquared(actual);
            if (best == null || d < bestDist) {
                best = candidate;
                bestDist = d;
            }
        }
        return best;
    }

    private static double inputAcceleration(Player player, Environment env, boolean ground, MovementPhysicsConfig cfg) {
        if (env.inWeb) return 0.005D;
        if (env.inWater || env.inLava) return cfg.liquidAcceleration;
        double move = PotionResolver.movementAttribute(player, cfg);
        if (player != null && player.isSprinting()) move *= cfg.sprintMultiplier;
        if (ground) {
            // Grim BlockProperties: groundSpeed = movementSpeed * (0.21600002 / friction^3).
            double friction = FrictionResolver.slipperiness(env.blockBelow, cfg) * cfg.groundFrictionFactor;
            return move * (cfg.groundAccelerationNumerator / (friction * friction * friction));
        }
        // Air acceleration is a flat constant in vanilla/Grim (0.02, or 0.026 sprinting). The movement-speed
        // attribute (Speed potion / elevated walk speed) only affects GROUND movement, so it must NOT scale
        // air acceleration — doing so left an over-lenient gap that an air-speed cheat could hide inside.
        double accel = cfg.airAcceleration;
        if (player != null && player.isSprinting()) accel += cfg.sprintAirAccelerationBonus;
        return accel;
    }

    private static Vector inputVector(double strafe, double forward, double movement, float yaw) {
        double dist = strafe * strafe + forward * forward;
        if (dist < 1.0E-4D) return new Vector();
        dist = Math.sqrt(dist);
        if (dist < 1.0D) dist = 1.0D;
        double factor = movement / dist;
        strafe *= factor;
        forward *= factor;
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        return new Vector(strafe * cos - forward * sin, 0.0D, forward * cos + strafe * sin);
    }

    private static double inputScale(Player player, PlayerData data, long nowMs) {
        if (player != null && player.isSneaking()) return 0.3D;
        if (player != null && (player.isBlocking() || UseItemTracker.isUsingItem(player, data))) {
            return Math.max(0.0D, ItemUseMovementUtil.movementInputScale(data, nowMs));
        }
        return 1.0D;
    }

    private static double advanceAdvantage(double current, double offset, MovementPhysicsConfig cfg) {
        if (offset <= cfg.advantageFloor) {
            return Math.max(0.0D, current - cfg.advantageDecay);
        }
        return Math.min(cfg.advantageCap, current + (offset - cfg.advantageFloor));
    }

    private static SimulationResult exempt(long nowMs, PlayerMovementState state, String reason) {
        return SimulationResult.builder()
                .timeMs(nowMs)
                .tick(state == null ? 0L : state.tick)
                .checked(false)
                .exemptReason(reason)
                .debug("exempt=" + reason)
                .build();
    }

    private static String debug(SimulationResult r, List<MovementViolation> violations, Candidate best) {
        StringBuilder sb = new StringBuilder();
        sb.append("off=").append(round(r.offset)).append('/').append(round(r.rawOffset))
                .append(" hOff=").append(round(r.horizontalOffset))
                .append(" vOff=").append(round(r.verticalOffset))
                .append(" exp=(").append(round(r.expectedMotion.getX())).append(',')
                .append(round(r.expectedMotion.getY())).append(',')
                .append(round(r.expectedMotion.getZ())).append(')')
                .append(" act=(").append(round(r.actualMotion.getX())).append(',')
                .append(round(r.actualMotion.getY())).append(',')
                .append(round(r.actualMotion.getZ())).append(')')
                .append(" g=").append(r.predictedGround).append(" cg=").append(r.clientGround)
                .append(" surface=").append(r.surface)
                .append(" block=").append(r.blockBelow)
                .append(" debt=").append(round(r.timerDebtMs))
                .append(" adv=").append(round(r.advantage))
                .append(" best=").append(best == null ? "none" : best.source);
        if (!violations.isEmpty()) {
            sb.append(" violations=");
            for (int i = 0; i < violations.size(); i++) {
                if (i > 0) sb.append('|');
                sb.append(violations.get(i).shortDebug());
            }
        }
        return sb.toString();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private static final class Candidate {
        final Vector displacement;
        final Vector nextMotion;
        final boolean onGround;
        final boolean collisionX;
        final boolean collisionY;
        final boolean collisionZ;
        final String source;

        Candidate(Vector displacement, Vector nextMotion, boolean onGround,
                  boolean collisionX, boolean collisionY, boolean collisionZ, String source) {
            this.displacement = displacement;
            this.nextMotion = nextMotion;
            this.onGround = onGround;
            this.collisionX = collisionX;
            this.collisionY = collisionY;
            this.collisionZ = collisionZ;
            this.source = source;
        }
    }

    private static final class Environment {
        final Material feet;
        final Material head;
        final Material blockBelow;
        final boolean inWater;
        final boolean inLava;
        final boolean inWeb;
        final boolean onClimbable;
        final boolean onIce;
        final boolean onSlime;
        final boolean previousIce;
        final String surface;

        Environment(Material feet, Material head, Material blockBelow, boolean inWater, boolean inLava,
                    boolean inWeb, boolean onClimbable, boolean onIce, boolean onSlime,
                    boolean previousIce, String surface) {
            this.feet = feet;
            this.head = head;
            this.blockBelow = blockBelow;
            this.inWater = inWater;
            this.inLava = inLava;
            this.inWeb = inWeb;
            this.onClimbable = onClimbable;
            this.onIce = onIce;
            this.onSlime = onSlime;
            this.previousIce = previousIce;
            this.surface = surface;
        }

        static Environment read(BlockProvider world, Location from, MovementPhysicsConfig cfg) {
            int x = floor(from.getX());
            int y = floor(from.getY());
            int z = floor(from.getZ());
            Material feet = world.getType(x, y, z);
            Material head = world.getType(x, y + 1, z);
            Material below = world.getType(x, y - 1, z);
            boolean water = CollisionResolver.isWater(feet) || CollisionResolver.isWater(head);
            boolean lava = CollisionResolver.isLava(feet) || CollisionResolver.isLava(head);
            boolean web = feet == Material.WEB || head == Material.WEB;
            boolean climb = CollisionResolver.isClimbable(feet) || CollisionResolver.isClimbable(head);
            boolean ice = below == Material.ICE || below == Material.PACKED_ICE;
            boolean slime = below == Material.SLIME_BLOCK;
            String surface = FrictionResolver.surface(below, water, lava, web, climb);
            return new Environment(feet, head, below, water, lava, web, climb, ice, slime, ice, surface);
        }
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
