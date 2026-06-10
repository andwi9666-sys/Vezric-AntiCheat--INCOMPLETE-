package com.colin.vezanticheat.velocity;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * VelocityPredictionEngine — Physics simulation for knockback trajectory prediction.
 *
 * When a player receives knockback (from combat, explosion, or projectile), this engine
 * predicts where they SHOULD end up each tick for the next 8 ticks. It explores multiple
 * "branches" of possible player inputs (sprint, jump, strafe, counter-input, sneak) to
 * create an envelope of all valid positions.
 *
 * Architecture:
 * =============
 * 1. simulate() is called with a VelocitySnapshot (velocity vector + player state)
 * 2. createBranches() generates ~15-20 SimState variants for different input combinations
 * 3. For each tick (0 to predictionTicks), each branch is stepped through applyPhysics()
 * 4. Results are merged via unionBranch() into a PredictedTick envelope per tick
 * 5. The envelope defines the min/max X/Y/Z the player can legally reach
 *
 * Physics Replication (Vanilla 1.8.8):
 * =====================================
 * The physics in applyPhysics() match net.minecraft.server EntityLiving.moveEntityWithHeading():
 *
 * Normal movement order:
 *   1. Get slip factor from block below (blockBelow.slipperiness * 0.91)
 *   2. Compute acceleration (0.16277136 / slip^3 for ground, 0.02 for air)
 *   3. Apply input via moveFlying() (normalize input, scale by acceleration, rotate by yaw)
 *   4. Apply web multiplier if in web
 *   5. moveEntity() — collision resolution with AABB checks
 *   6. Ladder clamping (±0.15 horizontal, -0.15 vertical max fall)
 *   7. Post-movement: motionY = (motionY - 0.08) * 0.98; motionX/Z *= slipFactor
 *
 * Water movement:
 *   moveFlying with 0.02 accel → moveEntity → multiply by 0.8 → subtract 0.02 gravity
 *   + wall swim-up (vy = 0.3 on horizontal collision)
 *
 * Special cases:
 *   - Slime block bounce: motionY = -motionY on landing (if not sneaking)
 *   - Sprint jump: +0.2 blocks/tick in facing direction on jump
 *   - Jump boost: +0.1 per level added to jump velocity (base 0.42)
 *   - Step-up: 0.5 blocks (player can walk up half-blocks without jumping)
 *
 * Collision System:
 * =================
 * collectCollisionBoxes() gathers all block AABBs near the player's path.
 * moveEntity() resolves collisions by testing Y first, then X/Z, with step-up fallback.
 * Block shapes are handled per-material (stairs, slabs, fences, doors, trapdoors, etc.)
 *
 * Tolerance System:
 * =================
 * Each PredictedTick includes horizontal and vertical tolerance values that expand
 * the envelope to account for:
 * - Ping (higher ping = more tolerance, capped)
 * - TPS drops (server lag expands tolerance)
 * - Input uncertainty (we don't know exact key states)
 *
 * This class is used by both VelocityProcessor (for KB validation) and
 * PredictionProcessor (for general movement simulation via simulateMovementTick).
 */
public final class VelocityPredictionEngine {

    private static final double PLAYER_HALF_WIDTH = 0.30D;
    private static final double PLAYER_HEIGHT = 1.80D;
    // Minecraft 1.8.8 player step height is 0.5 (EntityPlayer -> EntityLiving.stepHeight)
    private static final double STEP_HEIGHT = 0.50D;
    private static final double COLLISION_EPSILON = 1.0E-7D;
    private static final double GRAVITY = 0.08D;
    private static final double AIR_DRAG_Y = 0.98D;
    private static final double AIR_FRICTION_H = 0.91D;

    private VelocityPredictionEngine() {}

    public static Vector horizontalDirection(Vector velocity) {
        if (velocity == null) return null;
        double h = Math.hypot(velocity.getX(), velocity.getZ());
        if (h <= 1.0E-6) return null;
        return new Vector(velocity.getX() / h, 0.0, velocity.getZ() / h);
    }

    public static List<PredictedTick> simulate(VezAntiCheat plugin, VelocitySnapshot snapshot) {
        List<PredictedTick> ticks = new ArrayList<PredictedTick>();
        if (plugin == null || snapshot == null || snapshot.startLocation == null) return ticks;

        int predictionTicks = plugin.getConfig().getInt("prediction.velocity.prediction-ticks",
                plugin.getConfig().getInt("velocity-engine.prediction-ticks", 8));
        double tolH = plugin.getConfig().getDouble("prediction.velocity.horizontal-tolerance",
                plugin.getConfig().getDouble("velocity-engine.horizontal-tolerance", 0.35D));
        double tolV = plugin.getConfig().getDouble("prediction.velocity.vertical-tolerance",
                plugin.getConfig().getDouble("velocity-engine.vertical-tolerance", 0.25D));

        double pingAllowance = Math.min(0.30, snapshot.ping * 0.0015);
        double tps = snapshot.tps <= 0.0 ? 20.0 : snapshot.tps;
        double tpsAllowance = tps < 19.0 ? (19.0 - tps) * 0.04 : 0.0;
        tolH += pingAllowance;
        tolV += pingAllowance * 0.65 + tpsAllowance;

        double sprintAssist = plugin.getConfig().getDouble("prediction.velocity.physics.sprint-assist",
                plugin.getConfig().getDouble("velocity-engine.physics.sprint-assist", 0.06D));
        double jumpVelocity = plugin.getConfig().getDouble("prediction.velocity.physics.jump-velocity",
                plugin.getConfig().getDouble("velocity-engine.physics.jump-velocity", 0.42D));
        double counterInput = plugin.getConfig().getDouble("prediction.velocity.physics.counter-input",
                plugin.getConfig().getDouble("velocity-engine.physics.counter-input", 0.20D));
        double groundAcceleration = plugin.getConfig().getDouble("prediction.velocity.physics.ground-acceleration", 0.10D);
        double airAcceleration = plugin.getConfig().getDouble("prediction.velocity.physics.air-acceleration", 0.02D);
        double sneakMultiplier = plugin.getConfig().getDouble("prediction.velocity.physics.sneak-multiplier", 0.30D);

        Location base = snapshot.startLocation;
        Vector kb = snapshot.velocity.clone();
        Vector kbDir = horizontalDirection(kb);
        Vector forward = horizontalFacing(base, kbDir);
        Vector lateral = rightOf(forward);
        List<SimState> branches = createBranches(plugin, base, kb, snapshot, kbDir, lateral, sprintAssist, jumpVelocity,
                counterInput, groundAcceleration, airAcceleration, sneakMultiplier, forward);

        for (int tick = 0; tick < predictionTicks; tick++) {
            PredictedTick envelope = null;
            for (SimState branch : branches) {
                envelope = unionBranch(envelope, stepBranch(branch, snapshot, tolH, tolV, tick));
            }
            if (envelope != null) ticks.add(envelope);
        }
        return ticks;
    }

    public static PredictedTick simulateMovementTick(VezAntiCheat plugin, VelocitySnapshot snapshot, double tolH, double tolV) {
        if (plugin == null || snapshot == null || snapshot.startLocation == null) return null;

        double sprintAssist = plugin.getConfig().getDouble("prediction.velocity.physics.sprint-assist",
                plugin.getConfig().getDouble("velocity-engine.physics.sprint-assist", 0.06D));
        double jumpVelocity = plugin.getConfig().getDouble("prediction.velocity.physics.jump-velocity",
                plugin.getConfig().getDouble("velocity-engine.physics.jump-velocity", 0.42D));
        double counterInput = plugin.getConfig().getDouble("prediction.velocity.physics.counter-input",
                plugin.getConfig().getDouble("velocity-engine.physics.counter-input", 0.20D));
        double groundAcceleration = plugin.getConfig().getDouble("prediction.velocity.physics.ground-acceleration", 0.10D);
        double airAcceleration = plugin.getConfig().getDouble("prediction.velocity.physics.air-acceleration", 0.02D);
        double sneakMultiplier = plugin.getConfig().getDouble("prediction.velocity.physics.sneak-multiplier", 0.30D);

        Vector kb = snapshot.velocity.clone();
        Vector kbDir = horizontalDirection(kb);
        Vector forward = horizontalFacing(snapshot.startLocation, kbDir);
        Vector lateral = rightOf(forward);
        List<SimState> branches = createBranches(plugin, snapshot.startLocation, kb, snapshot, kbDir, lateral, sprintAssist,
                jumpVelocity, counterInput, groundAcceleration, airAcceleration, sneakMultiplier, forward);

        PredictedTick envelope = null;
        for (SimState branch : branches) {
            envelope = unionBranch(envelope, stepBranch(branch, snapshot, tolH, tolV, 0));
        }
        return envelope;
    }

    private static PredictedTick unionBranch(PredictedTick envelope, PredictedTick branch) {
        if (branch == null) return envelope;
        return envelope == null ? branch : envelope.union(branch);
    }

    private static PredictedTick stepBranch(SimState state, VelocitySnapshot snapshot, double tolH, double tolV, int tick) {
        applyPhysics(state, snapshot, tick);
        return PredictedTick.fromPoint(tick, state.x, state.y, state.z, tolH, tolV);
    }

    /**
     * Applies one tick of Minecraft 1.8.8 physics matching EntityLiving.moveEntityWithHeading().
     *
     * Vanilla order:
     * 1. Compute slip factor from block BELOW current position (before movement)
     * 2. Compute movement acceleration from slip factor
     * 3. Apply input acceleration (moveFlying)
     * 4. Apply web multipliers (if in web)
     * 5. moveEntity (collision resolution)
     * 6. Apply post-move friction: motionX/Z *= slipFactor; motionY = (motionY - gravity) * airDrag
     * 7. Ladder clamping
     */
    private static void applyPhysics(SimState state, VelocitySnapshot snapshot, int tick) {
        if (snapshot.startLocation == null || snapshot.startLocation.getWorld() == null) {
            state.x += state.vx;
            state.y += state.vy;
            state.z += state.vz;
            return;
        }

        World world = snapshot.startLocation.getWorld();

        if (snapshot.inLiquid) {
            // Vanilla water physics (EntityLiving.moveEntityWithHeading in water):
            // 1. moveFlying(strafe, forward, 0.02) — same as air acceleration
            // 2. moveEntity(vx, vy, vz)
            // 3. vx *= 0.8; vy *= 0.8; vz *= 0.8
            // 4. vy -= 0.02 (water gravity, weaker than air)
            // 5. If collided horizontally and isOffsetPositionInLiquid, vy = 0.3 (swim up walls)
            applyInputAcceleration(state, snapshot, tick);
            MoveResult moved = moveEntity(world, state, state.vx, state.vy, state.vz);
            state.x = moved.x;
            state.y = moved.y;
            state.z = moved.z;
            state.onGround = moved.onGround;
            if (moved.xCollision) state.vx = 0.0D;
            if (moved.yCollision) state.vy = 0.0D;
            if (moved.zCollision) state.vz = 0.0D;
            state.vx *= 0.80D;
            state.vy *= 0.80D;
            state.vz *= 0.80D;
            state.vy -= 0.02D;
            // Swim up wall: if horizontally collided while in water, player can bob up
            if (moved.xCollision || moved.zCollision) {
                state.vy = 0.30D;
            }
            return;
        }

        // Step 1: Get slip factor from block below BEFORE this tick's movement
        // In vanilla: float f2 = 0.91F; if(onGround) f2 = worldObj.getBlock(...).slipperiness * 0.91F
        double slipFactor = state.onGround ? getGroundFriction(world, state.x, state.y, state.z) : AIR_FRICTION_H;

        // Step 2: Compute acceleration from slip factor
        // In vanilla: float f3 = 0.16277136F / (f2 * f2 * f2)
        // If onGround: acceleration = landMovementFactor * f3 (landMovementFactor = 0.1 base)
        // If airborne: acceleration = 0.02 (jumpMovementFactor)
        applyInputAcceleration(state, snapshot, tick);

        // Step 3: Web multiplier (applied to velocity BEFORE movement)
        if (snapshot.inWeb) {
            state.vx *= 0.25D;
            state.vy *= 0.05D;
            state.vz *= 0.25D;
        }

        // Step 4: moveEntity - collision resolution
        MoveResult moved = moveEntity(world, state, state.vx, state.vy, state.vz);
        state.x = moved.x;
        state.y = moved.y;
        state.z = moved.z;
        state.onGround = moved.onGround;

        if (moved.xCollision) state.vx = 0.0D;
        if (moved.yCollision) {
            if (state.vy < 0.0D) {
                state.onGround = true;
                // Slime block bounce: reflect Y velocity (vanilla: motionY = -motionY if not sneaking)
                if (snapshot.onSlime && !snapshot.sneaking) {
                    state.vy = -state.vy;
                } else {
                    state.vy = 0.0D;
                }
            } else {
                state.vy = 0.0D;
            }
        }
        if (moved.zCollision) state.vz = 0.0D;

        // Step 5: Ladder/vine clamping (after moveEntity, before friction)
        if (isClimbable(world, state.x, state.y, state.z)) {
            state.vx = clamp(state.vx, -0.15D, 0.15D);
            state.vz = clamp(state.vz, -0.15D, 0.15D);
            // Vanilla: if holding space on ladder, vy is clamped to max -0.15 (slow fall)
            // If pressing jump key: vy stays at 0.0 or goes positive
            state.vy = Math.max(state.vy, -0.15D);
        }

        // Step 6: Post-movement friction and gravity
        // In vanilla: motionY -= 0.08; motionY *= 0.98; motionX *= slipFactor; motionZ *= slipFactor
        state.vy = (state.vy - GRAVITY) * AIR_DRAG_Y;
        state.vx *= slipFactor;
        state.vz *= slipFactor;

        // Slowness effect (post-friction multiplier)
        if (snapshot.slowAmp > 0) {
            double slow = Math.max(0.5D, 1.0D - (0.12D * snapshot.slowAmp));
            state.vx *= slow;
            state.vz *= slow;
        }
    }

    /**
     * Vanilla moveFlying() equivalent.
     * In 1.8.8: moveFlying(strafe, forward, friction) applies:
     *   dist = strafe^2 + forward^2
     *   if (dist >= 1.0E-4) normalize then * friction
     *   motionX += resultX; motionZ += resultZ
     *
     * The friction param here is the "movement acceleration" which depends on ground state:
     * - On ground: landMovementFactor * (0.16277136 / (slip^3))
     *   where landMovementFactor = 0.1 * (1 + sprint*0.3) * (1 + speed*0.2)
     * - In air: jumpMovementFactor = 0.02 (+ sprint bonus 0.005989)
     */
    private static void applyInputAcceleration(SimState state, VelocitySnapshot snapshot, int tick) {
        if (state.inputX == 0.0D && state.inputZ == 0.0D) {
            return;
        }

        double acceleration;
        if (state.onGround) {
            // Vanilla ground acceleration:
            // slipFactor = blockBelow.slipperiness * 0.91
            // f3 = 0.16277136 / (slipFactor^3)
            // acceleration = landMovementFactor * f3
            // landMovementFactor base = 0.1 (getAIMoveSpeed() / 2 for default walk speed 0.2)
            double landMovementFactor = 0.1D;
            if (snapshot.sprinting && state.forwardBranch) {
                landMovementFactor *= 1.3D;
            }
            if (snapshot.speedAmp > 0) {
                landMovementFactor *= (1.0D + (snapshot.speedAmp * 0.2D));
            }
            if (snapshot.slowAmp > 0) {
                landMovementFactor *= Math.max(0.3D, 1.0D - (snapshot.slowAmp * 0.15D));
            }
            if (snapshot.blocking) {
                landMovementFactor *= 0.2D;
            }
            if (state.sneakingBranch) {
                landMovementFactor *= 0.3D;
            }
            double slipFactor = getGroundFriction(
                    snapshot.startLocation.getWorld(), state.x, state.y, state.z);
            double f3 = 0.16277136D / (slipFactor * slipFactor * slipFactor);
            acceleration = landMovementFactor * f3;
        } else {
            // Vanilla air acceleration: jumpMovementFactor = 0.02
            acceleration = 0.02D;
            if (snapshot.sprinting && state.forwardBranch) {
                acceleration += 0.005989D;
            }
        }

        // Sprint jump boost on first tick
        if (tick == 0 && state.jumpBranch && snapshot.onGround && snapshot.sprinting && state.forwardBranch) {
            // In vanilla: jump() adds sprint boost as velocity in facing direction
            // +0.2 * sin/cos(yaw) — we model this as extra acceleration
            acceleration *= 1.0D; // Already handled by jump branch adding sprint velocity
        }

        acceleration *= state.inputScale;

        // moveFlying: normalize input then multiply by acceleration
        double dist = (state.inputX * state.inputX) + (state.inputZ * state.inputZ);
        if (dist >= 1.0E-4D) {
            dist = Math.sqrt(dist);
            if (dist < 1.0D) dist = 1.0D;
            double factor = acceleration / dist;
            state.vx += state.inputX * factor;
            state.vz += state.inputZ * factor;
        }
    }

    private static List<SimState> createBranches(VezAntiCheat plugin, Location base, Vector kb, VelocitySnapshot snapshot, Vector kbDir, Vector lateral,
                                                 double sprintAssist, double jumpVelocity, double counterInput,
                                                 double groundAcceleration, double airAcceleration, double sneakMultiplier,
                                                 Vector forward) {
        List<SimState> branches = new ArrayList<SimState>();
        SimState pure = SimState.from(base, kb, snapshot.onGround);
        pure.groundAcceleration = groundAcceleration;
        pure.airAcceleration = airAcceleration;
        pure.sneakMultiplier = sneakMultiplier;
        branches.add(pure);

        double forwardScale = 1.0D;
        double strafeScale = plugin.getConfig().getDouble("prediction.velocity.physics.strafe-input-scale", 0.92D);
        double backwardScale = plugin.getConfig().getDouble("prediction.velocity.physics.backward-input-scale", 0.88D);
        double forwardDiagonalScale = plugin.getConfig().getDouble("prediction.velocity.physics.forward-diagonal-input-scale", 0.96D);
        double counterScale = plugin.getConfig().getDouble("prediction.velocity.physics.counter-input-scale", 0.78D);

        Vector backward = forward.clone().multiply(-1.0D);
        Vector left = lateral.clone().multiply(-1.0D);
        Vector right = lateral.clone();

        if (snapshot.sprinting && !snapshot.restrictive) {
            SimState sprint = pure.copy();
            sprint.vx += forward.getX() * sprintAssist;
            sprint.vz += forward.getZ() * sprintAssist;
            sprint.forwardBranch = true;
            branches.add(sprint);
        }

        branches.add(withInput(pure, forward, false, true, forwardScale));
        if (!snapshot.restrictive) {
            branches.add(withInput(pure, backward, false, false, backwardScale));
            branches.add(withInput(pure, left, false, false, strafeScale));
            branches.add(withInput(pure, right, false, false, strafeScale));
            branches.add(withInput(pure, combine(backward, left), false, false, Math.min(backwardScale, strafeScale)));
            branches.add(withInput(pure, combine(backward, right), false, false, Math.min(backwardScale, strafeScale)));
        }
        branches.add(withInput(pure, combine(forward, left), false, true, forwardDiagonalScale));
        branches.add(withInput(pure, combine(forward, right), false, true, forwardDiagonalScale));
        if (snapshot.sneaking || snapshot.blocking || snapshot.restrictive) {
            branches.add(withInput(pure, forward, true, true, forwardScale));
            branches.add(withInput(pure, combine(forward, left), true, true, forwardDiagonalScale));
            branches.add(withInput(pure, combine(forward, right), true, true, forwardDiagonalScale));
        }

        if (kbDir != null && !snapshot.restrictive) {
            SimState counter = pure.copy();
            counter.vx -= forward.getX() * counterInput * counterScale;
            counter.vz -= forward.getZ() * counterInput * counterScale;
            branches.add(counter);
        }

        if (snapshot.onGround && !snapshot.inLiquid && !snapshot.inWeb) {
            SimState jump = pure.copy();
            jump.vy = Math.max(jump.vy, jumpVelocity + (snapshot.jumpAmp * 0.1D));
            jump.onGround = false;
            jump.jumpBranch = true;
            // Vanilla sprint jump: motionX += -sin(yaw) * 0.2; motionZ += cos(yaw) * 0.2
            if (snapshot.sprinting) {
                jump.vx += forward.getX() * 0.2D;
                jump.vz += forward.getZ() * 0.2D;
            }
            branches.add(jump);
            branches.add(withJumpInput(jump, forward, false, true, forwardScale));
            if (!snapshot.restrictive) {
                branches.add(withJumpInput(jump, combine(forward, left), false, true, forwardDiagonalScale));
                branches.add(withJumpInput(jump, combine(forward, right), false, true, forwardDiagonalScale));
            }
            if (snapshot.sneaking || snapshot.blocking || snapshot.restrictive) {
                branches.add(withJumpInput(jump, forward, true, true, forwardScale));
            }
        }

        return branches;
    }

    private static SimState withInput(SimState base, Vector direction, boolean sneaking, boolean forwardBranch, double inputScale) {
        SimState branch = base.copy();
        branch.inputX = direction.getX();
        branch.inputZ = direction.getZ();
        branch.sneakingBranch = sneaking;
        branch.forwardBranch = forwardBranch;
        branch.inputScale = inputScale;
        return branch;
    }

    private static SimState withJumpInput(SimState base, Vector direction, boolean sneaking, boolean forwardBranch, double inputScale) {
        SimState branch = withInput(base, direction, sneaking, forwardBranch, inputScale);
        branch.jumpBranch = true;
        return branch;
    }

    private static Vector combine(Vector a, Vector b) {
        Vector combined = a.clone().add(b);
        if (combined.lengthSquared() <= 1.0E-6D) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }
        return combined.normalize();
    }

    private static Vector horizontalFacing(Location loc, Vector fallback) {
        if (loc == null) {
            return fallback == null ? new Vector(0.0D, 0.0D, 1.0D) : fallback.clone();
        }
        double radians = Math.toRadians(loc.getYaw());
        Vector facing = new Vector(-Math.sin(radians), 0.0D, Math.cos(radians));
        if (facing.lengthSquared() <= 1.0E-6D) {
            return fallback == null ? new Vector(0.0D, 0.0D, 1.0D) : fallback.clone();
        }
        return facing.normalize();
    }

    private static Vector rightOf(Vector forward) {
        if (forward == null || forward.lengthSquared() <= 1.0E-6D) {
            return new Vector(1.0D, 0.0D, 0.0D);
        }
        return new Vector(-forward.getZ(), 0.0D, forward.getX()).normalize();
    }

    public static Location closestValidLocation(List<PredictedTick> ticks, int tickIndex, double x, double y, double z) {
        if (ticks == null || ticks.isEmpty()) return null;
        int idx = Math.max(0, Math.min(ticks.size() - 1, tickIndex));
        PredictedTick tick = ticks.get(idx);
        PredictedTick.LocationPoint point = tick.closestPoint(x, y, z);
        Location ref = new Location(null, point.x, point.y, point.z);
        return ref;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static MoveResult moveEntity(World world, SimState state, double dx, double dy, double dz) {
        PlayerBox original = PlayerBox.at(state.x, state.y, state.z);
        List<AxisAlignedBox> collisions = collectCollisionBoxes(world, original.expand(dx, dy, dz).grow(0.001D));

        MoveResult directA = collideWithOrder(original, collisions, dx, dy, dz, true);
        MoveResult directB = collideWithOrder(original, collisions, dx, dy, dz, false);
        MoveResult direct = directA.horizontalDistanceSquared() >= directB.horizontalDistanceSquared() ? directA : directB;

        boolean blockedHorizontally = direct.xCollision || direct.zCollision;
        boolean canStep = blockedHorizontally && (state.onGround || dy < 0.0D);
        if (!canStep) {
            return direct;
        }

        List<AxisAlignedBox> stepCollisions = collectCollisionBoxes(world, original.expand(dx, STEP_HEIGHT, dz).grow(0.001D));
        MoveResult stepA = collideStep(original, stepCollisions, dx, dy, dz, true);
        MoveResult stepB = collideStep(original, stepCollisions, dx, dy, dz, false);
        MoveResult stepped = stepA.horizontalDistanceSquared() >= stepB.horizontalDistanceSquared() ? stepA : stepB;
        return stepped.horizontalDistanceSquared() > direct.horizontalDistanceSquared() ? stepped : direct;
    }

    private static MoveResult collideStep(PlayerBox box, List<AxisAlignedBox> collisions, double dx, double dy, double dz, boolean xThenZ) {
        double up = STEP_HEIGHT;
        for (AxisAlignedBox block : collisions) {
            up = block.computeOffsetY(box, up);
        }
        PlayerBox stepped = box.offset(0.0D, up, 0.0D);
        MoveResult horizontal = collideWithOrder(stepped, collisions, dx, 0.0D, dz, xThenZ);
        double down = dy - up;
        PlayerBox afterHorizontal = PlayerBox.at(horizontal.x, horizontal.y, horizontal.z);
        double resolvedDown = down;
        for (AxisAlignedBox block : collisions) {
            resolvedDown = block.computeOffsetY(afterHorizontal, resolvedDown);
        }
        afterHorizontal = afterHorizontal.offset(0.0D, resolvedDown, 0.0D);
        return new MoveResult(
                afterHorizontal.centerX(),
                afterHorizontal.minY,
                afterHorizontal.centerZ(),
                horizontal.movedX,
                up + resolvedDown,
                horizontal.movedZ,
                horizontal.xCollision,
                resolvedDown != down,
                horizontal.zCollision,
                resolvedDown != down && down < 0.0D
        );
    }

    private static MoveResult collideWithOrder(PlayerBox box, List<AxisAlignedBox> collisions, double dx, double dy, double dz, boolean xThenZ) {
        double movedX = dx;
        double movedY = dy;
        double movedZ = dz;

        for (AxisAlignedBox block : collisions) {
            movedY = block.computeOffsetY(box, movedY);
        }
        box = box.offset(0.0D, movedY, 0.0D);

        if (xThenZ) {
            for (AxisAlignedBox block : collisions) {
                movedX = block.computeOffsetX(box, movedX);
            }
            box = box.offset(movedX, 0.0D, 0.0D);
            for (AxisAlignedBox block : collisions) {
                movedZ = block.computeOffsetZ(box, movedZ);
            }
            box = box.offset(0.0D, 0.0D, movedZ);
        } else {
            for (AxisAlignedBox block : collisions) {
                movedZ = block.computeOffsetZ(box, movedZ);
            }
            box = box.offset(0.0D, 0.0D, movedZ);
            for (AxisAlignedBox block : collisions) {
                movedX = block.computeOffsetX(box, movedX);
            }
            box = box.offset(movedX, 0.0D, 0.0D);
        }

        return new MoveResult(
                box.centerX(),
                box.minY,
                box.centerZ(),
                movedX,
                movedY,
                movedZ,
                movedX != dx,
                movedY != dy,
                movedZ != dz,
                movedY != dy && dy < 0.0D
        );
    }

    private static List<AxisAlignedBox> collectCollisionBoxes(World world, PlayerBox search) {
        if (world == null) return Collections.emptyList();
        List<AxisAlignedBox> boxes = new ArrayList<AxisAlignedBox>();
        int minX = floor(search.minX) - 1;
        int maxX = floor(search.maxX) + 1;
        int minY = floor(search.minY) - 1;
        int maxY = floor(search.maxY) + 1;
        int minZ = floor(search.minZ) - 1;
        int maxZ = floor(search.maxZ) + 1;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Chunk loading validation: skip unloaded chunks
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block == null) continue;
                    addBlockBoxes(block, boxes, search);
                }
            }
        }
        return boxes;
    }

    private static void addBlockBoxes(Block block, List<AxisAlignedBox> into, PlayerBox search) {
        Material material = block.getType();
        if (material == null || isNonCollidable(material)) return;

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        List<AxisAlignedBox> local = new ArrayList<AxisAlignedBox>(2);
        byte data = block.getData();

        if (material == Material.SOUL_SAND) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 0.875D, z + 1.0D));
        } else if (material == Material.WEB) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
        } else if (material == Material.LADDER) {
            addLadderBox(x, y, z, data, local);
        } else if (material == Material.CARPET) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 0.0625D, z + 1.0D));
        } else if (material == Material.SNOW) {
            double height = Math.min(1.0D, ((data & 7) + 1) / 8.0D);
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + height, z + 1.0D));
        } else if (material == Material.CACTUS) {
            local.add(new AxisAlignedBox(x + 0.0625D, y, z + 0.0625D, x + 0.9375D, y + 1.0D, z + 0.9375D));
        } else if (material == Material.BED_BLOCK) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 0.5625D, z + 1.0D));
        } else if (isFenceLike(material)) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.5D, z + 1.0D));
        } else if (isFenceGate(material)) {
            addFenceGateBoxes(block, local);
        } else if (isPane(material)) {
            addPaneBoxes(block, local);
        } else if (isTrapDoor(material)) {
            addTrapDoorBoxes(x, y, z, data, local);
        } else if (isDoor(material)) {
            addDoorBoxes(block, local);
        } else if (isSlab(material)) {
            boolean top = (data & 8) != 0;
            if (isDoubleSlab(material)) {
                local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else if (top) {
                local.add(new AxisAlignedBox(x, y + 0.5D, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else {
                local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 0.5D, z + 1.0D));
            }
        } else if (isStair(material)) {
            addStairBoxes(x, y, z, data, local);
        } else if (material.isSolid()) {
            local.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
        }

        for (AxisAlignedBox box : local) {
            if (box.intersects(search)) {
                into.add(box);
            }
        }
    }

    private static void addLadderBox(int x, int y, int z, byte data, List<AxisAlignedBox> boxes) {
        double thickness = 0.125D;
        switch (data) {
            case 2:
                boxes.add(new AxisAlignedBox(x, y, z + (1.0D - thickness), x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            case 3:
                boxes.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + thickness));
                break;
            case 4:
                boxes.add(new AxisAlignedBox(x + (1.0D - thickness), y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            default:
                boxes.add(new AxisAlignedBox(x, y, z, x + thickness, y + 1.0D, z + 1.0D));
                break;
        }
    }

    private static void addFenceGateBoxes(Block block, List<AxisAlignedBox> boxes) {
        byte data = block.getData();
        boolean open = (data & 4) != 0;
        if (open) {
            return;
        }
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        double thickness = 0.25D;
        int orientation = data & 3;
        if (orientation == 0 || orientation == 2) {
            boxes.add(new AxisAlignedBox(x, y, z + 0.5D - thickness, x + 1.0D, y + 1.5D, z + 0.5D + thickness));
        } else {
            boxes.add(new AxisAlignedBox(x + 0.5D - thickness, y, z, x + 0.5D + thickness, y + 1.5D, z + 1.0D));
        }
    }

    private static void addPaneBoxes(Block block, List<AxisAlignedBox> boxes) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        double min = 0.4375D;
        double max = 0.5625D;
        boxes.add(new AxisAlignedBox(x + min, y, z + min, x + max, y + 1.0D, z + max));

        if (connectsToPane(block.getRelative(1, 0, 0))) {
            boxes.add(new AxisAlignedBox(x + max, y, z + min, x + 1.0D, y + 1.0D, z + max));
        }
        if (connectsToPane(block.getRelative(-1, 0, 0))) {
            boxes.add(new AxisAlignedBox(x, y, z + min, x + min, y + 1.0D, z + max));
        }
        if (connectsToPane(block.getRelative(0, 0, 1))) {
            boxes.add(new AxisAlignedBox(x + min, y, z + max, x + max, y + 1.0D, z + 1.0D));
        }
        if (connectsToPane(block.getRelative(0, 0, -1))) {
            boxes.add(new AxisAlignedBox(x + min, y, z, x + max, y + 1.0D, z + min));
        }
    }

    private static boolean connectsToPane(Block block) {
        if (block == null) return false;
        Material material = block.getType();
        return material != null && (material.isSolid() || isPane(material));
    }

    private static void addTrapDoorBoxes(int x, int y, int z, byte data, List<AxisAlignedBox> boxes) {
        boolean open = (data & 4) != 0;
        boolean top = (data & 8) != 0;
        double thickness = 0.1875D;
        if (!open) {
            if (top) {
                boxes.add(new AxisAlignedBox(x, y + 1.0D - thickness, z, x + 1.0D, y + 1.0D, z + 1.0D));
            } else {
                boxes.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + thickness, z + 1.0D));
            }
            return;
        }

        switch (data & 3) {
            case 0:
                boxes.add(new AxisAlignedBox(x, y, z + 1.0D - thickness, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            case 1:
                boxes.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + thickness));
                break;
            case 2:
                boxes.add(new AxisAlignedBox(x + 1.0D - thickness, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            default:
                boxes.add(new AxisAlignedBox(x, y, z, x + thickness, y + 1.0D, z + 1.0D));
                break;
        }
    }

    private static void addDoorBoxes(Block block, List<AxisAlignedBox> boxes) {
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        Block base = block;
        byte data = block.getData();
        if ((data & 8) != 0) {
            base = block.getRelative(0, -1, 0);
            data = base.getData();
        }

        boolean open = (data & 4) != 0;
        int facing = data & 3;
        double thickness = 0.1875D;

        int effective = facing;
        if (open) {
            switch (facing) {
                case 0:
                    effective = 1;
                    break;
                case 1:
                    effective = 2;
                    break;
                case 2:
                    effective = 3;
                    break;
                default:
                    effective = 0;
                    break;
            }
        }

        switch (effective) {
            case 0:
                boxes.add(new AxisAlignedBox(x, y, z, x + thickness, y + 1.0D, z + 1.0D));
                break;
            case 1:
                boxes.add(new AxisAlignedBox(x, y, z, x + 1.0D, y + 1.0D, z + thickness));
                break;
            case 2:
                boxes.add(new AxisAlignedBox(x + 1.0D - thickness, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
            default:
                boxes.add(new AxisAlignedBox(x, y, z + 1.0D - thickness, x + 1.0D, y + 1.0D, z + 1.0D));
                break;
        }
    }

    private static void addStairBoxes(int x, int y, int z, byte data, List<AxisAlignedBox> boxes) {
        boolean top = (data & 4) != 0;
        int facing = data & 3;
        double lowerMinY = top ? 0.5D : 0.0D;
        double lowerMaxY = top ? 1.0D : 0.5D;
        double upperMinY = top ? 0.0D : 0.5D;
        double upperMaxY = top ? 0.5D : 1.0D;

        boxes.add(new AxisAlignedBox(x, y + lowerMinY, z, x + 1.0D, y + lowerMaxY, z + 1.0D));
        switch (facing) {
            case 0:
                boxes.add(new AxisAlignedBox(x + 0.5D, y + upperMinY, z, x + 1.0D, y + upperMaxY, z + 1.0D));
                break;
            case 1:
                boxes.add(new AxisAlignedBox(x, y + upperMinY, z, x + 0.5D, y + upperMaxY, z + 1.0D));
                break;
            case 2:
                boxes.add(new AxisAlignedBox(x, y + upperMinY, z + 0.5D, x + 1.0D, y + upperMaxY, z + 1.0D));
                break;
            default:
                boxes.add(new AxisAlignedBox(x, y + upperMinY, z, x + 1.0D, y + upperMaxY, z + 0.5D));
                break;
        }
    }

    private static boolean isNonCollidable(Material material) {
        if (material == Material.AIR
                || material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA
                || material == Material.VINE) {
            return true;
        }
        String name = material.name();
        return name.contains("SIGN")
                || name.contains("BUTTON")
                || name.contains("PLATE")
                || name.contains("LEVER")
                || name.contains("TORCH")
                || name.contains("FLOWER")
                || name.contains("MUSHROOM")
                || name.contains("SAPLING")
                || name.contains("BANNER");
    }

    private static boolean isFenceLike(Material material) {
        String name = material.name();
        return name.contains("FENCE") || name.contains("WALL");
    }

    private static boolean isSlab(Material material) {
        String name = material.name();
        return name.contains("STEP") || name.contains("SLAB");
    }

    private static boolean isDoubleSlab(Material material) {
        String name = material.name();
        return name.contains("DOUBLE");
    }

    private static boolean isStair(Material material) {
        return material.name().contains("STAIRS");
    }

    private static boolean isPane(Material material) {
        String name = material.name();
        return name.contains("PANE") || material == Material.THIN_GLASS || material == Material.IRON_FENCE;
    }

    private static boolean isFenceGate(Material material) {
        return material == Material.FENCE_GATE;
    }

    private static boolean isTrapDoor(Material material) {
        return material == Material.TRAP_DOOR;
    }

    private static boolean isDoor(Material material) {
        String name = material.name();
        return name.contains("DOOR");
    }

    private static boolean isClimbable(World world, double x, double y, double z) {
        if (world == null) return false;
        Material material = world.getBlockAt(floor(x), floor(y), floor(z)).getType();
        return material == Material.LADDER || material == Material.VINE;
    }

    /**
     * Returns the combined slip factor for the block below the player.
     * Vanilla formula: blockBelow.slipperiness * 0.91
     * Default slipperiness = 0.6, Ice = 0.98, Slime = 0.8
     * Combined: default = 0.546, ice = 0.8918, slime = 0.728
     */
    private static double getGroundFriction(World world, double x, double y, double z) {
        if (world == null) return AIR_FRICTION_H;
        Material below = world.getBlockAt(floor(x), floor(y) - 1, floor(z)).getType();
        if (below == Material.ICE || below == Material.PACKED_ICE) {
            return 0.98D * 0.91D;
        }
        if (below == Material.SLIME_BLOCK) {
            return 0.8D * 0.91D;
        }
        return 0.6D * 0.91D;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static final class SimState {
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        double groundY;
        boolean onGround;
        double inputX;
        double inputZ;
        double groundAcceleration;
        double airAcceleration;
        double sneakMultiplier;
        double inputScale = 1.0D;
        boolean sneakingBranch;
        boolean forwardBranch;
        boolean jumpBranch;

        static SimState from(Location loc, Vector kb, boolean onGround) {
            SimState s = new SimState();
            s.x = loc.getX();
            s.y = loc.getY();
            s.z = loc.getZ();
            s.vx = kb.getX();
            s.vy = kb.getY();
            s.vz = kb.getZ();
            s.onGround = onGround;
            return s;
        }

        SimState copy() {
            SimState s = new SimState();
            s.x = x;
            s.y = y;
            s.z = z;
            s.vx = vx;
            s.vy = vy;
            s.vz = vz;
            s.onGround = onGround;
            s.inputX = inputX;
            s.inputZ = inputZ;
            s.groundAcceleration = groundAcceleration;
            s.airAcceleration = airAcceleration;
            s.sneakMultiplier = sneakMultiplier;
            s.inputScale = inputScale;
            s.sneakingBranch = sneakingBranch;
            s.forwardBranch = forwardBranch;
            s.jumpBranch = jumpBranch;
            return s;
        }
    }

    private static final class MoveResult {
        final double x;
        final double y;
        final double z;
        final double movedX;
        final double movedY;
        final double movedZ;
        final boolean xCollision;
        final boolean yCollision;
        final boolean zCollision;
        final boolean onGround;

        private MoveResult(double x, double y, double z, double movedX, double movedY, double movedZ,
                           boolean xCollision, boolean yCollision, boolean zCollision, boolean onGround) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.movedX = movedX;
            this.movedY = movedY;
            this.movedZ = movedZ;
            this.xCollision = xCollision;
            this.yCollision = yCollision;
            this.zCollision = zCollision;
            this.onGround = onGround;
        }

        private double horizontalDistanceSquared() {
            return (movedX * movedX) + (movedZ * movedZ);
        }
    }

    private static final class PlayerBox {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        private PlayerBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static PlayerBox at(double x, double y, double z) {
            return new PlayerBox(x - PLAYER_HALF_WIDTH, y, z - PLAYER_HALF_WIDTH,
                    x + PLAYER_HALF_WIDTH, y + PLAYER_HEIGHT, z + PLAYER_HALF_WIDTH);
        }

        PlayerBox offset(double x, double y, double z) {
            return new PlayerBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }

        PlayerBox expand(double x, double y, double z) {
            double nMinX = x < 0.0D ? minX + x : minX;
            double nMaxX = x > 0.0D ? maxX + x : maxX;
            double nMinY = y < 0.0D ? minY + y : minY;
            double nMaxY = y > 0.0D ? maxY + y : maxY;
            double nMinZ = z < 0.0D ? minZ + z : minZ;
            double nMaxZ = z > 0.0D ? maxZ + z : maxZ;
            return new PlayerBox(nMinX, nMinY, nMinZ, nMaxX, nMaxY, nMaxZ);
        }

        PlayerBox grow(double amount) {
            return new PlayerBox(minX - amount, minY - amount, minZ - amount,
                    maxX + amount, maxY + amount, maxZ + amount);
        }

        double centerX() {
            return (minX + maxX) * 0.5D;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }
    }

    private static final class AxisAlignedBox {
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        private AxisAlignedBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private boolean intersects(PlayerBox box) {
            return box.maxX > minX && box.minX < maxX
                    && box.maxY > minY && box.minY < maxY
                    && box.maxZ > minZ && box.minZ < maxZ;
        }

        private double computeOffsetX(PlayerBox box, double dx) {
            if (box.maxY <= minY || box.minY >= maxY || box.maxZ <= minZ || box.minZ >= maxZ) {
                return dx;
            }
            if (dx > 0.0D && box.maxX <= minX) {
                double max = minX - box.maxX - COLLISION_EPSILON;
                if (max < dx) dx = max;
            } else if (dx < 0.0D && box.minX >= maxX) {
                double min = maxX - box.minX + COLLISION_EPSILON;
                if (min > dx) dx = min;
            }
            return dx;
        }

        private double computeOffsetY(PlayerBox box, double dy) {
            if (box.maxX <= minX || box.minX >= maxX || box.maxZ <= minZ || box.minZ >= maxZ) {
                return dy;
            }
            if (dy > 0.0D && box.maxY <= minY) {
                double max = minY - box.maxY - COLLISION_EPSILON;
                if (max < dy) dy = max;
            } else if (dy < 0.0D && box.minY >= maxY) {
                double min = maxY - box.minY + COLLISION_EPSILON;
                if (min > dy) dy = min;
            }
            return dy;
        }

        private double computeOffsetZ(PlayerBox box, double dz) {
            if (box.maxX <= minX || box.minX >= maxX || box.maxY <= minY || box.minY >= maxY) {
                return dz;
            }
            if (dz > 0.0D && box.maxZ <= minZ) {
                double max = minZ - box.maxZ - COLLISION_EPSILON;
                if (max < dz) dz = max;
            } else if (dz < 0.0D && box.minZ >= maxZ) {
                double min = maxZ - box.minZ + COLLISION_EPSILON;
                if (min > dz) dz = min;
            }
            return dz;
        }
    }
}
