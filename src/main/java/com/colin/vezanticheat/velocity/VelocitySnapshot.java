package com.colin.vezanticheat.velocity;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Immutable capture of a server-applied velocity event and player state.
 */
public final class VelocitySnapshot {

    public final long timeMs;
    public final Vector velocity;
    public final Location startLocation;
    public final VelocitySource source;
    public final double expectedHorizontal;
    public final double expectedVertical;
    public final boolean onGround;
    public final boolean sprinting;
    public final boolean sneaking;
    public final boolean blocking;
    public final boolean inLiquid;
    public final boolean onIce;
    public final boolean onSlime;
    public final boolean inWeb;
    public final boolean weirdSurface;
    public final int speedAmp;
    public final int slowAmp;
    public final int jumpAmp;
    public final int ping;
    public final double tps;
    public final boolean horizontalBlocked;
    public final boolean verticalBlocked;
    public final boolean restrictive;

    // Attacker state at KB time (for validation)
    public final UUID attackerUuid;
    public final boolean attackerSprinting;
    public final Vector attackerVelocity;
    public final double attackCooldown;
    public final int knockbackEnchantLevel;

    public VelocitySnapshot(
            long timeMs,
            Vector velocity,
            Location startLocation,
            VelocitySource source,
            double expectedHorizontal,
            double expectedVertical,
            boolean onGround,
            boolean sprinting,
            boolean sneaking,
            boolean blocking,
            boolean inLiquid,
            boolean onIce,
            boolean onSlime,
            boolean inWeb,
            boolean weirdSurface,
            int speedAmp,
            int slowAmp,
            int jumpAmp,
            int ping,
            double tps,
            boolean horizontalBlocked,
            boolean verticalBlocked,
            boolean restrictive,
            UUID attackerUuid,
            boolean attackerSprinting,
            Vector attackerVelocity,
            double attackCooldown,
            int knockbackEnchantLevel) {
        this.timeMs = timeMs;
        this.velocity = velocity == null ? new Vector(0, 0, 0) : velocity.clone();
        this.startLocation = startLocation == null ? null : startLocation.clone();
        this.source = source == null ? VelocitySource.OTHER : source;
        this.expectedHorizontal = expectedHorizontal;
        this.expectedVertical = expectedVertical;
        this.onGround = onGround;
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.blocking = blocking;
        this.inLiquid = inLiquid;
        this.onIce = onIce;
        this.onSlime = onSlime;
        this.inWeb = inWeb;
        this.weirdSurface = weirdSurface;
        this.speedAmp = speedAmp;
        this.slowAmp = slowAmp;
        this.jumpAmp = jumpAmp;
        this.ping = ping;
        this.tps = tps;
        this.horizontalBlocked = horizontalBlocked;
        this.verticalBlocked = verticalBlocked;
        this.restrictive = restrictive;
        this.attackerUuid = attackerUuid;
        this.attackerSprinting = attackerSprinting;
        this.attackerVelocity = attackerVelocity == null ? null : attackerVelocity.clone();
        this.attackCooldown = attackCooldown;
        this.knockbackEnchantLevel = knockbackEnchantLevel;
    }
}
