package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.utils.CriticalsUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CriticalsA -- Fake Airborne / Ground-Spoof Critical Hit Detection
 *
 * <p><b>What it detects:</b> Criticals hacks that spoof the client's onGround flag
 * to false while remaining physically on the ground. In vanilla Minecraft, critical
 * hits require the player to be falling (airborne + fallDistance &gt; 0). Cheat clients
 * send onGround=false in the position packet without actually jumping, tricking the
 * server into granting 1.5x damage.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>On each attack, verify the player IS on solid ground (server-side check).</li>
 *   <li>If the client claims to be airborne (onGround=false) BUT:
 *       dy (vertical movement since last tick) is near-zero (default &lt;= 0.015),
 *       fallDistance is near-zero (default &lt;= 0.06), no low ceiling, and no half-blocks
 *       nearby -- this is a "fake air" packet.</li>
 *   <li>Buffer fake-air detections. Flag at bufferToFlag (default 5).</li>
 * </ol>
 *
 * <p><b>PlayerData fields used:</b></p>
 * <ul>
 *   <li>{@code wasLastClientGround()} -- the onGround flag from the client's last packet.</li>
 *   <li>{@code lastMoveFrom} -- previous position for dy computation.</li>
 * </ul>
 *
 * <p><b>Buffer/threshold system:</b> Static ConcurrentHashMap buffer per UUID.
 * Increments by 1 on fake-air detection, flags at 5, resets to 0 on flag.
 * Decays by 1 when not on solid ground or when not faking.</p>
 *
 * <p><b>Exemptions:</b></p>
 * <ul>
 *   <li>Velocity/teleport exempt (KB sends player upward legitimately).</li>
 *   <li>Invalid crit environment (in water, on ladder, etc.).</li>
 *   <li>Not on solid ground server-side (player is legitimately airborne).</li>
 *   <li>Low ceiling or near half-blocks (hitbox edge cases).</li>
 * </ul>
 *
 * <p><b>False positive protections:</b> Requires server-confirmed solid ground;
 * half-block and low-ceiling exclusions prevent slab/stair edge cases; dy and
 * fallDistance thresholds allow micro-movement from walking on uneven terrain.</p>
 *
 * <p><b>Connections:</b> Independent from CriticalsB (micro-jump) and CriticalsC
 * (crit-rate). All three detect different criticals hack variants.</p>
 */
public final class CharCriticalsA extends TierCheck {

    private static final ConcurrentHashMap<UUID, Integer> BUFFER = new ConcurrentHashMap<UUID, Integer>();

    public CharCriticalsA(VezAntiCheat plugin) {
        super(plugin, "CharCriticalsA", CheckTier.CHARACTERISTICS);
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
            decayBuf(p.getUniqueId());
            decay(p, 0.5);
            return;
        }

        Location loc = p.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        boolean serverGround = CriticalsUtil.isSolidGround(loc);
        if (!serverGround) {
            decayBuf(p.getUniqueId());
            decay(p, 0.5);
            return;
        }

        double dy = 0.0;
        Location from = data.getLastMoveFrom();
        if (from != null && from.getWorld() != null && from.getWorld().equals(loc.getWorld())) {
            dy = loc.getY() - from.getY();
        }

        float fall = p.getFallDistance();
        boolean fakeAir = !data.wasLastClientGround()
                && dy <= plugin.tierCfg().checkDouble(name(), "maxGroundDy", 0.015)
                && fall <= plugin.tierCfg().checkDouble(name(), "maxGroundFallDistance", 0.06)
                && !CriticalsUtil.hasLowCeiling(loc)
                && !CriticalsUtil.isNearHalfBlock(loc);

        if (fakeAir) {
            UUID id = p.getUniqueId();
            int buf = getI(BUFFER, id) + 1;
            BUFFER.put(id, buf);

            if (buf >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 5)) {
                fail(p, data, 1.15,
                        "fake-air dy=" + r(dy)
                                + " fall=" + r(fall)
                                + " onGround=false serverGround=true");
                BUFFER.put(id, 0);
            }
            return;
        }

        decayBuf(p.getUniqueId());
        decay(p, 0.5);
    }

    private void decayBuf(UUID u) {
        Integer b = BUFFER.get(u);
        if (b != null && b.intValue() > 0) {
            BUFFER.put(u, Math.max(0, b.intValue() - 1));
        }
    }

    private int getI(ConcurrentHashMap<UUID, Integer> m, UUID u) {
        Integer v = m.get(u);
        return v == null ? 0 : v.intValue();
    }

    private double r(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
