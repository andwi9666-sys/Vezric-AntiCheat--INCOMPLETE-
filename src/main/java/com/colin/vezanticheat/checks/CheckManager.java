package com.colin.vezanticheat.checks;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * CheckManager — Legacy flat check registry (retired in v16 Polar tier rewrite).
 *
 * @deprecated Use {@link com.colin.vezanticheat.tier.TierCheckManager} instead.
 *             This class remains for reference; it is no longer instantiated at runtime.
 */
@Deprecated
public class CheckManager {

    private static final long EXCEPTION_LOG_INTERVAL_MS = 30_000L;

    private final VezAntiCheat plugin;
    private final List<Check> checks = new ArrayList<Check>();
    private final ConcurrentHashMap<String, Long> lastCheckExceptionLogMs = new ConcurrentHashMap<>();

    public CheckManager(VezAntiCheat plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        // Retired in v16 — tier checks register via TierCheckRegistry.
    }

    private void add(Check c) { checks.add(c); }

    public int count() { return checks.size(); }

    @FunctionalInterface
    private interface SafeCheckAction {
        void run(Check check);
    }

    private void dispatch(String event, SafeCheckAction action) {
        for (Check c : checks) {
            try {
                action.run(c);
            } catch (Throwable t) {
                logCheckThrowable(c.name(), event, t);
            }
        }
    }

    private void logCheckThrowable(String checkName, String event, Throwable t) {
        String key = checkName + ":" + event;
        long now = System.currentTimeMillis();
        Long last = lastCheckExceptionLogMs.get(key);
        if (last != null && now - last < EXCEPTION_LOG_INTERVAL_MS) {
            return;
        }
        lastCheckExceptionLogMs.put(key, now);
        plugin.getLogger().log(Level.WARNING,
                "Check " + checkName + " failed during " + event + ": "
                        + t.getClass().getSimpleName() + " - " + t.getMessage()
                        + " (further errors throttled for 30s)",
                t);
    }

    public void onMove(Player p, PlayerData data) { dispatch("onMove", c -> c.onMove(p, data)); }
    public void onAttack(Player p, PlayerData data) { dispatch("onAttack", c -> c.onAttack(p, data)); }
    public void onVelocity(Player p, PlayerData data) { dispatch("onVelocity", c -> c.onVelocity(p, data)); }
    public void onArmSwing(Player p, PlayerData data) { dispatch("onArmSwing", c -> c.onArmSwing(p, data)); }
    public void onBlockPlace(Player p, PlayerData data) { dispatch("onBlockPlace", c -> c.onBlockPlace(p, data)); }
    public void onBowShoot(Player p, PlayerData data, long pullMs) { dispatch("onBowShoot", c -> c.onBowShoot(p, data, pullMs)); }
    public void onConsume(Player p, PlayerData data, long useMs) { dispatch("onConsume", c -> c.onConsume(p, data, useMs)); }
    public void onInventoryAction(Player p, PlayerData data) { dispatch("onInventoryAction", c -> c.onInventoryAction(p, data)); }
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) { dispatch("onRotation", c -> c.onRotation(p, data, yaw, pitch)); }
    public void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block) { dispatch("onBlockBreak", c -> c.onBlockBreak(p, data, block)); }
    public void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block) {
        dispatch("onDigStart", c -> c.onDigStart(p, data, block));
    }
    public void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {
        dispatch("onDigging", c -> c.onDigging(p, data, action, block));
    }
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {
        dispatch("onBlockPlacePacket", c -> c.onBlockPlacePacket(p, data, against, faceId, cursorX, cursorY, cursorZ));
    }
    public void onHeldItemChange(Player p, PlayerData data, int slot) {
        dispatch("onHeldItemChange", c -> c.onHeldItemChange(p, data, slot));
    }
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        dispatch("onEntityAction", c -> c.onEntityAction(p, data, actionName));
    }
    public void onUseItem(Player p, PlayerData data) {
        dispatch("onUseItem", c -> c.onUseItem(p, data));
    }
    public void onWindowClick(Player p, PlayerData data, int windowId, int slot) {
        dispatch("onWindowClick", c -> c.onWindowClick(p, data, windowId, slot));
    }
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {
        dispatch("onWindowConfirmation", c -> c.onWindowConfirmation(p, data, actionId, nowMs));
    }
    public void onCloseInventory(Player p, PlayerData data) {
        dispatch("onCloseInventory", c -> c.onCloseInventory(p, data));
    }
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, org.bukkit.entity.Entity target) {
        dispatch("onInteractEntity", c -> c.onInteractEntity(p, data, entityId, attack, target));
    }
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) { dispatch("onFlyingPacket", c -> c.onFlyingPacket(p, data, nowMs)); }

    public void flagToStaff(String playerName, String checkName, int vl) {
        String fmt = plugin.cfg().flagFormat();
        String msg = fmt
                .replace("{prefix}", plugin.cfg().prefix())
                .replace("{player}", playerName)
                .replace("{check}", checkName)
                .replace("{vl}", String.valueOf(vl));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("vez.staff")) continue;
            PlayerData d = plugin.data().get(staff);
            if (!d.isFlagsEnabled()) continue;
            staff.sendMessage(msg);
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
}
