package com.colin.vezanticheat.engine;

import org.bukkit.util.Vector;

/**
 * MovementPlayer — per-player engine state, analogue of GrimAC's GrimPlayer.
 *
 * Holds everything the prediction loop needs for one tick: the committed position, the
 * carried client velocity, the candidate that won last tick, environmental flags, potion
 * amplifiers, and the uncertainty/0.03 helpers. One instance lives per player on
 * {@link com.colin.vezanticheat.data.PlayerData} and is mutated in place each movement
 * packet by {@link MovementCheckRunner}.
 */
public final class MovementPlayer {

    // Committed position (current packet) and previous packet position.
    public double x, y, z;
    public double lastX, lastY, lastZ;
    public float yaw, pitch;

    // actualMovement = (x,y,z) - (lastX,lastY,lastZ)
    public Vector actualMovement = new Vector();
    // clientVelocity is the carried pre-collision velocity entering this tick.
    public Vector clientVelocity = new Vector();
    // predictedVelocity is the best post-collision displacement chosen for this tick.
    public VectorData predictedVelocity;

    public boolean onGround;
    public boolean lastOnGround;
    public boolean clientClaimsGround;

    public boolean sprinting;
    public boolean sneaking;
    public boolean blocking;        // sword block / item use slowdown
    public boolean usingItem;
    /** 1.0 = full input; ramps toward 0.2 while eating (see ItemUseMovementUtil). */
    public double itemInputScale = 1.0D;

    public int speedAmplifier;      // 0 = none, else level
    public int slowAmplifier;
    public int jumpAmplifier;

    // Environment for the current tick.
    public boolean inWater;
    public boolean inLava;
    public boolean onClimbable;
    public boolean inWeb;
    public boolean onIce;
    public boolean onSlime;

    // Winning collision result from the prediction tick (for PhasePrediction etc.).
    public boolean collisionX;
    public boolean collisionY;
    public boolean collisionZ;

    // Derived physics values for this tick.
    public float friction = 0.91f;
    public float movementSpeed = 0.1f;  // base land movement speed (walkSpeed/2)
    public double gravity = 0.08D;

    // Pending external impulses (knockback / explosion) to fold into start vectors.
    public Vector pendingKnockback;
    public Vector pendingExplosion;
    public boolean knockbackVerified;

    // 0.03 tick-skip possibility for this tick.
    public boolean couldSkipTick;

    // Lag/teleport bookkeeping.
    public long lastTeleportMs;
    public int ticksSinceTeleport = 1000;
    public int ping;
    public double tps = 20.0D;

    // Timestamps of the last external impulses already folded into a start vector,
    // so a single knockback/explosion is only applied on one tick.
    public long lastKnockbackConsumedMs;
    public long lastExplosionConsumedMs;

    public CompensatedWorld world;
    public final UncertaintyHandler uncertaintyHandler = new UncertaintyHandler(this);
    public final PointThreeEstimator pointThreeEstimator = new PointThreeEstimator(this);

    public Vector pose() {
        return new Vector(x, y, z);
    }

    public void resetTo(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = this.lastX = x;
        this.y = this.lastY = y;
        this.z = this.lastZ = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = this.lastOnGround = onGround;
        this.clientVelocity = new Vector();
        this.predictedVelocity = null;
        this.pendingKnockback = null;
        this.pendingExplosion = null;
    }

    public boolean isAffectedByFluids() {
        return inWater || inLava;
    }

    /** Horizontal magnitude of actual movement this tick. */
    public double actualHorizontal() {
        return Math.hypot(actualMovement.getX(), actualMovement.getZ());
    }
}
