package com.colin.vezanticheat.movement;

import com.colin.vezanticheat.engine.EngineResult;
import com.colin.vezanticheat.engine.VectorData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class SimulationResult {

    public final long timeMs;
    public final long tick;
    public final boolean checked;
    public final String exemptReason;
    public final String worldName;
    public final Location position;
    public final Vector expectedMotion;
    public final Vector actualMotion;
    public final Vector nextMotion;
    public final double offset;
    public final double rawOffset;
    public final double horizontalOffset;
    public final double verticalOffset;
    public final boolean clientGround;
    public final boolean predictedGround;
    public final boolean collisionX;
    public final boolean collisionY;
    public final boolean collisionZ;
    public final boolean inWater;
    public final boolean inLava;
    public final boolean inWeb;
    public final boolean onClimbable;
    public final boolean onIce;
    public final boolean onSlime;
    public final boolean usingItem;
    public final boolean illegalSprint;
    public final boolean illegalSneak;
    public final boolean couldSkipTick;
    public final boolean velocityTick;
    public final boolean explosionTick;
    public final Material blockBelow;
    public final String surface;
    public final int speedAmplifier;
    public final int jumpAmplifier;
    public final int slowAmplifier;
    public final long flyingGapMs;
    public final double timerDebtMs;
    public final double noSlowExcess;
    public final double entityPushOffset;
    public final double advantage;
    public final List<MovementViolation> violations;
    public final String debug;

    private SimulationResult(Builder b) {
        this.timeMs = b.timeMs;
        this.tick = b.tick;
        this.checked = b.checked;
        this.exemptReason = b.exemptReason;
        this.worldName = b.worldName;
        this.position = b.position == null ? null : b.position.clone();
        this.expectedMotion = b.expectedMotion == null ? new Vector() : b.expectedMotion.clone();
        this.actualMotion = b.actualMotion == null ? new Vector() : b.actualMotion.clone();
        this.nextMotion = b.nextMotion == null ? new Vector() : b.nextMotion.clone();
        this.offset = b.offset;
        this.rawOffset = b.rawOffset;
        this.horizontalOffset = b.horizontalOffset;
        this.verticalOffset = b.verticalOffset;
        this.clientGround = b.clientGround;
        this.predictedGround = b.predictedGround;
        this.collisionX = b.collisionX;
        this.collisionY = b.collisionY;
        this.collisionZ = b.collisionZ;
        this.inWater = b.inWater;
        this.inLava = b.inLava;
        this.inWeb = b.inWeb;
        this.onClimbable = b.onClimbable;
        this.onIce = b.onIce;
        this.onSlime = b.onSlime;
        this.usingItem = b.usingItem;
        this.illegalSprint = b.illegalSprint;
        this.illegalSneak = b.illegalSneak;
        this.couldSkipTick = b.couldSkipTick;
        this.velocityTick = b.velocityTick;
        this.explosionTick = b.explosionTick;
        this.blockBelow = b.blockBelow;
        this.surface = b.surface == null ? "normal" : b.surface;
        this.speedAmplifier = b.speedAmplifier;
        this.jumpAmplifier = b.jumpAmplifier;
        this.slowAmplifier = b.slowAmplifier;
        this.flyingGapMs = b.flyingGapMs;
        this.timerDebtMs = b.timerDebtMs;
        this.noSlowExcess = b.noSlowExcess;
        this.entityPushOffset = b.entityPushOffset;
        this.advantage = b.advantage;
        this.violations = Collections.unmodifiableList(new ArrayList<MovementViolation>(b.violations));
        this.debug = b.debug == null ? "" : b.debug;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasFamily(MovementFamily family) {
        for (MovementViolation violation : violations) {
            if (violation.family == family) return true;
        }
        return false;
    }

    public EnumSet<MovementFamily> families() {
        EnumSet<MovementFamily> out = EnumSet.noneOf(MovementFamily.class);
        for (MovementViolation violation : violations) out.add(violation.family);
        return out;
    }

    public EngineResult toEngineResult() {
        return EngineResult.builder()
                .timeMs(timeMs)
                .checked(checked)
                .exemptReason(exemptReason)
                .offset(offset)
                .rawOffset(rawOffset)
                .horizontalOffset(horizontalOffset)
                .verticalOffset(verticalOffset)
                .predicted(expectedMotion.clone())
                .actual(actualMotion.clone())
                .predictedOnGround(predictedGround)
                .clientGround(clientGround)
                .collisionX(collisionX)
                .collisionY(collisionY)
                .collisionZ(collisionZ)
                .bestType(VectorData.Type.BEST)
                .knockbackTick(velocityTick)
                .explosionTick(explosionTick)
                .onIce(onIce)
                .onSlime(onSlime)
                .inWater(inWater || inLava)
                .onClimbable(onClimbable)
                .inWeb(inWeb)
                .couldSkipTick(couldSkipTick)
                .flyingGapMs(flyingGapMs)
                .timerDebtMs(timerDebtMs)
                .usingItem(usingItem)
                .illegalSprint(illegalSprint)
                .illegalSneak(illegalSneak)
                .noSlowExcess(noSlowExcess)
                .entityPushOffset(entityPushOffset)
                .debug(debug)
                .build();
    }

    public static final class Builder {
        private long timeMs;
        private long tick;
        private boolean checked = true;
        private String exemptReason;
        private String worldName;
        private Location position;
        private Vector expectedMotion = new Vector();
        private Vector actualMotion = new Vector();
        private Vector nextMotion = new Vector();
        private double offset;
        private double rawOffset;
        private double horizontalOffset;
        private double verticalOffset;
        private boolean clientGround;
        private boolean predictedGround;
        private boolean collisionX;
        private boolean collisionY;
        private boolean collisionZ;
        private boolean inWater;
        private boolean inLava;
        private boolean inWeb;
        private boolean onClimbable;
        private boolean onIce;
        private boolean onSlime;
        private boolean usingItem;
        private boolean illegalSprint;
        private boolean illegalSneak;
        private boolean couldSkipTick;
        private boolean velocityTick;
        private boolean explosionTick;
        private Material blockBelow;
        private String surface;
        private int speedAmplifier;
        private int jumpAmplifier;
        private int slowAmplifier;
        private long flyingGapMs;
        private double timerDebtMs;
        private double noSlowExcess;
        private double entityPushOffset;
        private double advantage;
        private final List<MovementViolation> violations = new ArrayList<MovementViolation>();
        private String debug;

        public Builder timeMs(long v) { timeMs = v; return this; }
        public Builder tick(long v) { tick = v; return this; }
        public Builder checked(boolean v) { checked = v; return this; }
        public Builder exemptReason(String v) { exemptReason = v; return this; }
        public Builder worldName(String v) { worldName = v; return this; }
        public Builder position(Location v) { position = v; return this; }
        public Builder expectedMotion(Vector v) { expectedMotion = v; return this; }
        public Builder actualMotion(Vector v) { actualMotion = v; return this; }
        public Builder nextMotion(Vector v) { nextMotion = v; return this; }
        public Builder offset(double v) { offset = v; return this; }
        public Builder rawOffset(double v) { rawOffset = v; return this; }
        public Builder horizontalOffset(double v) { horizontalOffset = v; return this; }
        public Builder verticalOffset(double v) { verticalOffset = v; return this; }
        public Builder clientGround(boolean v) { clientGround = v; return this; }
        public Builder predictedGround(boolean v) { predictedGround = v; return this; }
        public Builder collisionX(boolean v) { collisionX = v; return this; }
        public Builder collisionY(boolean v) { collisionY = v; return this; }
        public Builder collisionZ(boolean v) { collisionZ = v; return this; }
        public Builder inWater(boolean v) { inWater = v; return this; }
        public Builder inLava(boolean v) { inLava = v; return this; }
        public Builder inWeb(boolean v) { inWeb = v; return this; }
        public Builder onClimbable(boolean v) { onClimbable = v; return this; }
        public Builder onIce(boolean v) { onIce = v; return this; }
        public Builder onSlime(boolean v) { onSlime = v; return this; }
        public Builder usingItem(boolean v) { usingItem = v; return this; }
        public Builder illegalSprint(boolean v) { illegalSprint = v; return this; }
        public Builder illegalSneak(boolean v) { illegalSneak = v; return this; }
        public Builder couldSkipTick(boolean v) { couldSkipTick = v; return this; }
        public Builder velocityTick(boolean v) { velocityTick = v; return this; }
        public Builder explosionTick(boolean v) { explosionTick = v; return this; }
        public Builder blockBelow(Material v) { blockBelow = v; return this; }
        public Builder surface(String v) { surface = v; return this; }
        public Builder speedAmplifier(int v) { speedAmplifier = v; return this; }
        public Builder jumpAmplifier(int v) { jumpAmplifier = v; return this; }
        public Builder slowAmplifier(int v) { slowAmplifier = v; return this; }
        public Builder flyingGapMs(long v) { flyingGapMs = v; return this; }
        public Builder timerDebtMs(double v) { timerDebtMs = v; return this; }
        public Builder noSlowExcess(double v) { noSlowExcess = v; return this; }
        public Builder entityPushOffset(double v) { entityPushOffset = v; return this; }
        public Builder advantage(double v) { advantage = v; return this; }
        public Builder addViolation(MovementViolation v) { if (v != null) violations.add(v); return this; }
        public Builder violations(List<MovementViolation> v) { if (v != null) violations.addAll(v); return this; }
        public Builder debug(String v) { debug = v; return this; }
        public SimulationResult build() { return new SimulationResult(this); }
    }
}
