package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import com.colin.vezanticheat.verdict.CheckVLStore;
import com.colin.vezanticheat.verdict.MitigationPolicy;
import com.colin.vezanticheat.verdict.TierPunishmentExecutor;
import com.colin.vezanticheat.staff.FlagsGui;
import com.colin.vezanticheat.staff.PolarFlagRecord;
import com.colin.vezanticheat.staff.PolarStaffAlertUtil;
import com.colin.vezanticheat.utils.PingUtil;
import com.colin.vezanticheat.utils.PrismCheckLabels;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Polar-tier check dispatcher. Replaces legacy CheckManager for the tier rewrite.
 * Dispatch order: Prism → Characteristics → Simulation → Prediction.
 */
public final class TierCheckManager {

    private final VezAntiCheat plugin;
    private final TierCheckRegistry registry;
    private final CheckVLStore vlStore;
    private final TierPunishmentExecutor punisher;
    private final MitigationPolicy mitigation;
    private final List<TierRunner> runners;

    public TierCheckManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        this.registry = new TierCheckRegistry(plugin);
        this.vlStore = new CheckVLStore();
        this.punisher = new TierPunishmentExecutor(plugin, vlStore);
        this.mitigation = new MitigationPolicy();
        this.runners = new ArrayList<TierRunner>();
        this.runners.add(new PrismRunner(plugin, registry.forTier(CheckTier.PRISM)));
        this.runners.add(new CharacteristicsRunner(plugin, registry.forTier(CheckTier.CHARACTERISTICS)));
        this.runners.add(new SimulationRunner(plugin, registry.forTier(CheckTier.SIMULATION)));
        this.runners.add(new PredictionRunner(plugin, registry.forTier(CheckTier.PREDICTION)));
    }

    public int count() { return registry.count(); }
    public TierCheckRegistry registry() { return registry; }
    public CheckVLStore vlStore() { return vlStore; }
    public TierPunishmentExecutor punisher() { return punisher; }
    public MitigationPolicy mitigation() { return mitigation; }

    private void forEachRunner(TierRunnerAction action) {
        for (TierRunner runner : runners) {
            action.run(runner);
        }
    }

    public void onMove(Player p, PlayerData data) { forEachRunner(r -> r.onMove(p, data)); }
    public void onAttack(Player p, PlayerData data) { forEachRunner(r -> r.onAttack(p, data)); }
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        forEachRunner(r -> r.onRotation(p, data, yaw, pitch));
    }
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        forEachRunner(r -> r.onFlyingPacket(p, data, nowMs));
    }
    public void onArmSwing(Player p, PlayerData data) { forEachRunner(r -> r.onArmSwing(p, data)); }
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, org.bukkit.entity.Entity target) {
        forEachRunner(r -> r.onInteractEntity(p, data, entityId, attack, target));
    }
    public void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {
        forEachRunner(r -> r.onDigging(p, data, action, block));
    }
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {
        forEachRunner(r -> r.onBlockPlacePacket(p, data, against, faceId, cursorX, cursorY, cursorZ));
    }
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {
        forEachRunner(r -> r.onWindowConfirmation(p, data, actionId, nowMs));
    }
    public void onInventoryAction(Player p, PlayerData data) { forEachRunner(r -> r.onInventoryAction(p, data)); }
    public void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block) {
        forEachRunner(r -> r.onBlockBreak(p, data, block));
    }
    public void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block) {
        forEachRunner(r -> r.onDigStart(p, data, block));
    }
    public void onEngineResult(Player p, PlayerData data, com.colin.vezanticheat.engine.EngineResult result, long nowMs) {
        forEachRunner(r -> r.onEngineResult(p, data, result, nowMs));
    }

    /** Legacy Bukkit hooks wired to tier runners. */
    public void onVelocity(Player p, PlayerData data) {}
    public void onBlockPlace(Player p, PlayerData data) {}
    public void onBowShoot(Player p, PlayerData data, long pullMs) {}
    public void onConsume(Player p, PlayerData data, long useMs) {}
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        forEachRunner(r -> r.onEntityAction(p, data, actionName));
    }
    public void onHeldItemChange(Player p, PlayerData data, int slot) {
        forEachRunner(r -> r.onHeldItemChange(p, data, slot));
    }
    public void onUseItem(Player p, PlayerData data) {
        forEachRunner(r -> r.onUseItem(p, data));
    }
    public void onCloseInventory(Player p, PlayerData data) {
        forEachRunner(r -> r.onCloseInventory(p, data));
    }
    public void onWindowClick(Player p, PlayerData data, int windowId, int slot) {
        forEachRunner(r -> r.onWindowClick(p, data, windowId, slot));
    }

    public void flagToStaff(String playerName, String checkName, int vl) {
        flagToStaff(null, checkName, null, vl, null);
    }

    /** Polar-style staff alert: check title + data tier (Prism) + metadata lines. */
    public void flagToStaff(Player flagged, String polarCheck, CheckTier tier, int vl, String debug) {
        String playerName = flagged != null ? flagged.getName() : "unknown";
        String dataTier = tier != null ? PrismCheckLabels.polarDataTier(tier) : "Unknown";
        String fmt = plugin.getConfig().getString("flags.format",
                "{prefix}&e{player} &7failed &c{check} &8[&7{tier}&8] &7(&fVL {vl}&7)");
        String prefix = plugin.getConfig().getString("prefix", "&6[WatchDog] ");
        String msg = ChatColor.translateAlternateColorCodes('&', fmt
                .replace("{prefix}", ChatColor.translateAlternateColorCodes('&', prefix))
                .replace("{player}", playerName)
                .replace("{check}", polarCheck == null ? "Unknown" : polarCheck)
                .replace("{tier}", dataTier)
                .replace("{vl}", String.valueOf(vl)));

        long now = System.currentTimeMillis();
        PlayerData flaggedData = flagged != null ? plugin.data().get(flagged) : null;
        int ping = flagged != null ? PingUtil.getPing(flagged) : 0;
        double tps = plugin.tps() != null ? plugin.tps().getTps() : 20.0D;
        String brand = flaggedData != null ? flaggedData.getClientBrand() : "Unknown";
        String client = flaggedData != null ? flaggedData.getClientVersion() : "Unknown";
        String serverName = plugin.getServer().getName();

        PolarFlagRecord record = new PolarFlagRecord(
                flagged != null ? flagged.getUniqueId() : null,
                playerName, polarCheck, tier, dataTier, vl, debug, now, ping, brand, client, tps, serverName);
        plugin.polarFlags().record(record);
        FlagsGui.refreshOpen(plugin);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("vez.staff")) continue;
            PlayerData d = plugin.data().get(staff);
            if (!d.isFlagsEnabled()) continue;
            staff.sendMessage(msg);
            if (d.isVerboseMode()) {
                boolean includeDebug = d.isDebugMode() && debug != null && !debug.isEmpty();
                for (String line : PolarStaffAlertUtil.verboseChatLines(record, now, includeDebug)) {
                    staff.sendMessage(line);
                }
            }
        }
    }

    public void verboseToStaff(String playerName, String checkName, String reason) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("vez.staff")) continue;
            PlayerData d = plugin.data().get(staff);
            if (!d.isVerboseMode()) continue;
            staff.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&8[&7V&8] &e" + playerName + " &8| &b" + checkName + " &8| &7" + reason));
        }
    }

    public void verboseFlagToStaff(Player flagged, String polarCheck, CheckTier tier, long checkVl, String debug) {
        // Metadata is included in {@link #flagToStaff} when staff verbose mode is on.
    }

    /** @deprecated use {@link #verboseFlagToStaff(Player, String, CheckTier, long, String)} */
    public void verboseFlagToStaff(String playerName, String checkName, long checkVl, String debug) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("vez.staff")) continue;
            PlayerData d = plugin.data().get(staff);
            if (!d.isVerboseMode()) continue;
            staff.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&8[&7V&8] &e" + playerName + " &8| &b" + checkName + " &8| &7&c[FLAG] &fVL=" + checkVl));
        }
    }

    @FunctionalInterface
    private interface TierRunnerAction {
        void run(TierRunner runner);
    }
}
