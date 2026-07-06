package com.colin.vezanticheat.utils;

/**
 * Per flying-window packet sequence state for BadPackets A–Z.
 * Updated only from PacketListener; checks read but do not mutate.
 */
public final class BadPacketTracker {

    private boolean swungThisWindow;
    private boolean attackedThisWindow;
    private boolean diggingThisWindow;
    private boolean placingThisWindow;
    private boolean usingItemThisWindow;
    private boolean digStartedThisWindow;

    private int lastInteractEntityId = -1;
    private int distinctInteractEntitiesThisWindow;
    private int lastSecondaryInteractEntityId = -1;

    private DiggingActionType lastDigAction = DiggingActionType.NONE;
    private int entityActionCountThisWindow;
    private int slotChangeCountThisWindow;
    private int swingCountThisWindow;
    private int positionlessFlyingStreak;
    private long lastPositionPacketMs;
    private long windowStartMs;
    private long pendingNoSwingAttackMs;
    private int pendingNoSwingEntityId = -1;

    /** Composite signal counters for BadPacketsZ within the current short window. */
    private int blatantSignalScore;
    private long blatantWindowStartMs;
    private final StringBuilder blatantContributors = new StringBuilder();

    public enum DiggingActionType {
        NONE, START, ABORT, FINISH, DROP, RELEASE
    }

    public void resetWindow(long nowMs) {
        swungThisWindow = false;
        attackedThisWindow = false;
        diggingThisWindow = false;
        placingThisWindow = false;
        usingItemThisWindow = false;
        digStartedThisWindow = false;
        lastInteractEntityId = -1;
        distinctInteractEntitiesThisWindow = 0;
        lastSecondaryInteractEntityId = -1;
        entityActionCountThisWindow = 0;
        slotChangeCountThisWindow = 0;
        swingCountThisWindow = 0;
        windowStartMs = nowMs;
    }

    public void notePositionPacket(long nowMs) {
        positionlessFlyingStreak = 0;
        lastPositionPacketMs = nowMs;
        resetWindow(nowMs);
    }

    public void notePositionlessFlying() {
        positionlessFlyingStreak++;
    }

    /** Legit rotation/ground keepalive while stationary should not build a positionless streak. */
    public void clearPositionlessFlyingStreak() {
        positionlessFlyingStreak = 0;
    }

    public void noteSwing() {
        swungThisWindow = true;
        swingCountThisWindow++;
    }

    public void noteAttack(int entityId) {
        attackedThisWindow = true;
        if (lastInteractEntityId < 0) {
            lastInteractEntityId = entityId;
            distinctInteractEntitiesThisWindow = 1;
        } else if (entityId != lastInteractEntityId) {
            if (entityId != lastSecondaryInteractEntityId) {
                lastSecondaryInteractEntityId = entityId;
                distinctInteractEntitiesThisWindow++;
            }
        }
    }

    public void noteInteractAttempt(int entityId) {
        noteAttack(entityId);
    }

    public void noteDigStart() {
        diggingThisWindow = true;
        digStartedThisWindow = true;
        lastDigAction = DiggingActionType.START;
    }

    public void noteDigAction(DiggingActionType action) {
        lastDigAction = action;
        if (action == DiggingActionType.START) {
            noteDigStart();
        } else if (action == DiggingActionType.ABORT || action == DiggingActionType.FINISH) {
            diggingThisWindow = false;
        }
    }

    public void notePlace() {
        placingThisWindow = true;
    }

    public void noteUseItem() {
        usingItemThisWindow = true;
    }

    public void noteEntityAction() {
        entityActionCountThisWindow++;
    }

    public void noteSlotChange() {
        slotChangeCountThisWindow++;
    }

    public void recordBlatantSignal(String source, int weight, long nowMs, long windowMs) {
        if (blatantWindowStartMs <= 0L || nowMs - blatantWindowStartMs > windowMs) {
            blatantWindowStartMs = nowMs;
            blatantSignalScore = 0;
            blatantContributors.setLength(0);
        }
        blatantSignalScore += Math.max(1, weight);
        if (blatantContributors.length() > 0) blatantContributors.append(',');
        blatantContributors.append(source);
    }

    public boolean swungThisWindow() { return swungThisWindow; }
    public boolean attackedThisWindow() { return attackedThisWindow; }
    public boolean diggingThisWindow() { return diggingThisWindow; }
    public boolean placingThisWindow() { return placingThisWindow; }
    public boolean usingItemThisWindow() { return usingItemThisWindow; }
    public boolean digStartedThisWindow() { return digStartedThisWindow; }
    public int lastInteractEntityId() { return lastInteractEntityId; }
    public int distinctInteractEntitiesThisWindow() { return distinctInteractEntitiesThisWindow; }
    public DiggingActionType lastDigAction() { return lastDigAction; }
    public int entityActionCountThisWindow() { return entityActionCountThisWindow; }
    public int slotChangeCountThisWindow() { return slotChangeCountThisWindow; }
    public int swingCountThisWindow() { return swingCountThisWindow; }
    public int positionlessFlyingStreak() { return positionlessFlyingStreak; }
    public long lastPositionPacketMs() { return lastPositionPacketMs; }
    public long windowStartMs() { return windowStartMs; }
    public int blatantSignalScore() { return blatantSignalScore; }
    public String blatantContributors() { return blatantContributors.toString(); }
    public long pendingNoSwingAttackMs() { return pendingNoSwingAttackMs; }
    public int pendingNoSwingEntityId() { return pendingNoSwingEntityId; }

    public void setPendingNoSwingAttack(long attackMs, int entityId) {
        pendingNoSwingAttackMs = attackMs;
        pendingNoSwingEntityId = entityId;
    }

    public void clearPendingNoSwingAttack() {
        pendingNoSwingAttackMs = 0L;
        pendingNoSwingEntityId = -1;
    }

    /** Per-check structural score for Prism bad-packet tier checks. */
    public int structuralScore(String checkName) {
        if (checkName == null) return 0;
        int base = blatantSignalScore;
        int hash = Math.abs(checkName.hashCode() % 17);
        return base + (positionlessFlyingStreak > 3 ? hash % 5 : 0)
                + (swingCountThisWindow > 2 && !attackedThisWindow ? hash % 4 : 0);
    }
}
