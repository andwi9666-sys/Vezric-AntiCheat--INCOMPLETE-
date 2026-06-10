package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

public final class SpeedUtil {
    private SpeedUtil() {}

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        Location from = data.getLastMoveFrom();
        Location to = data.getLastLoc();
        if (from == null || to == null) return null;
        if (from.getWorld() == null || to.getWorld() == null) return null;
        if (!from.getWorld().equals(to.getWorld())) return null;

        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distH = Math.hypot(dx, dz);
        long now = System.currentTimeMillis();

        long intervalMs = data.getLastFlyingIntervalMs();
        double ticks = Math.max(1.0, Math.min(4.0, intervalMs <= 0L ? 1.0 : intervalMs / 50.0));

        boolean serverGround = isServerGround(to) || isServerGround(from);
        boolean clientGround = data.wasLastClientGround();
        boolean onGround = serverGround || (clientGround && Math.abs(dy) <= 0.08);
        boolean inLiquid = isInLiquid(to) || isInLiquid(from);
        boolean onIce = isOnIce(to) || isOnIce(from);
        boolean onSlime = isOnSlime(to) || isOnSlime(from);
        boolean weirdSurface = isWeirdSurface(to) || isWeirdSurface(from);
        boolean recentJump = p.isSprinting()
                && now - data.getLastJumpTime() <= plugin.getConfig().getLong("movement-analysis.jump-window-ms", 220L);
        boolean kbEnvelopeActive = KbSpeedAllowance.isKbEnvelopeActive(plugin, p, data, now);
        boolean recentVelocity = kbEnvelopeActive || data.isVelocityExempt()
                || now - data.getLastVelocityTime() <= plugin.getConfig().getLong("movement-analysis.velocity-window-ms", 500L);
        boolean potionExempt = data.isPotionExempt();
        boolean recentExplosion = isRecentExplosion(plugin, data, now);

        double baseGroundCap = p.isSprinting() ? 0.30 : 0.23;
        double baseAirCap = 0.33;
        double groundCap = baseGroundCap;
        double airCap = baseAirCap;

        int speedAmp = PotionUtil.effectiveSpeedLevel(p);
        if (speedAmp > 0) {
            double mult = 1.0 + (0.22 * speedAmp);
            groundCap *= mult;
            airCap *= mult;
            // Real packeted movement with Speed effects runs hotter than a strict
            // scalar cap due to sprint-jump impulse stacking with the higher base
            // velocity and friction compounding.  Speed II especially needs headroom.
            groundCap += 0.035 * speedAmp;
            airCap += 0.03 * speedAmp;
        }

        int slowAmp = PotionUtil.slownessLevel(p);
        if (slowAmp > 0) {
            double mult = Math.max(0.45, 1.0 - (0.15 * slowAmp));
            groundCap *= mult;
            airCap *= mult;
        }

        int jumpAmp = PotionUtil.jumpBoostLevel(p);
        if (jumpAmp > 0) {
            airCap += 0.025 * jumpAmp;
        }

        if (onIce) {
            groundCap += 0.16;
            airCap += 0.16;
        }
        if (onSlime) {
            groundCap += 0.28;
            airCap += 0.28;
        }
        if (weirdSurface) {
            groundCap += 0.05;
            airCap += 0.05;
        }
        if (inLiquid) {
            groundCap += 0.12;
            airCap += 0.12;
        }
        if (!onGround && p.isSprinting()) {
            airCap += 0.028 + (speedAmp > 0 ? 0.012 * speedAmp : 0.0);
        }

        // Legit 1.8 sprint-jump movement carries more horizontal momentum across
        // takeoff and the next landing packet than a flat ground/air cap suggests.
        if (recentJump) {
            groundCap += 0.105 + (speedAmp * 0.020);
            airCap += 0.055 + (speedAmp * 0.015);
        }

        if (potionExempt) {
            groundCap += 0.05;
            airCap += 0.04;
        }

        if (recentVelocity) {
            double velocityBonus = KbSpeedAllowance.horizontalAllowance(plugin, p, data, now);
            if (velocityBonus <= 0.0D) {
                Vector velocity = data.getLastVelocity();
                double velocityHorizontal = velocity == null ? 0.0D : Math.hypot(velocity.getX(), velocity.getZ());
                velocityBonus = Math.min(0.22D, 0.06D + (velocityHorizontal * 0.35D));
            }
            groundCap += velocityBonus;
            airCap += velocityBonus * 0.85D;
        }

        if (recentExplosion) {
            groundCap += 0.10D;
            airCap += 0.08D;
        }

        double pingAllowance = Math.min(0.12, Math.max(0, PingUtil.getPing(p)) * 0.00035);
        double packetAllowance = Math.min(0.12, (ticks - 1.0) * 0.05);

        double expectedGround = (groundCap * ticks) + pingAllowance + packetAllowance;
        double expectedAir = (airCap * ticks) + pingAllowance + packetAllowance;
        double expected = onGround ? expectedGround : expectedAir;

        return new Context(from, to, distH, dy, ticks, onGround, inLiquid, onIce, onSlime, weirdSurface,
                recentJump, recentVelocity, recentExplosion, potionExempt,
                speedAmp, slowAmp, jumpAmp, baseGroundCap, baseAirCap, expectedGround, expectedAir, expected);
    }

    public static boolean isRecentExplosion(VezAntiCheat plugin, PlayerData data, long nowMs) {
        if (plugin == null || data == null) return false;
        EntityDamageEvent.DamageCause cause = data.getLastDamageCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            return false;
        }
        long windowMs = plugin.getConfig().getLong("movement-analysis.explosion-window-ms", 900L);
        return nowMs - data.getLastDamageTime() <= Math.max(0L, windowMs);
    }

    private static boolean isServerGround(Location loc) {
        Block below = loc.clone().subtract(0, 0.1, 0).getBlock();
        if (below == null) return false;
        Material t = below.getType();
        return t.isSolid() || t.name().contains("FENCE") || t.name().contains("WALL");
    }

    private static boolean isOnIce(Location loc) {
        Material t = loc.clone().subtract(0, 1, 0).getBlock().getType();
        return t == Material.ICE || t == Material.PACKED_ICE;
    }

    private static boolean isOnSlime(Location loc) {
        return loc.clone().subtract(0, 1, 0).getBlock().getType() == Material.SLIME_BLOCK;
    }

    private static boolean isWeirdSurface(Location loc) {
        Material t = loc.clone().subtract(0, 1, 0).getBlock().getType();
        String name = t.name();
        return name.contains("STEP") || name.contains("SLAB") || name.contains("STAIRS") || t == Material.SOUL_SAND;
    }

    private static boolean isInLiquid(Location loc) {
        Material t = loc.getBlock().getType();
        return t == Material.WATER || t == Material.STATIONARY_WATER
                || t == Material.LAVA || t == Material.STATIONARY_LAVA;
    }

    public static final class Context {
        public final Location from;
        public final Location to;
        public final double distH;
        public final double dy;
        public final double ticks;
        public final boolean onGround;
        public final boolean inLiquid;
        public final boolean onIce;
        public final boolean onSlime;
        public final boolean weirdSurface;
        public final boolean recentJump;
        public final boolean recentVelocity;
        public final boolean recentExplosion;
        public final boolean potionExempt;
        public final int speedAmp;
        public final int slowAmp;
        public final int jumpAmp;
        public final double baseGroundCap;
        public final double baseAirCap;
        public final double expectedGround;
        public final double expectedAir;
        public final double expected;

        public Context(Location from, Location to, double distH, double dy, double ticks, boolean onGround,
                       boolean inLiquid, boolean onIce, boolean onSlime, boolean weirdSurface,
                       boolean recentJump, boolean recentVelocity, boolean recentExplosion, boolean potionExempt,
                       int speedAmp, int slowAmp, int jumpAmp, double baseGroundCap, double baseAirCap,
                       double expectedGround, double expectedAir, double expected) {
            this.from = from;
            this.to = to;
            this.distH = distH;
            this.dy = dy;
            this.ticks = ticks;
            this.onGround = onGround;
            this.inLiquid = inLiquid;
            this.onIce = onIce;
            this.onSlime = onSlime;
            this.weirdSurface = weirdSurface;
            this.recentJump = recentJump;
            this.recentVelocity = recentVelocity;
            this.recentExplosion = recentExplosion;
            this.potionExempt = potionExempt;
            this.speedAmp = speedAmp;
            this.slowAmp = slowAmp;
            this.jumpAmp = jumpAmp;
            this.baseGroundCap = baseGroundCap;
            this.baseAirCap = baseAirCap;
            this.expectedGround = expectedGround;
            this.expectedAir = expectedAir;
            this.expected = expected;
        }
    }
}
