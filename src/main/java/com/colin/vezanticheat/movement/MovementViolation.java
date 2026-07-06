package com.colin.vezanticheat.movement;

import org.bukkit.util.Vector;

public final class MovementViolation {

    public final MovementFamily family;
    public final double confidence;
    public final double offset;
    public final Vector expectedMotion;
    public final Vector actualMotion;
    public final int tickAge;
    public final String reason;
    public final boolean setbackEligible;

    public MovementViolation(MovementFamily family, double confidence, double offset,
                             Vector expectedMotion, Vector actualMotion, int tickAge,
                             String reason, boolean setbackEligible) {
        this.family = family;
        this.confidence = confidence;
        this.offset = offset;
        this.expectedMotion = expectedMotion == null ? new Vector() : expectedMotion.clone();
        this.actualMotion = actualMotion == null ? new Vector() : actualMotion.clone();
        this.tickAge = tickAge;
        this.reason = reason == null ? "" : reason;
        this.setbackEligible = setbackEligible;
    }

    public boolean blatant() {
        return confidence >= 0.92D || offset >= 0.42D;
    }

    public String shortDebug() {
        return family + " conf=" + round(confidence) + " off=" + round(offset) + " " + reason;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }
}
