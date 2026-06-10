package com.colin.vezanticheat.engine;

import org.bukkit.util.Vector;

/**
 * VectorData — a single candidate velocity hypothesis, tagged with how it was produced.
 *
 * Mirrors GrimAC's VectorData: the prediction engine enumerates many of these (start
 * velocity + input + jump + knockback + 0.03 branches), collides each, and keeps the one
 * whose post-collision displacement is closest to the player's actual movement. The tags
 * are used for uncertainty handling (e.g. knockback / 0.03 branches get extra lenience)
 * and for debug output ("which hypothesis won").
 */
public final class VectorData {

    public enum Type {
        START,
        INPUT,
        JUMP,
        KNOCKBACK,
        EXPLOSION,
        ZERO_POINT_THREE,
        BEST
    }

    public final Vector vector;
    public final Type type;
    public final VectorData parent;
    public boolean isKnockback;
    public boolean isExplosion;
    public boolean isZeroPointThree;
    public boolean isJump;

    public VectorData(Vector vector, Type type) {
        this(vector, null, type);
    }

    public VectorData(Vector vector, VectorData parent, Type type) {
        this.vector = vector;
        this.parent = parent;
        this.type = type;
        if (parent != null) {
            this.isKnockback = parent.isKnockback;
            this.isExplosion = parent.isExplosion;
            this.isZeroPointThree = parent.isZeroPointThree;
            this.isJump = parent.isJump;
        }
        if (type == Type.KNOCKBACK) isKnockback = true;
        if (type == Type.EXPLOSION) isExplosion = true;
        if (type == Type.ZERO_POINT_THREE) isZeroPointThree = true;
        if (type == Type.JUMP) isJump = true;
    }

    public VectorData withVector(Vector newVec, Type newType) {
        return new VectorData(newVec, this, newType);
    }

    public boolean hasKnockbackOrExplosion() {
        return isKnockback || isExplosion;
    }

    @Override
    public String toString() {
        return type + "(" + round(vector.getX()) + "," + round(vector.getY()) + "," + round(vector.getZ()) + ")";
    }

    private static double round(double v) {
        return Math.round(v * 1000.0D) / 1000.0D;
    }
}
