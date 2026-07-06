package com.colin.vezanticheat.tier.prism.scaffold;

import com.colin.vezanticheat.utils.ScaffoldUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Block-placement line-of-sight raytracer. From the player's eye position it walks a ray along the
 * placement yaw/pitch and checks whether it intersects the against block, then measures the angular
 * error between the crosshair and the clicked point on that block's face.
 *
 * <p>Deliberately tolerant: it answers "is the player roughly looking at the block face" for the
 * silent Scaffold check, NOT "is the aim mathematically perfect". The reach walk reuses
 * {@link ScaffoldUtil#canRayHitPlacedAgainstFace} so the 1.8.8 placement geometry stays consistent
 * with the rest of the plugin.</p>
 */
final class ScaffoldRaytrace {

    private ScaffoldRaytrace() {}

    static final class Result {
        final boolean faceHit;
        final double alignAngleDeg;

        Result(boolean faceHit, double alignAngleDeg) {
            this.faceHit = faceHit;
            this.alignAngleDeg = alignAngleDeg;
        }
    }

    /**
     * @param feet      player feet location at placement (x/y/z; rotation comes from yaw/pitch)
     * @param eyeHeight player eye height
     * @param yaw       placement yaw from the packet
     * @param pitch     placement pitch from the packet
     * @param against   the block clicked against
     * @param cursorX   cursor position on the face (0..1), or NaN when unavailable
     * @param cursorY   cursor position on the face (0..1), or NaN when unavailable
     * @param cursorZ   cursor position on the face (0..1), or NaN when unavailable
     */
    static Result evaluate(Player p, Location feet, double eyeHeight, float yaw, float pitch,
                           Location against, float cursorX, float cursorY, float cursorZ,
                           double reach, double step) {
        if (p == null || feet == null || feet.getWorld() == null
                || against == null || against.getWorld() == null
                || !feet.getWorld().equals(against.getWorld())) {
            return new Result(false, 180.0D);
        }

        boolean hit = ScaffoldUtil.canRayHitPlacedAgainstFace(feet, eyeHeight, yaw, pitch, against, reach, step);

        Location eyeRot = new Location(feet.getWorld(), feet.getX(), feet.getY() + eyeHeight, feet.getZ(), yaw, pitch);
        Vector look = eyeRot.getDirection();
        if (look.lengthSquared() <= 1.0E-8D) return new Result(hit, 180.0D);

        boolean cursorValid = !Float.isNaN(cursorX) && !Float.isNaN(cursorY) && !Float.isNaN(cursorZ)
                && cursorX >= 0.0F && cursorX <= 1.0F && cursorY >= 0.0F && cursorY <= 1.0F
                && cursorZ >= 0.0F && cursorZ <= 1.0F;
        double tx;
        double ty;
        double tz;
        if (cursorValid) {
            tx = against.getBlockX() + cursorX;
            ty = against.getBlockY() + cursorY;
            tz = against.getBlockZ() + cursorZ;
        } else {
            tx = against.getBlockX() + 0.5D;
            ty = against.getBlockY() + 0.5D;
            tz = against.getBlockZ() + 0.5D;
        }

        Vector to = new Vector(tx - eyeRot.getX(), ty - eyeRot.getY(), tz - eyeRot.getZ());
        if (to.lengthSquared() <= 1.0E-8D) return new Result(hit, 0.0D);

        double dot = look.normalize().dot(to.normalize());
        if (dot > 1.0D) dot = 1.0D;
        if (dot < -1.0D) dot = -1.0D;
        return new Result(hit, Math.toDegrees(Math.acos(dot)));
    }
}
