package com.colin.vezanticheat.verdict;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.tier.TierCheck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Executes per-check ban when that check's VL reaches its punishVl threshold.
 */
public final class TierPunishmentExecutor {

    private final VezAntiCheat plugin;
    private final CheckVLStore vlStore;

    public TierPunishmentExecutor(VezAntiCheat plugin, CheckVLStore vlStore) {
        this.plugin = plugin;
        this.vlStore = vlStore;
    }

    public void evaluateBan(Player player, PlayerData data, TierCheck check, double checkVl, String debug) {
        if (player == null || check == null) return;
        if (check.shadowEnabled()) return;
        if (checkVl < check.punishVl()) return;
        if (!plugin.getConfig().getBoolean("punish.enabled", true)) return;

        String category = check.tier().name();
        plugin.punish().executeImmediate(player, category, null, null, null, check.publicName());
        vlStore.resetVl(player.getUniqueId(), check.vlPoolName());
        if (data != null) {
            data.addTotalVl(0);
        }
    }

    public void tickDecay(Player player, TierCheck check, long nowMs) {
        if (player == null || check == null) return;
        long grace = plugin.getConfig().getLong("tier.vl-decay-grace-ms", 15000L);
        double rate = check.decayPerSecond();
        if (rate <= 0.0D) return;
        // decay handled in TierCheck.fail path via scheduled decay; lightweight per-tick optional
    }
}
