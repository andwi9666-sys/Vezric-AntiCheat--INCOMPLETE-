package com.colin.vezanticheat.movement;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DebugTrace {

    public final long timeMs;
    public final long tick;
    public final String world;
    public final Location position;
    public final Vector delta;
    public final Vector expected;
    public final double offset;
    public final double horizontalOffset;
    public final double verticalOffset;
    public final boolean clientGround;
    public final boolean predictedGround;
    public final Material blockBelow;
    public final boolean collisionX;
    public final boolean collisionY;
    public final boolean collisionZ;
    public final int speedAmplifier;
    public final int jumpAmplifier;
    public final int slowAmplifier;
    public final double timerDebtMs;
    public final String surface;
    public final List<MovementViolation> violations;
    public final String summary;

    public DebugTrace(SimulationResult result) {
        this.timeMs = result.timeMs;
        this.tick = result.tick;
        this.world = result.worldName;
        this.position = result.position == null ? null : result.position.clone();
        this.delta = result.actualMotion == null ? new Vector() : result.actualMotion.clone();
        this.expected = result.expectedMotion == null ? new Vector() : result.expectedMotion.clone();
        this.offset = result.offset;
        this.horizontalOffset = result.horizontalOffset;
        this.verticalOffset = result.verticalOffset;
        this.clientGround = result.clientGround;
        this.predictedGround = result.predictedGround;
        this.blockBelow = result.blockBelow;
        this.collisionX = result.collisionX;
        this.collisionY = result.collisionY;
        this.collisionZ = result.collisionZ;
        this.speedAmplifier = result.speedAmplifier;
        this.jumpAmplifier = result.jumpAmplifier;
        this.slowAmplifier = result.slowAmplifier;
        this.timerDebtMs = result.timerDebtMs;
        this.surface = result.surface;
        this.violations = Collections.unmodifiableList(new ArrayList<MovementViolation>(result.violations));
        this.summary = result.debug;
    }

    public String compact() {
        return "#" + tick + " " + surface + " off=" + round(offset)
                + " h=" + round(horizontalOffset) + " v=" + round(verticalOffset)
                + " cg=" + clientGround + " pg=" + predictedGround
                + " debt=" + round(timerDebtMs) + " fam=" + families();
    }

    private String families() {
        if (violations.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (MovementViolation v : violations) {
            if (sb.length() > 0) sb.append(',');
            sb.append(v.family.name());
        }
        return sb.toString();
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
