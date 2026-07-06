package com.colin.vezanticheat.tier.prism.scaffold;

/**
 * One immutable bridge-placement sample collected by {@link ScaffoldEngine}. Pure data — all
 * analysis lives in {@link ScaffoldAnalyzer}. Angles are in degrees, times in milliseconds.
 *
 * <p>Collected from PacketEvents block-placement data plus the rotation/movement state already
 * tracked on PlayerData, so the detection works off packet truth rather than Bukkit guesses.</p>
 */
final class ScaffoldPlacement {

    final long time;
    /** Player yaw/pitch reported on the placement packet. */
    final float yaw;
    final float pitch;
    /** Signed change from the previous placement's yaw/pitch (degrees). */
    final float yawDelta;
    final float pitchDelta;
    final boolean sneaking;
    final boolean onGround;
    /** Milliseconds since the previous placement (0 for the first in a sequence). */
    final long interval;
    /** Angle (deg) between look heading and horizontal movement heading; NaN when not moving. */
    final double lookVsMoveDeg;
    /** True when an eye ray along the placement rotation actually intersects the against block. */
    final boolean faceRayHit;
    /** Angle (deg) between the look ray and the vector to the clicked point on the against face. */
    final double alignAngleDeg;
    /** True when the against block is a legal support (not placing against air/liquid with no history). */
    final boolean legalSupport;
    /** Placed-block position relative to the player at placement (for repeated-relative-position). */
    final double relX;
    final double relZ;

    ScaffoldPlacement(long time, float yaw, float pitch, float yawDelta, float pitchDelta,
                      boolean sneaking, boolean onGround, long interval, double lookVsMoveDeg,
                      boolean faceRayHit, double alignAngleDeg, boolean legalSupport,
                      double relX, double relZ) {
        this.time = time;
        this.yaw = yaw;
        this.pitch = pitch;
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
        this.sneaking = sneaking;
        this.onGround = onGround;
        this.interval = interval;
        this.lookVsMoveDeg = lookVsMoveDeg;
        this.faceRayHit = faceRayHit;
        this.alignAngleDeg = alignAngleDeg;
        this.legalSupport = legalSupport;
        this.relX = relX;
        this.relZ = relZ;
    }
}
