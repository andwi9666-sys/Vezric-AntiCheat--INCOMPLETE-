package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.CombatContextAnalyzer;
import com.colin.vezanticheat.utils.PrismCheckLabels;
import com.colin.vezanticheat.utils.PrismPatternSupport;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.entity.Player;

/**
 * Polar triangle/fake-random CPS pattern detector (8–14 CPS band occupancy).
 */
public final class PrismAutoClickD extends TierCheck {

    public PrismAutoClickD(VezAntiCheat plugin) {
        super(plugin, "PrismAutoClickD", CheckTier.PRISM);
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        if (p == null || data == null || PlayerData.bypass(p)) return;

        long now = System.currentTimeMillis();
        if (!isCombatSwing(now, p, data)) {
            decay(p, 0.8D);
            return;
        }

        CombatContextAnalyzer.CombatContext combat = CombatContextAnalyzer.analyze(plugin, p, data, name());
        if (combat == null || !combat.isClean() || combat.isTrade()) {
            decay(p, 0.8D);
            return;
        }

        PrismPatternSupport.ClickPatternScore score =
                PrismPatternSupport.analyzeClickPattern(plugin, name(), data.getArmSwings(),
                        plugin.tierCfg().checkInt(name(), "windowSwings", 36));

        double minCps = plugin.tierCfg().checkDouble(name(), "minTrackedCps", 8.0D);
        double maxCps = plugin.tierCfg().checkDouble(name(), "maxTrackedCps", 15.0D);
        if (score.samples < plugin.tierCfg().checkInt(name(), "minSamples", 18)
                || score.meanCps < minCps || score.meanCps > maxCps) {
            decay(p, 0.7D);
            return;
        }

        if (score.triangleLike && incrementBuffer(p, 1)) {
            failWithMitigation(p, data, 1.1D,
                    PrismCheckLabels.autoClickerReason(score.debug),
                    PrismMitigationPolicy.fromPattern(score.confidence));
            resetBuffer(p);
        } else if (!score.triangleLike) {
            coolBuffer(p, 1);
            decay(p, 0.75D);
        }
    }

    private boolean isCombatSwing(long now, Player p, PlayerData data) {
        return data.wasLastUseEntityAttack()
                && (now - data.getLastUseEntityTime() <= plugin.tierCfg().checkLong(name(), "combatSwingWindowMs", 180L));
    }
}
