package com.colin.vezanticheat.engine;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * PredictionEngine — the GrimAC-style offset core for 1.8.8 movement.
 *
 * For one tick it:
 *  1. builds a set of candidate "start" velocities (carried client velocity, jump variants,
 *     and any pending knockback/explosion impulse),
 *  2. for each start, enumerates the legal WASD/strafe input results (yaw-rotated, scaled by
 *     ground/air movement speed, sneak and item-use multipliers),
 *  3. expands each candidate by the uncertainty box, cuts that box toward the player's actual
 *     movement, collides the cut vector against the world, and
 *  4. keeps the candidate whose post-collision displacement is closest to actualMovement.
 *
 * The winning candidate's pre-collision vector (after friction/gravity) becomes next tick's
 * carried velocity; its post-collision displacement is {@code predictedVelocity}. The leftover
 * distance between predicted and actual is the offset that OffsetHandler flags on.
 */
public final class PredictionEngine {

    private PredictionEngine() {}

    public static void guessBestMovement(MovementPlayer player, BlockProvider provider) {
        if (player.inWeb) {
            webTravel(player, provider);
            return;
        }
        if (player.inWater) {
            fluidTravel(player, provider, 0.8D, 0.02D);
            return;
        }
        if (player.inLava) {
            fluidTravel(player, provider, 0.5D, 0.02D);
            return;
        }
        normalTravel(player, provider);
    }

    // ---- Cobweb travel (1.8: severe horizontal/vertical slowdown) ----

    private static void webTravel(MovementPlayer player, BlockProvider provider) {
        player.uncertaintyHandler.stuckSpeedTick = true;
        List<VectorData> starts = fetchStartVectors(player);
        // Web reduces movement input to ~25% of normal; use very low accel.
        List<VectorData> candidates = applyInputs(player, starts, 0.005F);
        if (player.pointThreeEstimator.shouldInjectZeroMovement()) {
            candidates.add(new VectorData(new Vector(), VectorData.Type.START));
        }

        BestPick best = doPredictions(player, provider, candidates);
        if (best == null) {
            player.predictedVelocity = new VectorData(player.actualMovement.clone(), VectorData.Type.BEST);
            player.clientVelocity = player.actualMovement.clone().multiply(0.25D);
            player.collisionX = false;
            player.collisionY = false;
            player.collisionZ = false;
            return;
        }

        Vector preCollision = best.preCollision;
        preCollision.setX(preCollision.getX() * 0.25D);
        preCollision.setY(preCollision.getY() * 0.05D);
        preCollision.setZ(preCollision.getZ() * 0.25D);

        Collisions.Result move = Collisions.collide(provider, player.x, player.y, player.z,
                preCollision.getX(), preCollision.getY(), preCollision.getZ(), player.onGround);

        Vector displacement = new Vector(move.movedX, move.movedY, move.movedZ);
        player.predictedVelocity = new VectorData(displacement, best.data, VectorData.Type.BEST);
        player.collisionX = move.collisionX;
        player.collisionY = move.collisionY;
        player.collisionZ = move.collisionZ;

        double carryX = move.collisionX ? 0.0D : preCollision.getX();
        double carryY = move.collisionY ? 0.0D : preCollision.getY();
        double carryZ = move.collisionZ ? 0.0D : preCollision.getZ();
        carryY = (carryY - player.gravity) * 0.98D;
        player.clientVelocity = new Vector(carryX, carryY, carryZ);
        player.onGround = move.onGround;
    }

    // ---- Normal (air/ground) travel ----

    private static void normalTravel(MovementPlayer player, BlockProvider provider) {
        float friction = player.friction;
        float inputAccel = player.onGround
                ? (float) (aiMoveSpeed(player) * (0.16277136D / (friction * friction * friction)))
                : airAccel(player);

        List<VectorData> starts = fetchStartVectors(player);
        List<VectorData> candidates = applyInputs(player, starts, inputAccel);
        if (player.pointThreeEstimator.shouldInjectZeroMovement()) {
            candidates.add(new VectorData(new Vector(), VectorData.Type.START));
        }

        BestPick best = doPredictions(player, provider, candidates);
        if (best == null) {
            // No candidate (shouldn't happen) — trust actual movement to avoid false flags.
            player.predictedVelocity = new VectorData(player.actualMovement.clone(), VectorData.Type.BEST);
            player.clientVelocity = player.actualMovement.clone();
            player.collisionX = false;
            player.collisionY = false;
            player.collisionZ = false;
            return;
        }

        Vector preCollision = best.preCollision;
        // Ladder clamp before collision (matches vanilla isOnLadder branch).
        if (player.onClimbable) {
            preCollision.setX(clamp(preCollision.getX(), -0.15D, 0.15D));
            preCollision.setZ(clamp(preCollision.getZ(), -0.15D, 0.15D));
            if (preCollision.getY() < -0.15D) preCollision.setY(-0.15D);
            if (player.sneaking && preCollision.getY() < 0.0D) preCollision.setY(0.0D);
        }

        Collisions.Result move = Collisions.collide(provider, player.x, player.y, player.z,
                preCollision.getX(), preCollision.getY(), preCollision.getZ(), player.onGround);

        Vector displacement = new Vector(move.movedX, move.movedY, move.movedZ);
        player.predictedVelocity = new VectorData(displacement, best.data, VectorData.Type.BEST);
        player.collisionX = move.collisionX;
        player.collisionY = move.collisionY;
        player.collisionZ = move.collisionZ;

        // Build carried velocity for next tick.
        double carryX = move.collisionX ? 0.0D : preCollision.getX();
        double carryY = preCollision.getY();
        double carryZ = move.collisionZ ? 0.0D : preCollision.getZ();
        boolean climbCollideUp = player.onClimbable && move.collisionX;

        if (player.onClimbable && move.collisionX) {
            carryY = 0.2D; // climbing wall pull-up
        }
        if (move.collisionY) {
            carryY = 0.0D;
        }
        // gravity + drag on Y, slip friction on X/Z (vanilla post-move order)
        carryY = (carryY - player.gravity) * 0.98D;
        carryX *= friction;
        carryZ *= friction;

        player.clientVelocity = new Vector(carryX, carryY, carryZ);
        player.onGround = move.onGround || move.collisionY && preCollision.getY() < 0.0D;
    }

    // ---- Fluid travel (water / lava) ----

    private static void fluidTravel(MovementPlayer player, BlockProvider provider, double horizontalDrag, double accel) {
        List<VectorData> starts = fetchStartVectors(player);
        List<VectorData> candidates = applyInputs(player, starts, (float) accel);

        BestPick best = doPredictions(player, provider, candidates);
        if (best == null) {
            player.predictedVelocity = new VectorData(player.actualMovement.clone(), VectorData.Type.BEST);
            player.clientVelocity = player.actualMovement.clone();
            player.collisionX = false;
            player.collisionY = false;
            player.collisionZ = false;
            return;
        }

        Vector preCollision = best.preCollision;
        Collisions.Result move = Collisions.collide(provider, player.x, player.y, player.z,
                preCollision.getX(), preCollision.getY(), preCollision.getZ(), player.onGround);

        Vector displacement = new Vector(move.movedX, move.movedY, move.movedZ);
        player.predictedVelocity = new VectorData(displacement, best.data, VectorData.Type.BEST);
        player.collisionX = move.collisionX;
        player.collisionY = move.collisionY;
        player.collisionZ = move.collisionZ;

        double carryX = (move.collisionX ? 0.0D : preCollision.getX()) * horizontalDrag;
        double carryY = (move.collisionY ? 0.0D : preCollision.getY()) * (player.inLava ? 0.5D : 0.8D);
        double carryZ = (move.collisionZ ? 0.0D : preCollision.getZ()) * horizontalDrag;
        carryY -= 0.02D;
        if ((move.collisionX || move.collisionZ)) {
            carryY = 0.3D; // swim up wall
        }
        player.clientVelocity = new Vector(carryX, carryY, carryZ);
        player.onGround = move.onGround;
    }

    // ---- Candidate generation ----

    private static List<VectorData> fetchStartVectors(MovementPlayer player) {
        List<VectorData> starts = new ArrayList<VectorData>();
        Vector base = player.clientVelocity.clone();

        // Fold pending knockback / explosion into the carried velocity.
        if (player.pendingKnockback != null) {
            Vector kb = base.clone().add(player.pendingKnockback);
            starts.add(new VectorData(kb, VectorData.Type.KNOCKBACK));
        }
        if (player.pendingExplosion != null) {
            Vector ex = base.clone().add(player.pendingExplosion);
            starts.add(new VectorData(ex, VectorData.Type.EXPLOSION));
        }
        starts.add(new VectorData(base.clone(), VectorData.Type.START));

        // Jump branches (on ground only).
        if (player.onGround && !player.inWater && !player.inLava) {
            double jumpY = 0.42D + (player.jumpAmplifier > 0 ? player.jumpAmplifier * 0.1D : 0.0D);
            List<VectorData> jumps = new ArrayList<VectorData>();
            for (VectorData start : starts) {
                Vector jv = start.vector.clone();
                jv.setY(jumpY);
                if (player.sprinting) {
                    double rad = Math.toRadians(player.yaw);
                    float walkRatio = player.movementSpeed > 0.0F ? (player.movementSpeed / 0.1F) : 1.0F;
                    double jumpBoostH = 0.2D * walkRatio;
                    jv.setX(jv.getX() - Math.sin(rad) * jumpBoostH);
                    jv.setZ(jv.getZ() + Math.cos(rad) * jumpBoostH);
                }
                jumps.add(new VectorData(jv, start, VectorData.Type.JUMP));
            }
            starts.addAll(jumps);
        }

        return starts;
    }

    private static List<VectorData> applyInputs(MovementPlayer player, List<VectorData> starts, float inputAccel) {
        List<VectorData> out = new ArrayList<VectorData>();
        double inputScale = 1.0D;
        if (player.sneaking) inputScale = 0.3D;
        else if (player.usingItem || player.blocking) {
            inputScale = player.itemInputScale > 0.0D ? player.itemInputScale : 0.2D;
        }

        for (VectorData start : starts) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                for (int forward = -1; forward <= 1; forward++) {
                    Vector input = movementInputToVelocity(strafe * inputScale, forward * inputScale, inputAccel, player.yaw);
                    Vector candidate = start.vector.clone().add(input);
                    out.add(new VectorData(candidate, start, VectorData.Type.INPUT));
                }
            }
        }
        return out;
    }

    /** Vanilla moveFlying: rotate normalized scaled input by yaw. */
    public static Vector movementInputToVelocity(double strafe, double forward, double movement, float yaw) {
        double dist = strafe * strafe + forward * forward;
        if (dist < 1.0E-4D) {
            return new Vector(0.0D, 0.0D, 0.0D);
        }
        dist = Math.sqrt(dist);
        if (dist < 1.0D) dist = 1.0D;
        double factor = movement / dist;
        strafe *= factor;
        forward *= factor;
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        return new Vector(strafe * cos - forward * sin, 0.0D, forward * cos + strafe * sin);
    }

    // ---- Best-fit loop ----

    private static final class BestPick {
        Vector preCollision;
        VectorData data;
        double offsetSq;
    }

    private static BestPick doPredictions(MovementPlayer player, BlockProvider provider, List<VectorData> candidates) {
        double uH = player.uncertaintyHandler.getHorizontalUncertainty();
        double uV = player.uncertaintyHandler.getVerticalUncertainty();
        Vector actual = player.actualMovement;

        BestPick best = null;
        for (VectorData candidate : candidates) {
            Vector c = candidate.vector;
            // Cut the uncertainty box [c-u, c+u] toward the actual movement.
            double tx = clamp(actual.getX(), c.getX() - uH, c.getX() + uH);
            double ty = clamp(actual.getY(), c.getY() - uV, c.getY() + uV);
            double tz = clamp(actual.getZ(), c.getZ() - uH, c.getZ() + uH);

            Collisions.Result move = Collisions.collide(provider, player.x, player.y, player.z, tx, ty, tz, player.onGround);
            double dx = move.movedX - actual.getX();
            double dy = move.movedY - actual.getY();
            double dz = move.movedZ - actual.getZ();
            double offsetSq = dx * dx + dy * dy + dz * dz;

            if (best == null || offsetSq < best.offsetSq) {
                best = new BestPick();
                best.preCollision = new Vector(tx, ty, tz);
                best.data = candidate;
                best.offsetSq = offsetSq;
                if (offsetSq < 1.0E-10D) break; // perfect match, stop early
            }
        }
        return best;
    }

    // ---- Physics helpers ----

    public static double aiMoveSpeed(MovementPlayer player) {
        double speed = player.movementSpeed > 0.0F ? player.movementSpeed : 0.1D;
        if (player.sprinting) speed *= 1.3D;
        if (player.slowAmplifier >= 1) speed *= Math.max(0.0D, 1.0D - 0.15D * player.slowAmplifier);
        return speed;
    }

    private static float airAccel(MovementPlayer player) {
        float walkRatio = player.movementSpeed > 0.0F ? (player.movementSpeed / 0.1F) : 1.0F;
        float accel = 0.02F * walkRatio;
        if (player.sprinting) accel += 0.005989F * walkRatio;
        return accel;
    }

    private static double clamp(double v, double min, double max) {
        if (min > max) { double t = min; min = max; max = t; }
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
