package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CriticalsUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * CriticalsB -- Micro-Jump Critical Hit Pattern Detection
 *
 * <p><b>What it detects:</b> Criticals hacks that perform tiny jumps (micro-jumps)
 * just enough to satisfy the airborne+falling requirements for critical hits. The
 * client sends a small upward movement (0.001-0.0625 blocks) immediately followed by
 * a small downward movement, creating a "bounce" pattern invisible to players but
 * sufficient for the server to grant critical damage.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Track Y-deltas (vertical position changes) on every move packet via onMove().</li>
 *   <li>On attack, scan the recent Y-delta history (last 5 ticks) for an up-then-down
 *       pattern: one delta in [microUpMin, microUpMax] followed by one in
 *       [microDownMin, microDownMax].</li>
 *   <li>Verify the pattern is NOT a legitimate jump (no delta &gt;= 0.10 in window)
 *       and NOT legitimate airborne (fallDistance &gt; 0.08 with client airborne).</li>
 *   <li>Buffer and flag at bufferToFlag (default 5).</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code recentYDeltas} -- deque of last 10 Y-axis movement deltas.</li>
 *   <li>{@code lastTrackY / hasLastTrackY} -- previous Y for delta computation.</li>
 *   <li>{@code criticalsBVerbose} -- buffer counter.</li>
 *   <li>{@code wasLastClientGround()} -- client ground state.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Verbose counter increments on micro-jump
 * detection. Flags at 5, resets to 0. Decays by 1 on clean attacks.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Velocity/teleport exempt.</li>
 *   <li>Invalid crit environment (water, ladder, etc.).</li>
 *   <li>Low ceiling / near half-blocks.</li>
 *   <li>Legitimate airborne (fallDistance &gt; 0.08).</li>
 *   <li>Strong jump detected in window (delta &gt;= 0.10 = real jump).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> The micro-jump range [0.001, 0.0625] is too
 * small for legitimate movement; strong-jump exclusion prevents flagging normal
 * jump-crits; fallDistance check excludes players already in the air.</p>
 *
 * <p><b>Connections:</b> Complementary to CriticalsA (ground-spoof) and CriticalsC
 * (crit-rate). Different hack implementations trigger different checks.</p>
 */
public final class CharCriticalsB extends TierCheck {

    public CharCriticalsB(VezAntiCheat plugin) {
        super(plugin, "CharCriticalsB", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onMove(Player p, PlayerData data) {
        if (p == null || data == null) return;

        double currentY = p.getLocation().getY();
        if (data.hasLastTrackY()) {
            double dy = currentY - data.getLastTrackY();
            data.getRecentYDeltas().addLast(dy);
            while (data.getRecentYDeltas().size() > 10) data.getRecentYDeltas().removeFirst();
        }
        data.setLastTrackY(currentY);
        data.setHasLastTrackY(true);
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
        if (CriticalsUtil.shouldExemptCritHeuristic(plugin, data, now)) {
            data.setCriticalsBVerbose(Math.max(0, data.getCriticalsBVerbose() - 1));
            decay(p, 0.5);
            return;
        }

        Location loc = p.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        if (CriticalsUtil.hasLowCeiling(loc) || CriticalsUtil.isNearHalfBlock(loc)) return;

        Deque<Double> yDeltas = data.getRecentYDeltas();
        if (yDeltas.size() < plugin.tierCfg().checkInt(name(), "minSamples", 4)) return;

        Double[] deltas = yDeltas.toArray(new Double[0]);
        int len = deltas.length;

        double upMin = plugin.tierCfg().checkDouble(name(), "microUpMin", 0.001);
        double upMax = plugin.tierCfg().checkDouble(name(), "microUpMax", 0.0625);
        double downMin = plugin.tierCfg().checkDouble(name(), "microDownMin", -0.0625);
        double downMax = plugin.tierCfg().checkDouble(name(), "microDownMax", -0.001);
        double legitJumpMin = plugin.tierCfg().checkDouble(name(), "legitJumpMin", 0.10);

        boolean foundMicroJump = false;
        int patternWindow = plugin.tierCfg().checkInt(name(), "patternWindow", 5);
        for (int i = Math.max(0, len - patternWindow); i < len - 1; i++) {
            double d1 = deltas[i].doubleValue();
            double d2 = deltas[i + 1].doubleValue();

            boolean up = d1 >= upMin && d1 <= upMax;
            boolean down = d2 >= downMin && d2 <= downMax;
            if (up && down) {
                foundMicroJump = true;
                break;
            }
        }

        if (!foundMicroJump) {
            data.setCriticalsBVerbose(Math.max(0, data.getCriticalsBVerbose() - 1));
            decay(p, 0.5);
            return;
        }

        double last = deltas[len - 1].doubleValue();
        boolean legitAir = !data.wasLastClientGround()
                && p.getFallDistance() > plugin.tierCfg().checkDouble(name(), "legitAirFallDistance", 0.08D);
        boolean strongJump = false;
        for (Double delta : deltas) {
            if (delta != null && delta.doubleValue() >= legitJumpMin) {
                strongJump = true;
                break;
            }
        }

        if (!legitAir && !strongJump) {
            int vb = data.getCriticalsBVerbose() + 1;
            data.setCriticalsBVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 5)) {
                fail(p, data, 1.2,
                        "micro-jump lastDy=" + r(last)
                                + " fall=" + r(p.getFallDistance())
                                + " clientGround=" + data.wasLastClientGround());
                data.setCriticalsBVerbose(0);
            }
        } else {
            data.setCriticalsBVerbose(Math.max(0, data.getCriticalsBVerbose() - 1));
            decay(p, 0.5);
        }
    }

    private double r(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
