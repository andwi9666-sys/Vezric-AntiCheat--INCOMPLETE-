package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.VezAntiCheat;

public final class MovementPhysicsConfig {

    public final double baseMoveSpeed;
    public final double sprintMultiplier;
    public final double speedPotionPerLevel;
    public final double slownessPerLevel;
    public final double jumpVelocity;
    public final double jumpBoostVelocity;
    public final double gravity;
    public final double verticalDrag;
    public final double groundFrictionFactor;
    public final double normalSlipperiness;
    public final double iceSlipperiness;
    public final double slimeSlipperiness;
    public final double groundAccelerationNumerator;
    public final double airAcceleration;
    public final double sprintAirAccelerationBonus;
    public final double waterDrag;
    public final double lavaDrag;
    public final double liquidAcceleration;
    public final double webHorizontalMultiplier;
    public final double webVerticalMultiplier;
    public final double ladderHorizontalClamp;
    public final double ladderDownClamp;
    public final double ladderClimbUp;
    public final double mathEpsilon;
    public final double collisionTolerance;
    public final double advantageFloor;
    public final double advantageDecay;
    public final double advantageCap;
    public final double minorOffset;
    public final double moderateOffset;
    public final double blatantOffset;
    public final double timerDebtThresholdMs;

    private MovementPhysicsConfig(VezAntiCheat plugin) {
        this.baseMoveSpeed = d(plugin, "base-move-speed", 0.1D);
        this.sprintMultiplier = d(plugin, "sprint-multiplier", 1.3D);
        this.speedPotionPerLevel = d(plugin, "speed-potion-per-level", 0.2D);
        this.slownessPerLevel = d(plugin, "slowness-per-level", 0.15D);
        this.jumpVelocity = d(plugin, "jump-velocity", 0.42D);
        this.jumpBoostVelocity = d(plugin, "jump-boost-velocity-per-level", 0.1D);
        this.gravity = d(plugin, "gravity", 0.08D);
        this.verticalDrag = d(plugin, "vertical-drag", 0.98D);
        this.groundFrictionFactor = d(plugin, "ground-friction-factor", 0.91D);
        this.normalSlipperiness = d(plugin, "normal-slipperiness", 0.6D);
        this.iceSlipperiness = d(plugin, "ice-slipperiness", 0.98D);
        this.slimeSlipperiness = d(plugin, "slime-slipperiness", 0.8D);
        // Vanilla/Grim ground acceleration numerator (BlockProperties: speed * 0.21600002 / friction^3).
        // The previous 0.16277136 was exactly (0.6*0.91)^3, which collapsed ground accel to `move` and
        // under-predicted sprint movement by ~25%, causing persistent false speed offsets.
        this.groundAccelerationNumerator = d(plugin, "ground-acceleration-numerator", 0.21600002D);
        this.airAcceleration = d(plugin, "air-acceleration", 0.02D);
        // Grim sprint air acceleration is 0.025999999 (= 0.02 + 0.006).
        this.sprintAirAccelerationBonus = d(plugin, "sprint-air-acceleration-bonus", 0.006D);
        this.waterDrag = d(plugin, "water-drag", 0.8D);
        this.lavaDrag = d(plugin, "lava-drag", 0.5D);
        this.liquidAcceleration = d(plugin, "liquid-acceleration", 0.02D);
        this.webHorizontalMultiplier = d(plugin, "web-horizontal-multiplier", 0.25D);
        this.webVerticalMultiplier = d(plugin, "web-vertical-multiplier", 0.05D);
        this.ladderHorizontalClamp = d(plugin, "ladder-horizontal-clamp", 0.15D);
        this.ladderDownClamp = d(plugin, "ladder-down-clamp", -0.15D);
        this.ladderClimbUp = d(plugin, "ladder-wall-climb-up", 0.2D);
        this.mathEpsilon = d(plugin, "math-epsilon", 1.0E-5D);
        this.collisionTolerance = d(plugin, "collision-tolerance", 0.03D);
        // Only offset ABOVE this noise floor accrues into the sustained-advantage accumulator, and clean
        // ticks decay it quickly. Prevents legit float/collision jitter from slowly building a false flag.
        this.advantageFloor = d(plugin, "advantage-floor", 0.015D);
        this.advantageDecay = d(plugin, "advantage-decay-per-clean-tick", 0.035D);
        this.advantageCap = d(plugin, "advantage-cap", 3.0D);
        this.minorOffset = d(plugin, "minor-offset", 0.035D);
        this.moderateOffset = d(plugin, "moderate-offset", 0.12D);
        this.blatantOffset = d(plugin, "blatant-offset", 0.42D);
        this.timerDebtThresholdMs = d(plugin, "timer-debt-threshold-ms", 120.0D);
    }

    public static MovementPhysicsConfig from(VezAntiCheat plugin) {
        return new MovementPhysicsConfig(plugin);
    }

    private static double d(VezAntiCheat plugin, String key, double def) {
        if (plugin == null || plugin.getConfig() == null) return def;
        return plugin.getConfig().getDouble("movement-engine.physics." + key, def);
    }
}
