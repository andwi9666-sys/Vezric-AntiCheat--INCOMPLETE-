package com.colin.vezanticheat.utils;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class MovementContextAnalyzer {
    private MovementContextAnalyzer() {}

    public static boolean isLikelyLegitSprintJump(VezAntiCheat plugin, Player p, PlayerData data) {
        return isLikelyLegitSprintJump(plugin, p, data, 0.0D);
    }

    public static boolean isLikelyLegitSprintJump(VezAntiCheat plugin, Player p, PlayerData data, double minHorizontalCap) {
        if (plugin == null || p == null || data == null) return false;
        SpeedUtil.Context speed = SpeedUtil.analyze(plugin, p, data);
        if (speed == null) return false;

        long now = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("movement-analysis.sprint-jump-grace-window-ms",
                plugin.getConfig().getLong("movement-analysis.jump-arc-window-ms", 950L));
        if (now - data.getLastJumpTime() > Math.max(0L, windowMs)) return false;
        if (speed.inLiquid || speed.onSlime || speed.weirdSurface) return false;

        double minDy = plugin.getConfig().getDouble("movement-analysis.sprint-jump-min-dy", -1.05D);
        double riseSlack = plugin.getConfig().getDouble("movement-analysis.sprint-jump-rise-slack", 0.08D);
        double baseHorizontal = plugin.getConfig().getDouble("movement-analysis.sprint-jump-base-horizontal", 0.12D);
        double horizontalPerTick = plugin.getConfig().getDouble("movement-analysis.sprint-jump-horizontal-per-tick", 0.42D);
        double potionHorizontal = plugin.getConfig().getDouble("movement-analysis.sprint-jump-speed-potion-horizontal", 0.05D);

        double maxHorizontal = Math.max(minHorizontalCap,
                Math.max(1.05D, baseHorizontal + (horizontalPerTick * speed.ticks)));
        if (speed.speedAmp > 0) {
            maxHorizontal += speed.speedAmp * potionHorizontal;
            maxHorizontal = Math.max(maxHorizontal, 1.12D + (speed.speedAmp * 0.14D));
        }
        if (speed.jumpAmp > 0) {
            maxHorizontal += speed.jumpAmp * 0.015D;
        }
        if (data.isEatMovementGrace() || data.getLastEatStart() > 0L) {
            maxHorizontal += 0.24D;
            minDy -= 0.08D;
        }

        double maxDy = 0.42D + (speed.jumpAmp * 0.10D) + Math.min(0.06D, Math.max(0.0D, speed.ticks - 1.0D) * 0.05D) + riseSlack;
        boolean sprintLike = p.isSprinting() || speed.distH >= 0.16D;
        if (!sprintLike) return false;

        return speed.dy >= minDy
                && speed.dy <= maxDy
                && speed.distH <= maxHorizontal;
    }

    public static Context analyze(VezAntiCheat plugin, Player p, PlayerData data) {
        if (plugin == null || p == null || data == null) return null;

        long now = System.currentTimeMillis();
        SpeedUtil.Context speed = SpeedUtil.analyze(plugin, p, data);
        double jitter = std(data.getFlyingIntervals());
        boolean packetBurst = data.getLastFlyingIntervalMs() >= plugin.getConfig().getLong("movement-analysis.packet-burst-ms", 110L)
                || jitter >= plugin.getConfig().getDouble("movement-analysis.high-jitter-ms", 12.0D);
        boolean recentJump = now - data.getLastJumpTime() <= plugin.getConfig().getLong("movement-analysis.jump-window-ms", 220L);
        boolean recentVelocity = data.isVelocityExempt() || now - data.getLastVelocityTime() <= plugin.getConfig().getLong("movement-analysis.velocity-window-ms", 500L);
        boolean recentPotion = data.isPotionExempt();
        boolean recentExplosion = speed != null && speed.recentExplosion;
        boolean recentCombat = now - data.getLastUseEntityTime() <= plugin.getConfig().getLong("movement-analysis.combat-window-ms", 400L)
                || now - data.getLastDamageTime() <= plugin.getConfig().getLong("movement-analysis.combat-window-ms", 400L);
        boolean recentBlockPlace = now - data.getLastBlockPlace() <= plugin.getConfig().getLong("movement-analysis.block-place-window-ms", 250L);
        boolean constrained = isConstrained(p);

        double cleanliness = 1.0D;
        List<String> notes = new ArrayList<String>();
        if (data.isTeleportExempt()) {
            cleanliness -= 0.45D;
            notes.add("teleport");
        }
        if (recentVelocity) {
            cleanliness -= 0.25D;
            notes.add("velocity");
        }
        if (recentExplosion) {
            cleanliness -= 0.22D;
            notes.add("explosion");
        }
        if (recentPotion) {
            cleanliness -= 0.18D;
            notes.add("potion");
        }
        if (packetBurst) {
            cleanliness -= 0.18D;
            notes.add("packet-burst");
        }
        if (recentJump) {
            cleanliness -= 0.12D;
            notes.add("jump");
        }
        if (recentCombat) {
            cleanliness -= 0.12D;
            notes.add("combat");
        }
        if (recentBlockPlace) {
            cleanliness -= 0.08D;
            notes.add("recent-place");
        }
        if (speed != null && (speed.inLiquid || speed.onIce || speed.onSlime || speed.weirdSurface)) {
            cleanliness -= 0.18D;
            notes.add("surface");
        }
        if (constrained) {
            cleanliness -= 0.14D;
            notes.add("constrained");
        }
        cleanliness = clamp(cleanliness, 0.0D, 1.0D);
        return new Context(now, speed, jitter, packetBurst, recentJump, recentVelocity, recentPotion, recentExplosion,
                recentCombat, recentBlockPlace, constrained, cleanliness, notes);
    }

    private static boolean isConstrained(Player p) {
        if (p == null || p.getLocation() == null || p.getWorld() == null) return false;
        return solid(p.getLocation().clone().add(0.0, 2.0, 0.0).getBlock())
                || solid(p.getLocation().clone().add(0.31, 0.0, 0.0).getBlock())
                || solid(p.getLocation().clone().add(-0.31, 0.0, 0.0).getBlock())
                || solid(p.getLocation().clone().add(0.0, 0.0, 0.31).getBlock())
                || solid(p.getLocation().clone().add(0.0, 0.0, -0.31).getBlock());
    }

    private static boolean solid(Block block) {
        return block != null && block.getType() != Material.AIR && block.getType().isSolid();
    }

    private static double std(Deque<Long> values) {
        if (values == null || values.size() < 3) return 0.0D;
        double mean = 0.0D;
        int count = 0;
        for (Long value : values) {
            if (value == null) continue;
            mean += value.longValue();
            count++;
        }
        if (count == 0) return 0.0D;
        mean /= count;
        double variance = 0.0D;
        for (Long value : values) {
            if (value == null) continue;
            double delta = value.longValue() - mean;
            variance += delta * delta;
        }
        return Math.sqrt(variance / count);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static final class Context {
        public final long now;
        public final SpeedUtil.Context speed;
        public final double jitterMs;
        public final boolean packetBurst;
        public final boolean recentJump;
        public final boolean recentVelocity;
        public final boolean recentPotion;
        public final boolean recentExplosion;
        public final boolean recentCombat;
        public final boolean recentBlockPlace;
        public final boolean constrained;
        public final double cleanliness;
        public final List<String> notes;

        public Context(long now, SpeedUtil.Context speed, double jitterMs, boolean packetBurst, boolean recentJump,
                       boolean recentVelocity, boolean recentPotion, boolean recentExplosion,
                       boolean recentCombat, boolean recentBlockPlace,
                       boolean constrained, double cleanliness, List<String> notes) {
            this.now = now;
            this.speed = speed;
            this.jitterMs = jitterMs;
            this.packetBurst = packetBurst;
            this.recentJump = recentJump;
            this.recentVelocity = recentVelocity;
            this.recentPotion = recentPotion;
            this.recentExplosion = recentExplosion;
            this.recentCombat = recentCombat;
            this.recentBlockPlace = recentBlockPlace;
            this.constrained = constrained;
            this.cleanliness = cleanliness;
            this.notes = notes == null ? new ArrayList<String>() : notes;
        }

        public boolean isClean() {
            return cleanliness >= 0.68D && !packetBurst && !recentVelocity && !recentPotion && !recentExplosion;
        }

        public boolean isSpeedUsable() {
            return cleanliness >= 0.42D && !recentVelocity && !recentPotion && !recentExplosion && !packetBurst;
        }

        public boolean isFlightUsable() {
            return cleanliness >= 0.34D && !recentVelocity && !packetBurst;
        }

        public double speedWeight() {
            double weight = cleanliness;
            if (recentJump) weight += 0.08D;
            if (recentCombat) weight += 0.06D;
            if (constrained) weight -= 0.08D;
            if (recentBlockPlace) weight -= 0.04D;
            return Math.max(0.0D, Math.min(1.0D, weight));
        }

        public double flightWeight() {
            double weight = cleanliness;
            if (recentJump) weight += 0.12D;
            if (recentCombat) weight += 0.04D;
            if (constrained) weight -= 0.10D;
            return Math.max(0.0D, Math.min(1.0D, weight));
        }

        public String debugSummary() {
            return "clean=" + round(cleanliness)
                    + " speedW=" + round(speedWeight())
                    + " flightW=" + round(flightWeight())
                    + " jitter=" + round(jitterMs)
                    + " burst=" + packetBurst
                    + " jump=" + recentJump
                    + " vel=" + recentVelocity
                    + " potion=" + recentPotion
                    + " expl=" + recentExplosion
                    + " combat=" + recentCombat
                    + " place=" + recentBlockPlace
                    + " constr=" + constrained
                    + (notes.isEmpty() ? "" : " notes=" + notes.toString());
        }

        private double round(double value) {
            return Math.round(value * 100.0D) / 100.0D;
        }
    }
}
