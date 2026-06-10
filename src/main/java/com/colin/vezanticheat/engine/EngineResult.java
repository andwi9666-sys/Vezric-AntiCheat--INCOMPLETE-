package com.colin.vezanticheat.engine;

import org.bukkit.util.Vector;

/**
 * EngineResult — the decomposed outcome of one prediction tick.
 *
 * Stored on PlayerData each movement packet so the movement sub-checks (Speed, Fly, NoFall,
 * Step, Jesus, Phase, Timer, NoSlow, Velocity) can read a single authoritative offset signal
 * instead of running their own heuristics. {@code checked == false} means the tick was
 * exempt (teleport, fluids the engine doesn't fully model, chunk unloaded, etc.) and must not
 * be flagged on.
 */
public final class EngineResult {

    public final long timeMs;
    public final boolean checked;
    public final String exemptReason;

    public final double offset;            // uncertainty-reduced total offset
    public final double rawOffset;         // pre-reduction offset
    public final double horizontalOffset;  // horizontal component of raw offset
    public final double verticalOffset;    // vertical component of raw offset

    public final Vector predicted;
    public final Vector actual;
    public final boolean predictedOnGround;
    public final boolean clientGround;
    public final boolean collisionX;
    public final boolean collisionY;
    public final boolean collisionZ;
    public final VectorData.Type bestType;
    public final boolean knockbackTick;
    public final boolean explosionTick;
    public final boolean onIce;
    public final boolean onSlime;
    public final boolean inWater;
    public final boolean onClimbable;
    public final boolean inWeb;
    public final boolean couldSkipTick;
    /** Milliseconds since the previous flying packet (blink gap). */
    public final long flyingGapMs;
    /** Legacy/prediction timer debt carried into the engine tick. */
    public final double timerDebtMs;
    /** Client is blocking/eating/drawing bow this tick. */
    public final boolean usingItem;
    /** Client claims sprint while horizontal envelope exceeds sprint prediction. */
    public final boolean illegalSprint;
    /** Client claims sneak while horizontal envelope exceeds sneak prediction. */
    public final boolean illegalSneak;
    /** Horizontal offset while using an item (NoSlow signal). */
    public final double noSlowExcess;
    /** Offset attributable to entity push / piston uncertainty. */
    public final double entityPushOffset;
    public final String debug;

    private EngineResult(Builder b) {
        this.timeMs = b.timeMs;
        this.checked = b.checked;
        this.exemptReason = b.exemptReason;
        this.offset = b.offset;
        this.rawOffset = b.rawOffset;
        this.horizontalOffset = b.horizontalOffset;
        this.verticalOffset = b.verticalOffset;
        this.predicted = b.predicted;
        this.actual = b.actual;
        this.predictedOnGround = b.predictedOnGround;
        this.clientGround = b.clientGround;
        this.collisionX = b.collisionX;
        this.collisionY = b.collisionY;
        this.collisionZ = b.collisionZ;
        this.bestType = b.bestType;
        this.knockbackTick = b.knockbackTick;
        this.explosionTick = b.explosionTick;
        this.onIce = b.onIce;
        this.onSlime = b.onSlime;
        this.inWater = b.inWater;
        this.onClimbable = b.onClimbable;
        this.inWeb = b.inWeb;
        this.couldSkipTick = b.couldSkipTick;
        this.flyingGapMs = b.flyingGapMs;
        this.timerDebtMs = b.timerDebtMs;
        this.usingItem = b.usingItem;
        this.illegalSprint = b.illegalSprint;
        this.illegalSneak = b.illegalSneak;
        this.noSlowExcess = b.noSlowExcess;
        this.entityPushOffset = b.entityPushOffset;
        this.debug = b.debug;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EngineResult exempt(long timeMs, String reason) {
        return builder().timeMs(timeMs).checked(false).exemptReason(reason)
                .debug("exempt=" + reason).build();
    }

    public static final class Builder {
        private long timeMs;
        private boolean checked = true;
        private String exemptReason;
        private double offset;
        private double rawOffset;
        private double horizontalOffset;
        private double verticalOffset;
        private Vector predicted = new Vector();
        private Vector actual = new Vector();
        private boolean predictedOnGround;
        private boolean clientGround;
        private boolean collisionX;
        private boolean collisionY;
        private boolean collisionZ;
        private VectorData.Type bestType = VectorData.Type.BEST;
        private boolean knockbackTick;
        private boolean explosionTick;
        private boolean onIce;
        private boolean onSlime;
        private boolean inWater;
        private boolean onClimbable;
        private boolean inWeb;
        private boolean couldSkipTick;
        private long flyingGapMs;
        private double timerDebtMs;
        private boolean usingItem;
        private boolean illegalSprint;
        private boolean illegalSneak;
        private double noSlowExcess;
        private double entityPushOffset;
        private String debug = "";

        public Builder timeMs(long v) { this.timeMs = v; return this; }
        public Builder checked(boolean v) { this.checked = v; return this; }
        public Builder exemptReason(String v) { this.exemptReason = v; return this; }
        public Builder offset(double v) { this.offset = v; return this; }
        public Builder rawOffset(double v) { this.rawOffset = v; return this; }
        public Builder horizontalOffset(double v) { this.horizontalOffset = v; return this; }
        public Builder verticalOffset(double v) { this.verticalOffset = v; return this; }
        public Builder predicted(Vector v) { this.predicted = v; return this; }
        public Builder actual(Vector v) { this.actual = v; return this; }
        public Builder predictedOnGround(boolean v) { this.predictedOnGround = v; return this; }
        public Builder clientGround(boolean v) { this.clientGround = v; return this; }
        public Builder collisionX(boolean v) { this.collisionX = v; return this; }
        public Builder collisionY(boolean v) { this.collisionY = v; return this; }
        public Builder collisionZ(boolean v) { this.collisionZ = v; return this; }
        public Builder bestType(VectorData.Type v) { this.bestType = v; return this; }
        public Builder knockbackTick(boolean v) { this.knockbackTick = v; return this; }
        public Builder explosionTick(boolean v) { this.explosionTick = v; return this; }
        public Builder onIce(boolean v) { this.onIce = v; return this; }
        public Builder onSlime(boolean v) { this.onSlime = v; return this; }
        public Builder inWater(boolean v) { this.inWater = v; return this; }
        public Builder onClimbable(boolean v) { this.onClimbable = v; return this; }
        public Builder inWeb(boolean v) { this.inWeb = v; return this; }
        public Builder couldSkipTick(boolean v) { this.couldSkipTick = v; return this; }
        public Builder flyingGapMs(long v) { this.flyingGapMs = v; return this; }
        public Builder timerDebtMs(double v) { this.timerDebtMs = v; return this; }
        public Builder usingItem(boolean v) { this.usingItem = v; return this; }
        public Builder illegalSprint(boolean v) { this.illegalSprint = v; return this; }
        public Builder illegalSneak(boolean v) { this.illegalSneak = v; return this; }
        public Builder noSlowExcess(double v) { this.noSlowExcess = v; return this; }
        public Builder entityPushOffset(double v) { this.entityPushOffset = v; return this; }
        public Builder debug(String v) { this.debug = v; return this; }

        public EngineResult build() {
            return new EngineResult(this);
        }
    }

    public boolean groundMismatch() { return predictedOnGround != clientGround; }
    public boolean inBlock() { return collisionX || collisionY || collisionZ; }
    public double stepOffset() { return collisionY ? Math.abs(verticalOffset) : 0.0D; }
    public double liquidOffset() { return (inWater || onClimbable) ? offset : 0.0D; }
    public double blinkGap() { return flyingGapMs; }
    public double noSlowRatio() { return usingItem ? 1.0D + noSlowExcess : 1.0D; }
    public double timerDebt() { return timerDebtMs; }
    public double velocityOffset() { return knockbackTick ? offset : 0.0D; }
    public double explosionOffset() { return explosionTick ? offset : 0.0D; }
    public boolean illegalSprint() { return illegalSprint; }
    public boolean illegalSneak() { return illegalSneak; }
    public boolean frictionMismatch() { return (onIce || onSlime) && offset > 0.05D; }
    public double jumpOffset() { return verticalOffset > 0.0D ? verticalOffset : 0.0D; }
    public double climbableOffset() { return onClimbable ? offset : 0.0D; }
    public double entityPushOffset() { return entityPushOffset; }
    public double iceOffset() { return onIce ? offset : 0.0D; }
    public double slimeOffset() { return onSlime ? offset : 0.0D; }
}
