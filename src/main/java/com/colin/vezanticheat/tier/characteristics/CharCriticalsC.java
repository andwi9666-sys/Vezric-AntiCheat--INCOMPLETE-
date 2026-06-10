package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CriticalsUtil;
import org.bukkit.entity.Player;

/**
 * CriticalsC -- Abnormal Critical Hit Rate Detection
 *
 * <p><b>What it detects:</b> Any criticals hack variant by detecting a statistically
 * impossible critical hit rate over a sample window. Regardless of HOW the client
 * achieves crits (micro-jump, ground-spoof, packet manipulation), if a player
 * lands crits on 88%+ of attacks over 24+ hits, it is virtually impossible without
 * cheats in normal PvP.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Track total attacks and critical hits over a 45-second rolling window.</li>
 *   <li>A hit is counted as "critical" if: client is airborne, fallDistance &gt;= 0.09,
 *       no low ceiling, no half-blocks.</li>
 *   <li>After accumulating minAttacks (default 24), compute critRate = crits / total.</li>
 *   <li>Flag if critRate &gt;= maxCritRate (default 0.88) AND crits &gt;= minCrits (default 14).</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code criticalHits / totalAttacks} -- counters for the current window.</li>
 *   <li>{@code lastCritResetTime} -- window start timestamp.</li>
 *   <li>{@code criticalsCVerbose} -- buffer counter.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Verbose counter increments when rate exceeds
 * threshold. Flags at bufferToFlag (default 4). Window resets after 45 seconds
 * or on flag. VL weight 1.3 (high confidence statistical check).</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Velocity/teleport exempt.</li>
 *   <li>Invalid crit environment (water, ladder, etc.).</li>
 *   <li>Insufficient sample size (&lt; 24 attacks).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> 24+ attack minimum prevents premature flags;
 * 88% threshold is well above what skilled players achieve (~30-50% in normal PvP);
 * 45-second window auto-resets to handle intermittent legitimate jump-crits;
 * buffer requirement prevents single-window anomalies from flagging.</p>
 *
 * <p><b>Connections:</b> Catches all criticals hack variants regardless of method.
 * Complementary to CriticalsA/B which detect specific implementation patterns.</p>
 */
public final class CharCriticalsC extends TierCheck {

    public CharCriticalsC(VezAntiCheat plugin) {
        super(plugin, "CharCriticalsC", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return;
        if (CriticalsUtil.isInvalidCritEnvironment(p)) return;

        if (now - data.getLastCritResetTime() > plugin.tierCfg().checkLong(name(), "windowMs", 45000L)) {
            data.setCriticalHits(0);
            data.setTotalAttacks(0);
            data.setLastCritResetTime(now);
        }

        data.setTotalAttacks(data.getTotalAttacks() + 1);

        boolean legitCritState = !data.wasLastClientGround()
                && p.getFallDistance() >= plugin.tierCfg().checkDouble(name(), "minCritFallDistance", 0.09)
                && !CriticalsUtil.hasLowCeiling(p.getLocation())
                && !CriticalsUtil.isNearHalfBlock(p.getLocation());
        if (legitCritState) {
            data.setCriticalHits(data.getCriticalHits() + 1);
        }

        int total = data.getTotalAttacks();
        int crits = data.getCriticalHits();
        int minSample = plugin.tierCfg().checkInt(name(), "minAttacks", 24);
        if (total < minSample) {
            decay(p, 0.4);
            return;
        }

        double critRate = total <= 0 ? 0.0 : (double) crits / total;
        double maxRate = plugin.tierCfg().checkDouble(name(), "maxCritRate", 0.88);
        int minCrits = plugin.tierCfg().checkInt(name(), "minCrits", 14);

        if (crits >= minCrits && critRate >= maxRate) {
            int vb = data.getCriticalsCVerbose() + 1;
            data.setCriticalsCVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 4)) {
                fail(p, data, 1.3,
                        "critRate=" + r(critRate * 100.0)
                                + "% (" + crits + "/" + total + ")");
                data.setCriticalsCVerbose(0);
                data.setCriticalHits(0);
                data.setTotalAttacks(0);
                data.setLastCritResetTime(now);
            }
        } else {
            data.setCriticalsCVerbose(Math.max(0, data.getCriticalsCVerbose() - 1));
            decay(p, 0.5);
        }
    }

    private double r(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
