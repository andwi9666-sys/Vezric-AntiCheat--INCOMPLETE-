package com.colin.vezanticheat.tier.characteristics;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import org.bukkit.entity.Player;

/** Rotation changes correlated with attack ticks only (KillAuraA signal 6). */
public final class CharAimCorrelation extends TierCheck {

    public CharAimCorrelation(VezAntiCheat plugin) {
        super(plugin, "CharAimCorrelation", CheckTier.CHARACTERISTICS);
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

        if (combat.getSampleWeight() < plugin.tierCfg().checkDouble(name(), "minSampleWeight", 0.25)) {
            decay(p, 0.30);
            return;
        }

        if (combat.isTrade() || combat.isWalkThrough()) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        if (CombatContextAnalyzer.shouldExemptAimHeuristics(plugin, combat, data, p, now)) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        double signal = computeCorrelationSignal(data, now);
        double threshold = plugin.tierCfg().checkDouble(name(), "threshold", 0.55);
        if (signal < threshold) {
            coolBuffer(p, 1);
            decay(p, 0.35);
            return;
        }

        int rotOnAttack = data.getKillAuraARotOnAttackTicks();
        int rotOnNonAttack = data.getKillAuraARotOnNonAttackTicks();
        int noRotNonAttack = data.getKillAuraANoRotOnNonAttackTicks();

        int gain = signal >= plugin.tierCfg().checkDouble(name(), "blatantThreshold", 0.85) ? 2 : 1;
        if (incrementBuffer(p, gain)) {
            fail(p, data, plugin.tierCfg().checkDouble(name(), "failVl", 1.15),
                    "correlation=" + round3(signal)
                            + " attackRot=" + rotOnAttack
                            + " nonAttackRot=" + rotOnNonAttack
                            + " idle=" + noRotNonAttack
                            + " " + combat.debugSummary());
            resetBuffer(p);
        } else {
            verbose(p, "correlation=" + round3(signal) + " attackRot=" + rotOnAttack);
        }
    }

    private double computeCorrelationSignal(PlayerData data, long now) {
        int rotOnAttack = data.getKillAuraARotOnAttackTicks();
        int rotOnNonAttack = data.getKillAuraARotOnNonAttackTicks();
        int noRotNonAttack = data.getKillAuraANoRotOnNonAttackTicks();
        int totalTicks = rotOnAttack + rotOnNonAttack + noRotNonAttack;
        long windowStart = data.getKillAuraACorrelationWindowStart();

        if (totalTicks < plugin.tierCfg().checkInt(name(), "minTotalTicks", 40) || rotOnAttack < 3) return 0.0;
        if (windowStart > 0 && (now - windowStart) < plugin.tierCfg().checkLong(name(), "minWindowMs", 2000L)) return 0.0;

        int totalNonAttack = rotOnNonAttack + noRotNonAttack;
        if (totalNonAttack > 0 && (double) noRotNonAttack / totalNonAttack > 0.70) return 0.0;

        int totalWithRotation = rotOnAttack + rotOnNonAttack;
        if (totalWithRotation == 0) return 0.0;

        double exclusivity = (double) rotOnAttack / totalWithRotation;
        if (exclusivity < plugin.tierCfg().checkDouble(name(), "minExclusivity", 0.70)) return 0.0;

        return clamp((exclusivity - 0.70) / 0.20, 0.0, 1.0);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
