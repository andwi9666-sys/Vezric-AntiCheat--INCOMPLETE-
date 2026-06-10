package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.engine.CombatRewind;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.utils.HitboxUtil;
import com.colin.vezanticheat.utils.PingUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Ray-AABB intersection validation for individual impossible hits (Prism tier). */
public final class PrismHitboxA extends TierCheck {

    private static final ConcurrentHashMap<UUID, int[]> STATE = new ConcurrentHashMap<UUID, int[]>();

    public PrismHitboxA(VezAntiCheat plugin) {
        super(plugin, "PrismHitboxA", CheckTier.PRISM);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null || lagGated(p, data)) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return;

        Entity target = CombatUtil.resolveTarget(p, data.getLastTargetUuid());
        if (target == null) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null) return;

        if (combat.getSampleWeight() < plugin.tierCfg().checkDouble(name(), "minSampleWeight", 0.40D)) {
            decay(p, 0.30D);
            return;
        }

        int ping = Math.max(0, PingUtil.getPing(p));
        long rewindMs = CombatUtil.compensationWindowMs(
                ping,
                plugin.tierCfg().checkLong(name(), "rewindBaseMs", 80L),
                plugin.tierCfg().checkDouble(name(), "rewindPingFactor", 0.35D),
                plugin.tierCfg().checkLong(name(), "maxRewindMs", 200L)
        );

        Location eye = data.getLastAttackEyeLocation();
        if (eye == null || eye.getWorld() == null) {
            eye = HitboxUtil.buildPacketSyncedEye(p, data);
        }

        PlayerData targetData = target instanceof Player ? plugin.data().get((Player) target) : null;
        CombatUtil.ReachContext ctx = resolveReachContext(data, eye, target, targetData, data.getLastUseEntityTime(), rewindMs);
        if (ctx == null) return;

        double minDistance = plugin.tierCfg().checkDouble(name(), "minDistance", 1.0D);
        if (ctx.getCompensatedDistance() < minDistance) {
            decay(p, 0.25D);
            return;
        }

        Location compensated = ctx.getCompensatedLocation();
        double width = ctx.getWidth();
        double height = ctx.getHeight();

        double vanillaExpansion = plugin.tierCfg().checkDouble(name(), "vanillaExpansion",
                CombatUtil.vanillaExpansion(plugin));
        double pingExpansion = Math.min(
                plugin.tierCfg().checkDouble(name(), "maxPingExpansion", 0.15D),
                ping * plugin.tierCfg().checkDouble(name(), "pingExpansionFactor", 0.0008D)
        );

        double moveExpansion = 0.0D;
        if (p.isSprinting()) moveExpansion += plugin.tierCfg().checkDouble(name(), "sprintExpansion", 0.03D);
        if (combat.isCombo()) moveExpansion += plugin.tierCfg().checkDouble(name(), "comboExpansion", 0.05D);

        double kbExpansion = HitboxUtil.knockbackExpansion(plugin, data, targetData, combat, now, name());
        double totalExpansion = vanillaExpansion + pingExpansion + moveExpansion + kbExpansion;

        List<Location> lookCandidates = HitboxUtil.attackLookCandidates(plugin, data, eye, now, name());
        double maxRayDistance = plugin.tierCfg().checkDouble(name(), "maxRayDistance", 6.0D);
        CombatUtil.RayTraceResult result = HitboxUtil.bestRayTraceToHitbox(
                lookCandidates, compensated, width, height, totalExpansion, maxRayDistance);

        if (result == null || result.isHit()) {
            decay(p, 0.35D);
            return;
        }

        double missDistance = result.getMissDistance();
        double flickGrace = HitboxUtil.flickMissGrace(plugin, data, now, ctx.getCompensatedDistance(), name());
        missDistance = Math.max(0.0D, missDistance - flickGrace);

        double graceThreshold = plugin.tierCfg().checkDouble(name(), "graceThreshold", 0.08D);
        if (missDistance <= graceThreshold) {
            decay(p, 0.30D);
            return;
        }

        UUID playerId = p.getUniqueId();
        int[] state = STATE.computeIfAbsent(playerId, k -> new int[2]);
        int buf = state[0];
        long lastBadSec = state[1];
        long nowSec = now / 1000L;

        long bufferResetSec = plugin.tierCfg().checkLong(name(), "bufferResetMs", 3000L) / 1000L;
        if (lastBadSec > 0 && (nowSec - lastBadSec) > bufferResetSec) {
            buf = 0;
        }

        double blatantMiss = plugin.tierCfg().checkDouble(name(), "blatantMissDistance", 0.4D);
        int gain;
        if (missDistance > blatantMiss) {
            gain = 3;
        } else if (missDistance > plugin.tierCfg().checkDouble(name(), "suspiciousMissDistance", 0.15D)) {
            gain = 2;
        } else {
            gain = 1;
        }

        buf += gain;
        state[0] = buf;
        state[1] = (int) nowSec;

        int bufferToFlag = plugin.tierCfg().checkInt(name(), "bufferToFlag", 4);
        if (buf < bufferToFlag) {
            verbose(p, "buf=" + buf + "/" + bufferToFlag + " miss=" + round3(missDistance) + " gain=" + gain);
        }
        if (buf >= bufferToFlag) {
            if (missDistance > blatantMiss && buf >= 5) {
                blockAttack(p, data, "hitbox-miss dist=" + round3(missDistance) + " buf=" + buf);
            }
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0D),
                    "ray-miss=" + round3(missDistance) + " blocks"
                            + " expansion=" + round3(totalExpansion)
                            + " dist=" + round3(ctx.getCompensatedDistance())
                            + " buf=" + buf
                            + " gain=" + gain
                            + " ping=" + ping
                            + " " + combat.debugSummary());
            state[0] = 0;
        }
    }

    public static void clearState(UUID player) {
        STATE.remove(player);
    }

    private CombatUtil.ReachContext resolveReachContext(
            PlayerData data, Location eye, Entity target, PlayerData targetData,
            long attackTime, long rewindMs) {
        if (plugin.getConfig().getBoolean("combat-engine.enabled", true)) {
            com.colin.vezanticheat.engine.CombatResult engine = data == null ? null : data.getLastCombatResult();
            if (engine != null && engine.tracked) {
                CombatUtil.ReachContext ctx = CombatRewind.toReachContext(engine);
                if (ctx != null) return ctx;
            }
        }
        return CombatUtil.analyzeReach(eye, target, targetData, attackTime, rewindMs);
    }
}
