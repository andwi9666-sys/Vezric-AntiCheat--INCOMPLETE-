package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Hit-select / attacking immediately after taking damage. */
public final class CharCombatTiming extends TierCheck {

    public CharCombatTiming(VezAntiCheat plugin) {
        super(plugin, "CharCombatTiming", CheckTier.CHARACTERISTICS);
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        if (p == null || data == null) return;
        if (lagGated(p, data)) return;

        long now = System.currentTimeMillis();
        if (!data.wasLastUseEntityAttack()) return;
        if (now - data.getLastUseEntityTime() > plugin.tierCfg().checkLong(name(), "attackFreshnessMs", 150L)) return;
        if (data.isVelocityExempt() || data.isTeleportExempt()) return;

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean()) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        if (combat.isTrade()) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        long lastDamage = data.getLastDamageTakenMs();
        if (lastDamage <= 0L) {
            decay(p, 0.30);
            return;
        }

        EntityDamageEvent.DamageCause cause = data.getLastDamageCause();
        if (cause != null && !isCombatDamage(cause)) {
            decay(p, 0.30);
            return;
        }

        long delta = now - lastDamage;
        long maxWindow = plugin.tierCfg().checkLong(name(), "maxHitSelectWindowMs", 120L);
        long minWindow = plugin.tierCfg().checkLong(name(), "minHitSelectWindowMs", 0L);
        long staleMs = plugin.tierCfg().checkLong(name(), "staleDamageMs", 500L);

        if (delta > staleMs) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        if (delta < minWindow || delta > maxWindow) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        int gain = 1;
        if (delta <= maxWindow / 2) gain = 2;
        if (combat.isCombo()) gain = Math.max(1, gain - 1);

        if (incrementBuffer(p, gain)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.0),
                    "hitSelect delta=" + delta + "ms cause=" + (cause == null ? "unknown" : cause.name())
                            + " " + combat.debugSummary());
            resetBuffer(p);
        } else {
            verbose(p, "hitSelect delta=" + delta + "ms");
        }
    }

    private boolean isCombatDamage(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case ENTITY_ATTACK:
            case ENTITY_EXPLOSION:
            case PROJECTILE:
            case MAGIC:
            case THORNS:
                return true;
            default:
                return false;
        }
    }
}
