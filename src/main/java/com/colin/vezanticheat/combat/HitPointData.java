package com.colin.vezanticheat.combat;

import com.colin.vezanticheat.combat.math.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Normalized hit location on a target hitbox for distribution tracking.
 */
public final class HitPointData {

    private static final double EDGE_THRESHOLD = 0.10D;
    private static final double CENTER_BAND = 0.15D;
    private static final double UPPER_BODY_MIN = 0.50D;
    private static final double UPPER_BODY_MAX = 0.85D;

    private final double relativeX;
    private final double relativeY;
    private final double relativeZ;
    private final boolean edgeHit;
    private final boolean centerLikeHit;
    private final boolean expansionShellHit;
    private final long timestamp;

    private HitPointData(double relativeX, double relativeY, double relativeZ,
                         boolean edgeHit, boolean centerLikeHit, boolean expansionShellHit,
                         long timestamp) {
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        this.relativeZ = relativeZ;
        this.edgeHit = edgeHit;
        this.centerLikeHit = centerLikeHit;
        this.expansionShellHit = expansionShellHit;
        this.timestamp = timestamp;
    }

    public static HitPointData fromHit(Vector hitPoint, BoundingBox normalBox, boolean normalHit,
                                       HitboxExpansionTier tier, long timestamp) {
        if (hitPoint == null || normalBox == null) {
            return null;
        }

        double spanX = normalBox.maxX - normalBox.minX;
        double spanY = normalBox.maxY - normalBox.minY;
        double spanZ = normalBox.maxZ - normalBox.minZ;

        double relativeX = spanX > 0.0D ? (hitPoint.getX() - normalBox.minX) / spanX : 0.5D;
        double relativeY = spanY > 0.0D ? (hitPoint.getY() - normalBox.minY) / spanY : 0.5D;
        double relativeZ = spanZ > 0.0D ? (hitPoint.getZ() - normalBox.minZ) / spanZ : 0.5D;

        boolean expansionShellHit = !normalHit && tier != null && tier.isExpansionTier();
        boolean edgeHit = relativeX <= EDGE_THRESHOLD || relativeX >= (1.0D - EDGE_THRESHOLD)
                || relativeZ <= EDGE_THRESHOLD || relativeZ >= (1.0D - EDGE_THRESHOLD);
        boolean centerLikeHit = Math.abs(relativeX - 0.5D) <= CENTER_BAND
                && Math.abs(relativeZ - 0.5D) <= CENTER_BAND
                && relativeY >= UPPER_BODY_MIN
                && relativeY <= UPPER_BODY_MAX;

        return new HitPointData(
                relativeX,
                relativeY,
                relativeZ,
                edgeHit,
                centerLikeHit,
                expansionShellHit,
                timestamp);
    }

    public double getRelativeX() {
        return relativeX;
    }

    public double getRelativeY() {
        return relativeY;
    }

    public double getRelativeZ() {
        return relativeZ;
    }

    public boolean isEdgeHit() {
        return edgeHit;
    }

    public boolean isCenterLikeHit() {
        return centerLikeHit;
    }

    public boolean isExpansionShellHit() {
        return expansionShellHit;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
