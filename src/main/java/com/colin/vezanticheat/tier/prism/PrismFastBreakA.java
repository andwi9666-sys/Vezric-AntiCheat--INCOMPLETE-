package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.utils.BreakSpeedUtil;
import com.colin.vezanticheat.utils.CombatUtil;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Single-block fast break: dig duration vs expected hardness/tool/potion.
 */
public final class PrismFastBreakA extends TierCheck {

    public PrismFastBreakA(VezAntiCheat plugin) {
        super(plugin, "PrismFastBreakA", CheckTier.PRISM);
    }

    @Override
    public void onBlockBreak(Player p, PlayerData data, Block block) {
        if (p == null || data == null || block == null || lagGated(p, data)) return;
        if (data.isTeleportExempt() || data.isVelocityExempt()) return;

        long now = System.currentTimeMillis();
        long digStart = data.getLastDigStartMs();
        if (digStart <= 0L) return;

        long elapsed = now - digStart;
        long expected = BreakSpeedUtil.expectedBreakMs(p, block);
        if (expected < 0L) return;

        double tolerance = plugin.tierCfg().checkDouble(name(), "toleranceRatio", 0.92D);
        long allowance = plugin.tierCfg().checkLong(name(), "absoluteAllowanceMs", 55L);
        long minExpected = plugin.tierCfg().checkLong(name(), "minExpectedMs", 100L);
        long threshold = Math.max(minExpected, (long) (expected * tolerance) - allowance);

        if (expected > 0L && elapsed < threshold) {
            int vb = data.getFastBreakAVerbose() + 1;
            data.setFastBreakAVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 2)) {
                blockDig(data, "fast_break");
                failWithMitigation(p, data, 1.0D,
                        "elapsed=" + elapsed + " expected>=" + threshold + " block=" + block.getType(),
                        PrismMitigationPolicy.Confidence.HIGH);
                data.setFastBreakAVerbose(0);
            }
        } else {
            data.setFastBreakAVerbose(Math.max(0, data.getFastBreakAVerbose() - 1));
            decay(p, 0.5D);
        }
    }

    @Override
    public void onDigStart(Player p, PlayerData data, Block block) {
        if (p == null || data == null) return;
        data.recordDigStartSample(System.currentTimeMillis());
    }
}
