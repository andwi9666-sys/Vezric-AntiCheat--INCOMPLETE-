package com.colin.vezanticheat.movement;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

public final class PlayerMovementState {

    public boolean initialized;
    public long tick;
    public long lastTeleportMs;
    public long lastVelocityConsumedMs;
    public long lastExplosionConsumedMs;

    public Location lastPosition;
    public Location currentPosition;
    public Vector lastMotion = new Vector();
    public Vector carriedMotion = new Vector();
    public Vector lastLegalMotion = new Vector();

    public boolean lastGround;
    public boolean predictedGround;
    public boolean lastClientGround;
    public boolean serverSetbackPending;

    public double fallDistance;
    public double lastLegalY;
    public double offsetAdvantage;
    public double timerDebtMs;
    public int airborneTicks;
    public int groundTicks;
    public int cleanTicks;
    public int violationTicks;
    public int velocityTicks;
    public int explosionTicks;
    public int blinkTicks;

    public Material lastBlockBelow = Material.AIR;
    public boolean lastOnIce;
    public boolean lastOnSlime;
    public boolean lastInLiquid;
    public boolean lastInWeb;
    public boolean lastOnClimbable;

    public void reset(Location location, boolean ground, long nowMs) {
        this.initialized = location != null;
        this.lastPosition = location == null ? null : location.clone();
        this.currentPosition = location == null ? null : location.clone();
        this.lastMotion = new Vector();
        this.carriedMotion = new Vector();
        this.lastLegalMotion = new Vector();
        this.lastGround = ground;
        this.predictedGround = ground;
        this.lastClientGround = ground;
        this.lastLegalY = location == null ? 0.0D : location.getY();
        this.fallDistance = 0.0D;
        this.timerDebtMs = 0.0D;
        this.airborneTicks = 0;
        this.groundTicks = ground ? 1 : 0;
        this.cleanTicks = 0;
        this.violationTicks = 0;
        this.velocityTicks = 0;
        this.explosionTicks = 0;
        this.blinkTicks = 0;
        this.lastTeleportMs = nowMs;
    }

    public void applyResult(SimulationResult result) {
        if (result == null) return;
        this.tick = result.tick;
        this.currentPosition = result.position == null ? currentPosition : result.position.clone();
        this.lastMotion = result.actualMotion == null ? new Vector() : result.actualMotion.clone();
        this.carriedMotion = result.nextMotion == null ? new Vector() : result.nextMotion.clone();
        this.lastLegalMotion = result.expectedMotion == null ? new Vector() : result.expectedMotion.clone();
        this.predictedGround = result.predictedGround;
        this.lastClientGround = result.clientGround;
        this.lastBlockBelow = result.blockBelow;
        this.lastOnIce = result.onIce;
        this.lastOnSlime = result.onSlime;
        this.lastInLiquid = result.inWater || result.inLava;
        this.lastInWeb = result.inWeb;
        this.lastOnClimbable = result.onClimbable;
        this.timerDebtMs = result.timerDebtMs;
        this.offsetAdvantage = result.advantage;
        if (result.predictedGround || result.clientGround) {
            this.groundTicks++;
            this.airborneTicks = 0;
            this.fallDistance = 0.0D;
            if (result.position != null) this.lastLegalY = result.position.getY();
        } else {
            this.airborneTicks++;
            this.groundTicks = 0;
            if (result.actualMotion != null && result.actualMotion.getY() < 0.0D) {
                this.fallDistance += -result.actualMotion.getY();
            }
        }
        if (result.violations.isEmpty()) {
            this.cleanTicks++;
            this.violationTicks = 0;
        } else {
            this.violationTicks++;
            this.cleanTicks = 0;
        }
        if (result.velocityTick) this.velocityTicks++;
        else this.velocityTicks = Math.max(0, velocityTicks - 1);
        if (result.explosionTick) this.explosionTicks++;
        else this.explosionTicks = Math.max(0, explosionTicks - 1);
        this.lastGround = result.predictedGround;
    }
}
