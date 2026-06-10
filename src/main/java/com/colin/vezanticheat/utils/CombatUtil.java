package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Deque;
import java.util.UUID;

public final class CombatUtil {
    /** Vanilla 1.8.8 combat hitbox expansion in blocks (each axis). */
    public static final double VANILLA_HITBOX_EXPANSION = 0.1D;
    /** Nominal vanilla reach before combat hitbox expansion. */
    public static final double VANILLA_BASE_REACH = 3.0D;

    private CombatUtil() {}

    /**
     * Effective allowed reach for expanded hitbox distance checks.
     * New configs use {@code maxReach: 3.1}; legacy configs may split {@code 3.0 + reachMargin 0.1}.
     */
    public static double resolveEffectiveMaxReach(VezAntiCheat plugin, String checkName) {
        if (plugin == null) return VANILLA_BASE_REACH + VANILLA_HITBOX_EXPANSION;
        double maxReach = plugin.tierCfg().checkDouble(checkName, "maxReach", 3.1D);
        double reachMargin = plugin.tierCfg().checkDouble(checkName, "reachMargin", 0.0D);
        return effectiveMaxReachFromValues(maxReach, reachMargin);
    }

    static double effectiveMaxReachFromValues(double maxReach, double reachMargin) {
        if (reachMargin > 0.0D && maxReach <= VANILLA_BASE_REACH) {
            return maxReach + reachMargin;
        }
        return maxReach;
    }

    /** Reads {@code combat-engine.reach-1_8-margin} when the plugin config is available. */
    public static double vanillaExpansion(Plugin plugin) {
        if (plugin == null) return VANILLA_HITBOX_EXPANSION;
        return plugin.getConfig().getDouble("combat-engine.reach-1_8-margin", VANILLA_HITBOX_EXPANSION);
    }

    /**
     * Expanded combat AABB matching vanilla {@code AxisAlignedBB.expand(0.1, 0.1, 0.1)}:
     * symmetric growth on X/Z half-width and on Y above feet and below feet.
     */
    public static CombatAabb buildCombatAabb(Location feet, double width, double height, double expansion) {
        if (feet == null) {
            return new CombatAabb(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return buildCombatAabb(feet.getX(), feet.getY(), feet.getZ(), width, height, expansion);
    }

    public static CombatAabb buildCombatAabb(double x, double y, double z, double width, double height, double expansion) {
        double half = Math.max(0.1D, width / 2.0D) + expansion;
        double bodyHeight = Math.max(0.1D, height);
        return new CombatAabb(
                x - half, y - expansion, z - half,
                x + half, y + bodyHeight + expansion, z + half
        );
    }

    public static Entity resolveTarget(Player attacker, UUID targetId) {
        if (attacker == null || targetId == null || attacker.getWorld() == null) return null;

        Player player = Bukkit.getPlayer(targetId);
        if (player != null && player.isOnline() && player.getWorld().equals(attacker.getWorld())) {
            return player;
        }

        for (Entity entity : attacker.getWorld().getEntities()) {
            if (targetId.equals(entity.getUniqueId())) {
                return entity;
            }
        }
        return null;
    }

    public static double entityWidth(Entity entity) {
        if (entity instanceof Player) return 0.60;
        if (entity instanceof LivingEntity) return 0.70;
        return 0.60;
    }

    public static double entityHeight(Entity entity) {
        if (entity instanceof Player) {
            return ((Player) entity).isSneaking() ? 1.65 : 1.80;
        }
        if (entity instanceof LivingEntity) return 1.80;
        return 1.80;
    }

    public static double entityHeight(Entity entity, boolean sneaking) {
        if (entity instanceof Player) {
            return sneaking ? 1.65 : 1.80;
        }
        return entityHeight(entity);
    }

    /**
     * Calculate distance from eye to entity hitbox. Applies vanilla 1.8.8 combat
     * hitbox expansion of 0.1 blocks (used for all attack interactions).
     */
    public static double distanceToHitbox(Location eye, Entity entity) {
        if (eye == null || entity == null) return 0.0;
        return distanceToHitbox(eye, entity.getLocation(), entityWidth(entity), entityHeight(entity));
    }

    /**
     * Calculate distance from eye to hitbox with default 0.1 block expansion.
     * Minecraft 1.8.8 expands all combat hitboxes by 0.1 blocks in every direction.
     */
    public static double distanceToHitbox(Location eye, Location base, double width, double height) {
        return distanceToHitbox(eye, base, width, height, VANILLA_HITBOX_EXPANSION);
    }

    /**
     * Calculate distance from eye to hitbox with custom expansion.
     * @param expansion Hitbox expansion in blocks (vanilla 1.8.8 combat uses 0.1)
     */
    public static double distanceToHitbox(Location eye, Location base, double width, double height, double expansion) {
        if (eye == null || base == null || eye.getWorld() == null || base.getWorld() == null) return 0.0;
        if (!eye.getWorld().equals(base.getWorld())) return Double.MAX_VALUE;

        return buildCombatAabb(base, width, height, expansion).distanceTo(eye.toVector());
    }

    public static double lookDotToHitbox(Location eye, Location base, double width, double height) {
        return lookDotToHitbox(eye, base, width, height, VANILLA_HITBOX_EXPANSION);
    }

    public static double lookDotToHitbox(Location eye, Location base, double width, double height, double expansion) {
        if (eye == null || base == null) return -1.0;

        Vector look = eye.getDirection();
        if (look.lengthSquared() <= 1.0E-8) return -1.0;

        Vector to = buildCombatAabb(base, width, height, expansion)
                .closestPoint(eye.toVector())
                .subtract(eye.toVector());
        if (to.lengthSquared() <= 1.0E-8) return 1.0;

        return look.normalize().dot(to.normalize());
    }

    public static double angularError(Location eye, Location base, double width, double height) {
        return angularError(eye, base, width, height, VANILLA_HITBOX_EXPANSION);
    }

    public static double angularError(Location eye, Location base, double width, double height, double expansion) {
        if (eye == null || base == null) return 180.0;

        Vector look = eye.getDirection();
        Vector to = buildCombatAabb(base, width, height, expansion)
                .closestPoint(eye.toVector())
                .subtract(eye.toVector());
        if (look.lengthSquared() <= 1.0E-8 || to.lengthSquared() <= 1.0E-8) return 0.0;

        double dot = clamp(look.normalize().dot(to.normalize()), -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    /**
     * True when attack geometry only overlaps the vanilla combat expansion shell:
     * look ray misses the unexpanded box but hits the expanded box, or closest
     * approach sits in the expansion band outside the raw AABB.
     */
    public static boolean isLegitExpansionMarginHit(Location eye, Location base, double width, double height) {
        return isLegitExpansionMarginHit(eye, base, width, height, VANILLA_HITBOX_EXPANSION);
    }

    public static boolean isLegitExpansionMarginHit(Location eye, Location base, double width, double height,
                                                    double expansion) {
        if (eye == null || base == null || expansion <= 0.0) return false;

        double rawDist = distanceToHitbox(eye, base, width, height, 0.0);
        double expandedDist = distanceToHitbox(eye, base, width, height, expansion);

        RayTraceResult rawRay = rayTraceToHitbox(eye, base, width, height, 0.0, 6.0);
        RayTraceResult expRay = rayTraceToHitbox(eye, base, width, height, expansion, 6.0);
        boolean rawMiss = rawRay != null && !rawRay.isHit();
        boolean expHit = expRay != null && expRay.isHit();
        if (rawMiss && expHit) return true;

        return rawDist > 0.005 && expandedDist <= 0.04;
    }

    public static double horizontalDistance(Location a, Location b) {
        if (a == null || b == null) return 0.0;
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distance(Location a, Location b) {
        if (a == null || b == null) return 0.0;
        if (a.getWorld() != null && b.getWorld() != null && !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.distance(b);
    }

    public static float angleDiff(float a, float b) {
        float diff = Math.abs(a - b) % 360.0F;
        return diff > 180.0F ? 360.0F - diff : diff;
    }

    public static long compensationWindowMs(int ping, long baseMs, double pingFactor, long maxMs) {
        long computed = baseMs + Math.round(Math.max(0, ping) * pingFactor);
        return Math.max(0L, Math.min(maxMs, computed));
    }

    /**
     * Ray-walk between two points; returns true if any solid block intersects the path.
     */
    public static boolean isRayBlockedBySolid(Location from, Location to, double step, double hitboxExpand, double endMargin) {
        if (from == null || to == null) return false;
        if (from.getWorld() == null || to.getWorld() == null) return false;
        if (!from.getWorld().equals(to.getWorld())) return false;

        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector delta = end.clone().subtract(start);
        double len = delta.length();
        if (len <= 0.01) return false;

        Vector dir = delta.clone().multiply(1.0 / len);
        double traveled = 0.0;
        double maxTravel = Math.max(0.0, len - Math.max(0.10, endMargin));
        while (traveled <= maxTravel) {
            Vector point = start.clone().add(dir.clone().multiply(traveled));
            if (isSolidAt(from, point.getX(), point.getY(), point.getZ(), hitboxExpand)) {
                return true;
            }
            traveled += Math.max(0.05, step);
        }
        return false;
    }

    private static boolean isSolidAt(Location refWorld, double x, double y, double z, double expand) {
        double[] offsets = new double[] { 0.0, expand, -expand };
        for (double ox : offsets) {
            for (double oy : offsets) {
                for (double oz : offsets) {
                    Block b = refWorld.getWorld().getBlockAt(
                            (int) Math.floor(x + ox),
                            (int) Math.floor(y + oy),
                            (int) Math.floor(z + oz)
                    );
                    if (b == null) continue;
                    Material m = b.getType();
                    if (m == null) continue;
                    if (m == Material.AIR) continue;
                    if (m == Material.WATER || m == Material.STATIONARY_WATER) continue;
                    if (m == Material.LAVA || m == Material.STATIONARY_LAVA) continue;
                    if (m.isSolid()) return true;
                }
            }
        }
        return false;
    }

    public static ReachContext analyzeReach(Location eye, Entity target, PlayerData targetData,
                                            long attackTime, long rewindMs) {
        if (eye == null || target == null) return null;

        double width = entityWidth(target);
        double currentHeight = entityHeight(target);
        Location current = target.getLocation();

        double currentDistance = distanceToHitbox(eye, current, width, currentHeight, VANILLA_HITBOX_EXPANSION);
        double bestDistance = currentDistance;
        long bestAge = 0L;
        double bestDisplacement = 0.0;
        double bestHeight = currentHeight;
        Location bestLocation = current.clone();

        if (targetData != null && rewindMs > 0L) {
            Deque<PlayerData.PositionSample> history = targetData.getPositionHistory();
            for (PlayerData.PositionSample sample : history) {
                if (sample == null || !sample.matchesWorld(target.getWorld())) continue;

                long age = attackTime - sample.getTime();
                // Allow a small future window to account for tick timing misalignment (1 tick = 50ms).
                // We allow up to 1 tick in the future to handle server tick boundary alignment.
                // Allowing more causes legitimate hits to be measured against future positions,
                // making reach appear shorter than it actually was.
                if (age < -50L || age > rewindMs) continue;

                Location historical = sample.toLocation(target.getWorld());
                if (historical == null) continue;

                double height = entityHeight(target, sample.isSneaking());
                double distance = distanceToHitbox(eye, historical, width, height, VANILLA_HITBOX_EXPANSION);
                if (distance + 1.0E-4 < bestDistance) {
                    bestDistance = distance;
                    bestAge = age;
                    bestDisplacement = distance(current, historical);
                    bestHeight = height;
                    bestLocation = historical;
                }
            }
        }

        return new ReachContext(currentDistance, bestDistance, bestAge, bestDisplacement, width, bestHeight, bestLocation);
    }

    /**
     * Ray-AABB intersection test. Casts a ray from the player's eye along their look direction
     * and checks if it intersects the target's expanded hitbox within maxDistance.
     *
     * @param eye        Player eye location (includes yaw/pitch for direction)
     * @param base       Target entity base location (feet)
     * @param width      Hitbox width
     * @param height     Hitbox height
     * @param expansion  Hitbox expansion (vanilla 1.8 uses 0.1 for combat)
     * @param maxDistance Maximum ray length to check
     * @return RayTraceResult with hit status and miss distance, or null if eye is inside hitbox
     */
    public static RayTraceResult rayTraceToHitbox(Location eye, Location base, double width, double height,
                                                   double expansion, double maxDistance) {
        if (eye == null || base == null || eye.getWorld() == null || base.getWorld() == null) {
            return new RayTraceResult(false, Double.MAX_VALUE, 0.0);
        }
        if (!eye.getWorld().equals(base.getWorld())) {
            return new RayTraceResult(false, Double.MAX_VALUE, 0.0);
        }

        CombatAabb box = buildCombatAabb(base, width, height, expansion);
        double minX = box.minX;
        double maxX = box.maxX;
        double minY = box.minY;
        double maxY = box.maxY;
        double minZ = box.minZ;
        double maxZ = box.maxZ;

        Vector origin = eye.toVector();
        Vector dir = eye.getDirection().normalize();

        // Check if origin is inside the AABB
        if (origin.getX() >= minX && origin.getX() <= maxX
                && origin.getY() >= minY && origin.getY() <= maxY
                && origin.getZ() >= minZ && origin.getZ() <= maxZ) {
            return null; // Inside hitbox, always a hit
        }

        // Slab method for ray-AABB intersection
        double tMin = 0.0;
        double tMax = maxDistance;

        // X axis
        if (Math.abs(dir.getX()) < 1.0E-8) {
            if (origin.getX() < minX || origin.getX() > maxX) {
                return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
            }
        } else {
            double t1 = (minX - origin.getX()) / dir.getX();
            double t2 = (maxX - origin.getX()) / dir.getX();
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
        }

        // Y axis
        if (Math.abs(dir.getY()) < 1.0E-8) {
            if (origin.getY() < minY || origin.getY() > maxY) {
                return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
            }
        } else {
            double t1 = (minY - origin.getY()) / dir.getY();
            double t2 = (maxY - origin.getY()) / dir.getY();
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
        }

        // Z axis
        if (Math.abs(dir.getZ()) < 1.0E-8) {
            if (origin.getZ() < minZ || origin.getZ() > maxZ) {
                return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
            }
        } else {
            double t1 = (minZ - origin.getZ()) / dir.getZ();
            double t2 = (maxZ - origin.getZ()) / dir.getZ();
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
        }

        // Hit: tMin is the entry point
        if (tMin >= 0.0 && tMin <= maxDistance) {
            return new RayTraceResult(true, 0.0, tMin);
        }

        return computeMiss(origin, dir, base, width, height, expansion, maxDistance);
    }

    private static RayTraceResult computeMiss(Vector origin, Vector dir, Location base,
                                              double width, double height, double expansion, double maxDistance) {
        CombatAabb box = buildCombatAabb(base, width, height, expansion);

        Vector hitboxCenter = new Vector(base.getX(), base.getY() + height / 2.0, base.getZ());
        Vector toCenter = hitboxCenter.clone().subtract(origin);
        double projLen = toCenter.dot(dir);
        Vector projPoint = origin.clone().add(dir.clone().multiply(clamp(projLen, 0.0, maxDistance)));
        Vector closestOnBox = box.closestPoint(projPoint);
        double missDistance = projPoint.distance(closestOnBox);

        return new RayTraceResult(false, missDistance, projLen);
    }

    public static final class RayTraceResult {
        private final boolean hit;
        private final double missDistance;
        private final double hitDistance;

        public RayTraceResult(boolean hit, double missDistance, double hitDistance) {
            this.hit = hit;
            this.missDistance = missDistance;
            this.hitDistance = hitDistance;
        }

        public boolean isHit() { return hit; }
        public double getMissDistance() { return missDistance; }
        public double getHitDistance() { return hitDistance; }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static final class ReachContext {
        private final double currentDistance;
        private final double compensatedDistance;
        private final long compensatedAgeMs;
        private final double compensatedDisplacement;
        private final double width;
        private final double height;
        private final Location compensatedLocation;

        public ReachContext(double currentDistance, double compensatedDistance, long compensatedAgeMs,
                            double compensatedDisplacement, double width, double height,
                            Location compensatedLocation) {
            this.currentDistance = currentDistance;
            this.compensatedDistance = compensatedDistance;
            this.compensatedAgeMs = compensatedAgeMs;
            this.compensatedDisplacement = compensatedDisplacement;
            this.width = width;
            this.height = height;
            this.compensatedLocation = compensatedLocation == null ? null : compensatedLocation.clone();
        }

        public double getCurrentDistance() { return currentDistance; }
        public double getCompensatedDistance() { return compensatedDistance; }
        public long getCompensatedAgeMs() { return compensatedAgeMs; }
        public double getCompensatedDisplacement() { return compensatedDisplacement; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public Location getCompensatedLocation() {
            return compensatedLocation == null ? null : compensatedLocation.clone();
        }

        public boolean isLegitExpansionMarginHit(Location eye) {
            return CombatUtil.isLegitExpansionMarginHit(eye, compensatedLocation, width, height);
        }
    }

    public static final class RayResult {
        public final boolean hit;
        public RayResult(boolean hit) { this.hit = hit; }
    }

    /**
     * Angle between packet look direction and expanded target hitbox (degrees).
     */
    public static double angleToEntity(Location eye, Entity entity, float yaw, float pitch) {
        if (eye == null || entity == null) return 0.0D;
        Location lookEye = eye.clone();
        lookEye.setYaw(yaw);
        lookEye.setPitch(pitch);
        return angularError(lookEye, entity.getLocation(), entityWidth(entity), entityHeight(entity));
    }

    public static RayResult raycastEntity(Location eye, float yaw, float pitch, Entity entity, double maxRange) {
        if (eye == null || entity == null) return new RayResult(false);
        Location lookEye = eye.clone();
        lookEye.setYaw(yaw);
        lookEye.setPitch(pitch);
        RayTraceResult trace = rayTraceToHitbox(
                lookEye,
                entity.getLocation(),
                entityWidth(entity),
                entityHeight(entity),
                VANILLA_HITBOX_EXPANSION,
                maxRange);
        return new RayResult(trace != null && trace.isHit());
    }

    private static Vector getLookVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vector(x, y, z).normalize();
    }
}
