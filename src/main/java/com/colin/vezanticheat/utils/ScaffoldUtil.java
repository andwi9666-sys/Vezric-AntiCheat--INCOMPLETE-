package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class ScaffoldUtil {
    private ScaffoldUtil() {}

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        Location placed = data.getLastPlacedBlockLoc();
        Location against = data.getLastPlaceAgainstLoc();
        Location to = data.getLastLoc();
        Location from = data.getLastMoveFrom();
        if (placed == null || against == null || to == null) return null;
        if (placed.getWorld() == null || against.getWorld() == null || to.getWorld() == null) return null;
        if (!placed.getWorld().equals(against.getWorld()) || !placed.getWorld().equals(to.getWorld())) return null;
        if (from != null && (from.getWorld() == null || !from.getWorld().equals(to.getWorld()))) from = null;

        BlockFace face = data.getLastPlaceFace();
        Location eye = p.getEyeLocation();
        double eyeToPlaced = eye.distance(placed.clone().add(0.5, 0.5, 0.5));
        double eyeToAgainst = eye.distance(against.clone().add(0.5, 0.5, 0.5));
        double playerToPlacedH = horizontalDistance(to, placed.clone().add(0.5, 0.0, 0.5));
        double playerToAgainstH = horizontalDistance(to, against.clone().add(0.5, 0.0, 0.5));
        double minPlacedReach = minEyeDistanceToBlock(p, from, to, placed);
        double minAgainstReach = minEyeDistanceToBlock(p, from, to, against);

        Vector look = eye.getDirection().normalize();
        Vector toAgainst = against.clone().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        double aimDot = toAgainst.lengthSquared() <= 0.0001 ? 1.0 : look.dot(toAgainst.normalize());

        double moveH = 0.0;
        double behindDot = 0.0;
        if (from != null) {
            moveH = horizontalDistance(from, to);
            if (moveH > 0.03) {
                Vector moveDir = new Vector(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ()).normalize();
                Vector toPlaced = placed.clone().add(0.5, 0.0, 0.5).toVector().subtract(to.toVector());
                toPlaced.setY(0.0);
                if (toPlaced.lengthSquared() > 0.0001) {
                    behindDot = moveDir.dot(toPlaced.normalize());
                }
            }
        }

        double placedBelow = to.getY() - placed.getY();
        boolean supportBelow = face == BlockFace.UP;
        boolean horizontalSupport = face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
        // bridgeLike: broad vertical relation only; final scaffold gating should use extensionLike.
        boolean bridgeLike = placedBelow >= 0.0 && placedBelow <= 2.5;
        // towerLike: placed block is near the player vertically and very close horizontally (tower up)
        double towerMaxMoveH = plugin.getConfig().getDouble("scaffold-analysis.tower.max-move-h", 0.18D);
        boolean towerLike = placedBelow > -0.5 && placedBelow < 1.5 && playerToPlacedH < 1.1 && moveH <= towerMaxMoveH;
        // closeBuildLike: nearby defensive building around the player/bed, not bridge extension
        MovementContextAnalyzer.Context movement = MovementContextAnalyzer.analyze(plugin, p, data);

        double maxBuildMoveH = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-move-h", 0.24D);
        double maxSupportH = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-support-h", 1.10D);
        double maxPlacedH = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-placed-h", 1.35D);
        double minAimDot = plugin.getConfig().getDouble("scaffold-analysis.close-build.min-aim-dot", 0.50D);
        double maxEyePlaced = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-eye-placed", 4.25D);
        double maxPlacedBelow = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-placed-below", 1.45D);
        double maxBehindDot = plugin.getConfig().getDouble("scaffold-analysis.close-build.max-behind-dot", -0.18D);
        Location feet = to;
        boolean effectivelyGrounded = isEffectivelyGroundedForScaffold(plugin, p, data, feet);
        boolean edgeStand = isBlockEdgeStand(plugin, p, feet);
        double groundedMoveH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-max-move-h", 0.38D);
        double groundedSupportH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-max-support-h", 1.28D);
        double groundedPlacedH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-max-placed-h", 1.55D);
        double groundedAimDot = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-min-aim-dot", 0.25D);
        boolean closeBuildLike = moveH <= maxBuildMoveH
                && playerToAgainstH <= maxSupportH
                && playerToPlacedH <= maxPlacedH
                && eyeToPlaced <= maxEyePlaced
                && aimDot >= minAimDot
                && behindDot >= maxBehindDot
                && (horizontalSupport || placedBelow <= maxPlacedBelow);
        boolean groundedCloseBuildLike = effectivelyGrounded
                && moveH <= groundedMoveH
                && playerToAgainstH <= groundedSupportH
                && playerToPlacedH <= groundedPlacedH
                && aimDot >= groundedAimDot
                && behindDot >= maxBehindDot
                && (horizontalSupport || (supportBelow && placedBelow <= maxPlacedBelow));
        double groundedGenericMoveH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-max-move-h", 0.60D);
        double groundedGenericSupportH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-max-support-h", 1.65D);
        double groundedGenericPlacedH = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-max-placed-h", 1.85D);
        double groundedGenericAimDot = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-min-aim-dot", -0.10D);
        double groundedGenericBehindDot = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-max-behind-dot", -0.45D);
        double groundedGenericBelow = plugin.getConfig().getDouble("scaffold-analysis.close-build.grounded-generic-max-placed-below", 1.25D);
        boolean groundedGenericBuildLike = effectivelyGrounded
                && moveH <= groundedGenericMoveH
                && playerToAgainstH <= groundedGenericSupportH
                && playerToPlacedH <= groundedGenericPlacedH
                && aimDot >= groundedGenericAimDot
                && behindDot >= groundedGenericBehindDot
                && placedBelow <= groundedGenericBelow;
        double jumpMoveH = plugin.getConfig().getDouble("scaffold-analysis.jump-build.max-move-h", 0.42D);
        double jumpSupportH = plugin.getConfig().getDouble("scaffold-analysis.jump-build.max-support-h", 1.35D);
        double jumpPlacedH = plugin.getConfig().getDouble("scaffold-analysis.jump-build.max-placed-h", 1.70D);
        double jumpAimDot = plugin.getConfig().getDouble("scaffold-analysis.jump-build.min-aim-dot", 0.18D);
        double jumpBelow = plugin.getConfig().getDouble("scaffold-analysis.jump-build.max-placed-below", 1.85D);
        boolean jumpBuildLike = movement != null
                && movement.recentJump
                && p != null && !p.isOnGround()
                && moveH <= jumpMoveH
                && playerToAgainstH <= jumpSupportH
                && playerToPlacedH <= jumpPlacedH
                && aimDot >= jumpAimDot
                && behindDot >= maxBehindDot
                && (horizontalSupport || (supportBelow && placedBelow <= jumpBelow));
        double combatMoveH = plugin.getConfig().getDouble("scaffold-analysis.combat-build.max-move-h", 0.55D);
        double combatSupportH = plugin.getConfig().getDouble("scaffold-analysis.combat-build.max-support-h", 1.45D);
        double combatPlacedH = plugin.getConfig().getDouble("scaffold-analysis.combat-build.max-placed-h", 1.80D);
        double combatAimDot = plugin.getConfig().getDouble("scaffold-analysis.combat-build.min-aim-dot", 0.05D);
        double combatBelow = plugin.getConfig().getDouble("scaffold-analysis.combat-build.max-placed-below", 2.0D);
        boolean combatBuildLike = movement != null
                && movement.recentCombat
                && moveH <= combatMoveH
                && playerToAgainstH <= combatSupportH
                && playerToPlacedH <= combatPlacedH
                && aimDot >= combatAimDot
                && behindDot >= maxBehindDot
                && (horizontalSupport || (supportBelow && placedBelow <= combatBelow));
        boolean horizontalBuildLike = horizontalSupport
                && placedBelow <= 0.5D
                && effectivelyGrounded
                && moveH <= combatMoveH
                && aimDot >= combatAimDot;
        double speedBridgeMoveH = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.max-move-h", 0.42D);
        double speedBridgeSupportH = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.max-support-h", 1.15D);
        double speedBridgePlacedH = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.max-placed-h", 1.45D);
        double speedBridgeAimDot = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.min-aim-dot", 0.28D);
        double speedBridgeBehindDot = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.max-behind-dot", -0.72D);
        double speedBridgeBelow = plugin.getConfig().getDouble("scaffold-analysis.speed-bridge.max-placed-below", 1.45D);
        boolean speedBridgeLike = effectivelyGrounded
                && data.isLastPlaceSneaking()
                && moveH <= speedBridgeMoveH
                && playerToAgainstH <= speedBridgeSupportH
                && playerToPlacedH <= speedBridgePlacedH
                && aimDot >= speedBridgeAimDot
                && behindDot >= speedBridgeBehindDot
                && face != null
                && face != BlockFace.DOWN
                && placedBelow <= speedBridgeBelow;
        double groundedBridgeMoveH = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.max-move-h", 0.52D);
        double groundedBridgeSupportH = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.max-support-h", 1.25D);
        double groundedBridgePlacedH = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.max-placed-h", 1.55D);
        double groundedBridgeAimDot = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.min-aim-dot", 0.10D);
        double groundedBridgeBehindDot = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.max-behind-dot", -0.82D);
        double groundedBridgeBelow = plugin.getConfig().getDouble("scaffold-analysis.grounded-bridge.max-placed-below", 1.55D);
        boolean groundedBridgeLike = effectivelyGrounded
                && moveH <= groundedBridgeMoveH
                && playerToAgainstH <= groundedBridgeSupportH
                && playerToPlacedH <= groundedBridgePlacedH
                && aimDot >= groundedBridgeAimDot
                && behindDot >= groundedBridgeBehindDot
                && face != null
                && face != BlockFace.DOWN
                && placedBelow <= groundedBridgeBelow;
        double edgeBridgeMoveH = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.max-move-h", 0.48D);
        double edgeBridgeSupportH = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.max-support-h", 1.35D);
        double edgeBridgePlacedH = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.max-placed-h", 1.65D);
        double edgeBridgeAimDot = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.min-aim-dot", 0.08D);
        double edgeBridgeBehindDot = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.max-behind-dot", -0.88D);
        double edgeBridgeBelow = plugin.getConfig().getDouble("scaffold-analysis.edge-bridge.max-placed-below", 1.65D);
        boolean edgeBridgeLike = effectivelyGrounded
                && supportBelow
                && moveH <= edgeBridgeMoveH
                && playerToAgainstH <= edgeBridgeSupportH
                && playerToPlacedH <= edgeBridgePlacedH
                && aimDot >= edgeBridgeAimDot
                && behindDot >= edgeBridgeBehindDot
                && face != null
                && face != BlockFace.DOWN
                && placedBelow <= edgeBridgeBelow;
        double minExtensionPlacedH = plugin.getConfig().getDouble("scaffold-analysis.extension.min-placed-h", 1.05D);
        double minExtensionSupportH = plugin.getConfig().getDouble("scaffold-analysis.extension.min-support-h", 0.95D);
        double minExtensionMoveH = plugin.getConfig().getDouble("scaffold-analysis.extension.min-move-h", 0.12D);
        double maxExtensionBehindDot = plugin.getConfig().getDouble("scaffold-analysis.extension.max-behind-dot", -0.20D);
        double minExtensionBelow = plugin.getConfig().getDouble("scaffold-analysis.extension.min-placed-below", 0.70D);
        float placePitch = data.getLastPlacePitch();
        double extBridgeMinPitch = plugin.getConfig().getDouble("scaffold-analysis.extension-bridge.min-bridge-pitch", 10.0D);
        double extBridgeMinPlacedH = plugin.getConfig().getDouble("scaffold-analysis.extension-bridge.min-placed-h", 0.85D);
        double extBridgeMaxBehindDot = plugin.getConfig().getDouble("scaffold-analysis.extension-bridge.max-behind-dot", -0.15D);
        double extBridgeMinAimDot = plugin.getConfig().getDouble("scaffold-analysis.extension-bridge.min-aim-dot", 0.06D);
        double extBridgeMoveH = plugin.getConfig().getDouble("scaffold-analysis.extension-bridge.max-move-h", 0.55D);
        boolean extensionBridgeLike = supportBelow
                && placedBelow >= minExtensionBelow
                && placePitch >= extBridgeMinPitch
                && moveH <= extBridgeMoveH
                && aimDot >= extBridgeMinAimDot
                && (playerToPlacedH >= extBridgeMinPlacedH || behindDot <= extBridgeMaxBehindDot);
        closeBuildLike = closeBuildLike || groundedCloseBuildLike || groundedGenericBuildLike || jumpBuildLike
                || combatBuildLike || horizontalBuildLike || speedBridgeLike || groundedBridgeLike
                || edgeBridgeLike || extensionBridgeLike;
        double faceExpand = plugin.getConfig().getDouble("scaffold-analysis.face.expand", 0.08D);
        if (edgeStand || (effectivelyGrounded && supportBelow)) {
            faceExpand += plugin.getConfig().getDouble("scaffold-analysis.edge-stand.face-expand-bonus", 0.22D);
        }
        boolean hiddenFacePlace = isHiddenFacePlace(p, from, to, against, face, faceExpand);
        long supportHistoryMs = plugin.getConfig().getLong("scaffold-analysis.packet-history.support-history-ms", 350L);
        boolean invalidSupport = isInvalidSupport(data, against, supportHistoryMs);
        float placeYaw = data.getLastPlaceYaw();
        double faceReach = plugin.getConfig().getDouble("scaffold-analysis.face.reach", 4.25D);
        double faceStep = plugin.getConfig().getDouble("scaffold-analysis.face.ray-step", 0.08D);
        boolean faceRayHit = canRayHitPlacedAgainstFace(to, p.getEyeHeight(), placeYaw, placePitch, against, faceReach, faceStep);
        if (from != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
            faceRayHit = faceRayHit || canRayHitPlacedAgainstFace(from, p.getEyeHeight(), placeYaw, placePitch, against, faceReach, faceStep);
        }
        double lenientAimDot = plugin.getConfig().getDouble("scaffold-analysis.edge-stand.min-lenient-aim-dot", 0.12D);
        boolean bridgeGeometryLenient = closeBuildLike || edgeBridgeLike || extensionBridgeLike
                || (effectivelyGrounded && supportBelow && placedBelow >= minExtensionBelow * 0.75D
                && aimDot >= lenientAimDot);
        if (bridgeGeometryLenient) {
            hiddenFacePlace = false;
            if (aimDot >= lenientAimDot) {
                faceRayHit = true;
            }
            if (effectivelyGrounded && playerToAgainstH <= edgeBridgeSupportH) {
                invalidSupport = false;
            }
        }
        boolean extensionLike = !closeBuildLike
                && supportBelow
                && placedBelow >= minExtensionBelow
                && (playerToPlacedH >= minExtensionPlacedH
                || playerToAgainstH >= minExtensionSupportH
                || moveH >= minExtensionMoveH
                || behindDot <= maxExtensionBehindDot
                || hiddenFacePlace
                || invalidSupport
                || !faceRayHit);
        boolean sneakingBridge = data.isLastPlaceSneaking();
        double quality = movement == null ? 1.0 : movement.cleanliness;
        boolean clean = movement == null || movement.cleanliness >= 0.42;
        if (moveH < plugin.getConfig().getDouble("scaffold-analysis.low-move-threshold", 0.05D)) {
            quality -= 0.08D;
        }
        quality = Math.max(0.0D, Math.min(1.0D, quality));

        return new Context(face, eyeToPlaced, eyeToAgainst, minPlacedReach, minAgainstReach,
                playerToPlacedH, playerToAgainstH, aimDot, moveH,
                behindDot, placedBelow, supportBelow, horizontalSupport, bridgeLike, towerLike, extensionLike, closeBuildLike,
                speedBridgeLike, edgeBridgeLike, extensionBridgeLike, effectivelyGrounded, edgeStand, bridgeGeometryLenient,
                hiddenFacePlace, invalidSupport, faceRayHit, sneakingBridge,
                clean, quality, movement == null ? "" : movement.debugSummary());
    }

    /**
     * Derives placement context from a block-place packet so scaffold checks evaluate the current
     * placement instead of the previous Bukkit event (packet arrives before BlockPlaceEvent).
     */
    public static void applyPacketPlaceContext(VezAntiCheat plugin, Player p, PlayerData data,
                                               Location againstLoc, int faceId,
                                               float yaw, float pitch, Location packetLoc, long now) {
        if (data == null || againstLoc == null || againstLoc.getWorld() == null) return;

        BlockFace face = faceFromId(faceId);
        Location placed = placedLocationFromFace(againstLoc, face);
        if (placed == null) return;

        data.setLastPlacedBlockLoc(placed);
        data.setLastPlaceAgainstLoc(againstLoc);
        data.setLastPlaceFace(face);
        data.setLastPlaceYaw(yaw);
        data.setLastPlacePitch(pitch);

        Location feet = packetLoc != null ? packetLoc : (p != null ? p.getLocation() : null);
        boolean grounded = p != null && p.isOnGround();
        if (!grounded) {
            grounded = isEffectivelyGroundedForScaffold(plugin, p, data, feet);
        }
        data.setLastPlaceOnGround(grounded);
        if (p != null) {
            data.setLastPlaceSneaking(p.isSneaking());
        }

        data.setLastBlockPlace(now);
        data.setPlaceStreak(data.getPlaceStreak() + 1);
        long last = data.getLastScaffoldPlaceTime();
        if (last != 0L) {
            long dt = now - last;
            if (dt > 0L && dt < 2000L) {
                data.getScaffoldIntervals().addLast(dt);
                while (data.getScaffoldIntervals().size() > 20) {
                    data.getScaffoldIntervals().removeFirst();
                }
            }
        }
        data.setLastScaffoldPlaceTime(now);
    }

    public static BlockFace faceFromId(int faceId) {
        switch (faceId) {
            case 0: return BlockFace.DOWN;
            case 1: return BlockFace.UP;
            case 2: return BlockFace.NORTH;
            case 3: return BlockFace.SOUTH;
            case 4: return BlockFace.WEST;
            case 5: return BlockFace.EAST;
            default: return BlockFace.SELF;
        }
    }

    public static Location placedLocationFromFace(Location against, BlockFace face) {
        if (against == null || face == null || face == BlockFace.SELF) return null;
        Location placed = against.clone();
        placed.add(face.getModX(), face.getModY(), face.getModZ());
        return placed;
    }

    /**
     * Client {@code onGround} is often false when standing on the very edge of a block — no footstep
     * particles or sounds, but the player is still supported. Treat block-under-foot as grounded.
     */
    public static boolean isEffectivelyGroundedForScaffold(VezAntiCheat plugin, Player p, PlayerData data,
                                                           Location feet) {
        if (data != null && data.isLastPlaceOnGround()) return true;
        if (p != null && p.isOnGround()) return true;
        if (feet == null && p != null) feet = p.getLocation();
        return hasBridgingSupportBelow(plugin, p, feet);
    }

    public static boolean isBlockEdgeStand(VezAntiCheat plugin, Player p, Location feet) {
        if (plugin == null || feet == null || feet.getWorld() == null) return false;

        double edgeFrac = plugin.getConfig().getDouble("scaffold-analysis.edge-stand.fraction-threshold", 0.10D);
        double maxAbove = plugin.getConfig().getDouble("scaffold-analysis.edge-stand.max-above-block", 0.62D);

        int bx = feet.getBlockX();
        int by = feet.getBlockY() - 1;
        int bz = feet.getBlockZ();
        Material below = feet.getWorld().getBlockAt(bx, by, bz).getType();
        double blockTopY = by + 1.0D;
        if (isAirOrLiquid(below)) {
            below = feet.getWorld().getBlockAt(bx, feet.getBlockY(), bz).getType();
            blockTopY = feet.getBlockY();
            if (isAirOrLiquid(below)) return false;
        }

        double dy = feet.getY() - blockTopY;
        if (dy < -0.08D || dy > maxAbove) return false;

        double fx = normalizeBlockFraction(feet.getX() - Math.floor(feet.getX()));
        double fz = normalizeBlockFraction(feet.getZ() - Math.floor(feet.getZ()));
        return fx <= edgeFrac || fx >= (1.0D - edgeFrac) || fz <= edgeFrac || fz >= (1.0D - edgeFrac);
    }

    public static boolean hasBridgingSupportBelow(VezAntiCheat plugin, Player p, Location feet) {
        if (plugin == null || feet == null || feet.getWorld() == null) return false;

        double maxAbove = plugin.getConfig().getDouble("scaffold-analysis.edge-stand.max-above-block", 0.62D);
        double halfWidth = plugin.getConfig().getDouble("scaffold-analysis.edge-stand.support-half-width", 0.30D);
        double step = Math.max(0.15D, halfWidth);

        for (double ox = -halfWidth; ox <= halfWidth + 0.001D; ox += step) {
            for (double oz = -halfWidth; oz <= halfWidth + 0.001D; oz += step) {
                Location probe = feet.clone().add(ox, -0.05D, oz);
                int by = (int) Math.floor(probe.getY() - 0.01D);
                Material material = probe.getWorld().getBlockAt(probe.getBlockX(), by, probe.getBlockZ()).getType();
                if (isAirOrLiquid(material)) continue;
                double blockTop = by + 1.0D;
                double dy = feet.getY() - blockTop;
                if (dy >= -0.10D && dy <= maxAbove) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double normalizeBlockFraction(double fraction) {
        double normalized = fraction % 1.0D;
        if (normalized < 0.0D) normalized += 1.0D;
        return normalized;
    }

    public static boolean shouldSkip(Player p, PlayerData data) {
        if (p == null || data == null) return true;
        if (p.isFlying() || p.getAllowFlight()) return true;
        if (p.isInsideVehicle()) return true;
        if (data.isTeleportExempt()) return true;
        return false;
    }

    /**
     * True during the bridge-movement grace window after a recent place when geometry/context
     * matches sustained right-click bridging (edge stand, extension bridge, lenient geometry).
     */
    public static boolean shouldExemptBridgingFlag(VezAntiCheat plugin, PlayerData data, Context ctx, long nowMs) {
        if (plugin == null || data == null || ctx == null) return false;
        long bridgeGraceMs = plugin.getConfig().getLong("scaffold-analysis.bridge-movement-grace-ms", 900L);
        return shouldExemptBridgingFlag(bridgeGraceMs, plugin, data, ctx, nowMs);
    }

    static boolean shouldExemptBridgingFlag(long bridgeGraceMs, PlayerData data, Context ctx, long nowMs) {
        return shouldExemptBridgingFlag(bridgeGraceMs, null, data, ctx, nowMs);
    }

    static boolean shouldExemptBridgingFlag(long bridgeGraceMs, VezAntiCheat plugin, PlayerData data,
                                            Context ctx, long nowMs) {
        if (data == null || ctx == null) return false;

        long placeMs = Math.max(data.getLastBlockPlace(), data.getLastBlockPlacePacketTime());
        if (placeMs <= 0L || (nowMs - placeMs) > bridgeGraceMs) return false;

        if (ctx.speedBridgeLike || ctx.edgeBridgeLike || ctx.extensionBridgeLike || ctx.bridgeGeometryLenient) {
            return true;
        }
        if (ctx.extensionLike && ctx.effectivelyGrounded && ctx.supportBelow) {
            return true;
        }

        Location feet = data.getLastLoc();
        return feet != null
                && (hasBridgingSupportBelow(plugin, null, feet) || isBlockEdgeStand(plugin, null, feet));
    }

    /** Active bridging with human timing/pitch variance — do not treat as scaffold cheat. */
    public static boolean shouldTreatBridgeAsLegit(VezAntiCheat plugin, PlayerData data, Context ctx,
                                                   Stats stats, long nowMs) {
        if (shouldExemptBridgingFlag(plugin, data, ctx, nowMs)) return true;
        if (plugin == null || data == null || ctx == null) return false;
        if (!isActiveBridgeContext(ctx, data)) return false;

        int pitchSample = plugin.tierCfg().checkInt("PrismScaffoldA", "pitchSampleSize", 6);
        double pitchRange = pitchCv(data.getScaffoldPitchHistory(), pitchSample);
        double maxTimingCv = plugin.tierCfg().checkDouble("PrismScaffoldA", "maxTimingCv", 0.08D);
        double maxPitchRange = plugin.tierCfg().checkDouble("PrismScaffoldA", "maxPitchRange", 4.0D);
        double maxSpreadMs = plugin.tierCfg().checkDouble("PrismScaffoldA", "maxSpreadMs", 75.0D);
        return PrismPatternSupport.hasBridgeHumanImperfection(stats, pitchRange, maxTimingCv, maxPitchRange, maxSpreadMs);
    }

    private static boolean isActiveBridgeContext(Context ctx, PlayerData data) {
        if (ctx == null || data == null) return false;
        if (data.getPlaceStreak() < 3) return false;
        return ctx.extensionLike || ctx.edgeBridgeLike || ctx.speedBridgeLike
                || ctx.extensionBridgeLike || ctx.bridgeGeometryLenient || ctx.bridgeLike;
    }

    public static Stats timingStats(Deque<Long> intervals, int sampleSize) {
        if (intervals == null || intervals.size() < sampleSize) return null;
        List<Long> sample = new ArrayList<Long>(intervals);
        sample = sample.subList(sample.size() - sampleSize, sample.size());
        double avg = 0.0;
        for (Long v : sample) avg += v.longValue();
        avg /= sample.size();
        double sum = 0.0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (Long v : sample) {
            double d = v.longValue() - avg;
            sum += d * d;
            min = Math.min(min, v.longValue());
            max = Math.max(max, v.longValue());
        }
        double sd = sample.size() <= 1 ? 0.0 : Math.sqrt(sum / (sample.size() - 1));
        double cv = avg <= 0.0 ? 999.0 : sd / avg;
        return new Stats(avg, sd, cv, min == Long.MAX_VALUE ? 0L : min, max == Long.MIN_VALUE ? 0L : max);
    }

    /**
     * Returns the pitch range (max - min degrees) across recent placement pitch values.
     * Scaffold cheats lock pitch to a narrow band; legitimate bridgers vary naturally (20-60 degrees).
     * Using range avoids the CV zero-mean problem when avg pitch is near 0 degrees.
     * Returns 999.0 when there are fewer than sampleSize entries (sentinel for "not enough data").
     */
    public static double pitchCv(Deque<Float> pitchHistory, int sampleSize) {
        if (pitchHistory == null || pitchHistory.size() < sampleSize) return 999.0;
        List<Float> sample = new ArrayList<Float>(pitchHistory);
        sample = sample.subList(sample.size() - sampleSize, sample.size());
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Float v : sample) {
            if (v.floatValue() < min) min = v.floatValue();
            if (v.floatValue() > max) max = v.floatValue();
        }
        return max - min;
    }

    /**
     * Returns true when the pitch at placement is suspiciously shallow.
     * Legitimate bridging requires looking downward (pitch > 0 in Minecraft means looking down).
     * minBridgePitch is the minimum positive pitch considered legitimate (e.g. 15.0f).
     */
    public static boolean isShallowPitch(float pitch, float minBridgePitch) {
        return pitch < minBridgePitch;
    }

    private static double horizontalDistance(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    public static boolean isInvalidSupport(PlayerData data, Location against, long historyWindowMs) {
        if (against == null || against.getWorld() == null) return true;
        Material type = against.getBlock().getType();
        if (!isAirOrLiquid(type)) return false;

        if (data != null) {
            long now = System.currentTimeMillis();
            for (PlayerData.BlockStateSample sample : data.getRecentBlockStateHistory()) {
                if (!sample.matches(against)) continue;
                if (now - sample.getTime() > Math.max(0L, historyWindowMs)) continue;
                if (!isAirOrLiquid(sample.getOldType()) || !isAirOrLiquid(sample.getNewType())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double minEyeDistanceToBlock(Player p, Location from, Location to, Location blockLoc) {
        if (p == null || to == null || blockLoc == null || blockLoc.getWorld() == null) return Double.MAX_VALUE;
        double min = distanceEyeToBlock(to, p.getEyeHeight(), blockLoc);
        if (from != null && from.getWorld() != null && from.getWorld().equals(blockLoc.getWorld())) {
            min = Math.min(min, distanceEyeToBlock(from, p.getEyeHeight(), blockLoc));
        }
        return min;
    }

    private static double distanceEyeToBlock(Location base, double eyeHeight, Location blockLoc) {
        double x = base.getX();
        double y = base.getY() + eyeHeight;
        double z = base.getZ();
        double minX = blockLoc.getBlockX();
        double maxX = blockLoc.getBlockX() + 1.0D;
        double minY = blockLoc.getBlockY();
        double maxY = blockLoc.getBlockY() + 1.0D;
        double minZ = blockLoc.getBlockZ();
        double maxZ = blockLoc.getBlockZ() + 1.0D;
        double cx = clamp(x, minX, maxX);
        double cy = clamp(y, minY, maxY);
        double cz = clamp(z, minZ, maxZ);
        double dx = x - cx;
        double dy = y - cy;
        double dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static boolean canRayHitPlacedAgainstFace(Player p, Location from, Location to, Location against, double reach, double step) {
        if (p == null || against == null || to == null || against.getWorld() == null) return false;
        List<Location> eyes = new ArrayList<Location>(2);
        eyes.add(new Location(to.getWorld(), to.getX(), to.getY() + p.getEyeHeight(), to.getZ(), p.getLocation().getYaw(), p.getLocation().getPitch()));
        if (from != null && from.getWorld() != null && from.getWorld().equals(to.getWorld())) {
            eyes.add(new Location(from.getWorld(), from.getX(), from.getY() + p.getEyeHeight(), from.getZ(), p.getLocation().getYaw(), p.getLocation().getPitch()));
        }

        for (Location eye : eyes) {
            Vector dir = eye.getDirection();
            if (dir.lengthSquared() <= 1.0E-6D) continue;
            if (rayHitsBlockBox(eye.toVector(), dir.normalize(), against, reach, step)) {
                return true;
            }
        }
        return false;
    }

    public static boolean canRayHitPlacedAgainstFace(Location baseLoc, double eyeHeight, float yaw, float pitch,
                                                     Location against, double reach, double step) {
        if (baseLoc == null || against == null || against.getWorld() == null || baseLoc.getWorld() == null) return false;
        if (!baseLoc.getWorld().equals(against.getWorld())) return false;
        Location eye = new Location(baseLoc.getWorld(), baseLoc.getX(), baseLoc.getY() + eyeHeight, baseLoc.getZ(), yaw, pitch);
        Vector dir = eye.getDirection();
        if (dir.lengthSquared() <= 1.0E-6D) return false;
        return rayHitsBlockBox(eye.toVector(), dir.normalize(), against, reach, step);
    }

    private static boolean isAirOrLiquid(Material type) {
        return type == null
                || type == Material.AIR
                || type == Material.WATER
                || type == Material.STATIONARY_WATER
                || type == Material.LAVA
                || type == Material.STATIONARY_LAVA;
    }

    private static boolean rayHitsBlockBox(Vector origin, Vector dir, Location blockLoc, double reach, double step) {
        double traveled = 0.0D;
        while (traveled <= reach) {
            Vector point = origin.clone().add(dir.clone().multiply(traveled));
            if (point.getX() >= blockLoc.getBlockX() && point.getX() <= blockLoc.getBlockX() + 1.0D
                    && point.getY() >= blockLoc.getBlockY() && point.getY() <= blockLoc.getBlockY() + 1.0D
                    && point.getZ() >= blockLoc.getBlockZ() && point.getZ() <= blockLoc.getBlockZ() + 1.0D) {
                return true;
            }
            traveled += Math.max(0.03D, step);
        }
        return false;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static boolean isHiddenFacePlace(Player p, Location from, Location to, Location against, BlockFace face, double expand) {
        if (p == null || to == null || against == null || face == null) return false;
        double eyeHeight = p.getEyeHeight();

        double minX = Math.min(from == null ? to.getX() : from.getX(), to.getX()) - expand;
        double maxX = Math.max(from == null ? to.getX() : from.getX(), to.getX()) + expand;
        double minY = Math.min((from == null ? to.getY() : from.getY()) + eyeHeight, to.getY() + eyeHeight) - expand;
        double maxY = Math.max((from == null ? to.getY() : from.getY()) + eyeHeight, to.getY() + eyeHeight) + expand;
        double minZ = Math.min(from == null ? to.getZ() : from.getZ(), to.getZ()) - expand;
        double maxZ = Math.max(from == null ? to.getZ() : from.getZ(), to.getZ()) + expand;

        double blockMinX = against.getBlockX();
        double blockMaxX = against.getBlockX() + 1.0D;
        double blockMinY = against.getBlockY();
        double blockMaxY = against.getBlockY() + 1.0D;
        double blockMinZ = against.getBlockZ();
        double blockMaxZ = against.getBlockZ() + 1.0D;

        boolean intersects = maxX >= blockMinX && minX <= blockMaxX
                && maxY >= blockMinY && minY <= blockMaxY
                && maxZ >= blockMinZ && minZ <= blockMaxZ;
        if (intersects) return false;

        switch (face) {
            case NORTH:
                return minZ > blockMinZ;
            case SOUTH:
                return maxZ < blockMaxZ;
            case EAST:
                return maxX < blockMaxX;
            case WEST:
                return minX > blockMinX;
            case UP:
                return maxY < blockMaxY;
            case DOWN:
                return minY > blockMinY;
            default:
                return false;
        }
    }

    public static final class Stats {
        public final double avg;
        public final double sd;
        public final double cv;
        public final long min;
        public final long max;

        public Stats(double avg, double sd, double cv, long min, long max) {
            this.avg = avg;
            this.sd = sd;
            this.cv = cv;
            this.min = min;
            this.max = max;
        }
    }

    public static final class Context {
        public final BlockFace face;
        public final double eyeToPlaced;
        public final double eyeToAgainst;
        public final double minPlacedReach;
        public final double minAgainstReach;
        public final double playerToPlacedH;
        public final double playerToAgainstH;
        public final double aimDot;
        public final double moveH;
        public final double behindDot;
        public final double placedBelow;
        public final boolean supportBelow;
        public final boolean horizontalSupport;
        public final boolean bridgeLike;
        public final boolean towerLike;
        public final boolean extensionLike;
        public final boolean closeBuildLike;
        public final boolean speedBridgeLike;
        public final boolean edgeBridgeLike;
        public final boolean extensionBridgeLike;
        public final boolean effectivelyGrounded;
        public final boolean edgeStand;
        public final boolean bridgeGeometryLenient;
        public final boolean hiddenFacePlace;
        public final boolean invalidSupport;
        public final boolean faceRayHit;
        public final boolean sneakingBridge;
        public final boolean clean;
        public final double quality;
        public final String debug;

        public Context(BlockFace face, double eyeToPlaced, double eyeToAgainst, double minPlacedReach, double minAgainstReach, double playerToPlacedH,
                       double playerToAgainstH, double aimDot, double moveH, double behindDot, double placedBelow,
                       boolean supportBelow, boolean horizontalSupport, boolean bridgeLike, boolean towerLike,
                       boolean extensionLike, boolean closeBuildLike, boolean speedBridgeLike,
                       boolean edgeBridgeLike, boolean extensionBridgeLike, boolean effectivelyGrounded, boolean edgeStand, boolean bridgeGeometryLenient,
                       boolean hiddenFacePlace, boolean invalidSupport, boolean faceRayHit,
                       boolean sneakingBridge, boolean clean, double quality, String debug) {
            this.face = face;
            this.eyeToPlaced = eyeToPlaced;
            this.eyeToAgainst = eyeToAgainst;
            this.minPlacedReach = minPlacedReach;
            this.minAgainstReach = minAgainstReach;
            this.playerToPlacedH = playerToPlacedH;
            this.playerToAgainstH = playerToAgainstH;
            this.aimDot = aimDot;
            this.moveH = moveH;
            this.behindDot = behindDot;
            this.placedBelow = placedBelow;
            this.supportBelow = supportBelow;
            this.horizontalSupport = horizontalSupport;
            this.bridgeLike = bridgeLike;
            this.towerLike = towerLike;
            this.extensionLike = extensionLike;
            this.closeBuildLike = closeBuildLike;
            this.speedBridgeLike = speedBridgeLike;
            this.edgeBridgeLike = edgeBridgeLike;
            this.extensionBridgeLike = extensionBridgeLike;
            this.effectivelyGrounded = effectivelyGrounded;
            this.edgeStand = edgeStand;
            this.bridgeGeometryLenient = bridgeGeometryLenient;
            this.hiddenFacePlace = hiddenFacePlace;
            this.invalidSupport = invalidSupport;
            this.faceRayHit = faceRayHit;
            this.sneakingBridge = sneakingBridge;
            this.clean = clean;
            this.quality = quality;
            this.debug = debug;
        }
    }
}
