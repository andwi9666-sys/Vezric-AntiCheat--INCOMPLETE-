package com.colin.vezanticheat.tier.prism;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.CheckTier;
import com.colin.vezanticheat.tier.TierCheck;
import com.colin.vezanticheat.verdict.PrismMitigationPolicy;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Deque;

/**
 * Sustained fast break pattern (Nuker): too many blocks broken too quickly.
 */
public final class PrismFastBreakB extends TierCheck {

    public PrismFastBreakB(VezAntiCheat plugin) {
        super(plugin, "PrismFastBreakB", CheckTier.PRISM);
    }

    @Override
    public void onBlockBreak(Player p, PlayerData data, Block block) {
        if (p == null || data == null || lagGated(p, data)) return;

        Deque<Long> intervals = data.getBreakIntervals();
        int minSamples = plugin.tierCfg().checkInt(name(), "minSamples", 5);
        if (intervals == null || intervals.size() < minSamples) {
            decay(p, 0.4D);
            return;
        }

        double avg = 0.0D;
        for (Long ms : intervals) avg += ms == null ? 0.0D : ms;
        avg /= intervals.size();

        double instantAvg = plugin.tierCfg().checkDouble(name(), "instantBlockMinAvgMs", 80.0D);
        double minRatio = plugin.tierCfg().checkDouble(name(), "minAvgRatio", 0.84D);

        long expectedSample = plugin.tierCfg().checkLong(name(), "expectedSampleMs", 450L);
        boolean suspicious = avg <= instantAvg || avg <= expectedSample * minRatio;

        if (suspicious) {
            int vb = data.getFastBreakBVerbose() + 1;
            data.setFastBreakBVerbose(vb);
            if (vb >= plugin.tierCfg().checkInt(name(), "bufferToFlag", 2)) {
                blockDig(data, "nuker_pattern");
                failWithMitigation(p, data, 1.0D, "avgBreakMs=" + round3(avg),
                        PrismMitigationPolicy.Confidence.MODERATE);
                data.setFastBreakBVerbose(0);
            }
        } else {
            data.setFastBreakBVerbose(Math.max(0, data.getFastBreakBVerbose() - 1));
            decay(p, 0.45D);
        }
    }
}
