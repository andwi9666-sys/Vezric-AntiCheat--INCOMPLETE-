package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.VezAntiCheat;
import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

abstract class AbstractTierRunner implements TierRunner {

    private static final long EXCEPTION_LOG_INTERVAL_MS = 30_000L;

    protected final VezAntiCheat plugin;
    protected final CheckTier tier;
    protected final List<TierCheck> checks;

    AbstractTierRunner(VezAntiCheat plugin, CheckTier tier, List<TierCheck> checks) {
        this.plugin = plugin;
        this.tier = tier;
        this.checks = Collections.unmodifiableList(new ArrayList<TierCheck>(checks));
    }

    @Override
    public CheckTier tier() { return tier; }

    protected void dispatch(String event, TierAction action) {
        for (TierCheck check : checks) {
            // A disabled check must not RUN at all — not just skip its flag. Otherwise a "disabled" check
            // still executes and can take direct side-effects (e.g. CharSilentAim.blockAttack()).
            if (!check.enabled()) continue;
            try {
                action.run(check);
            } catch (Throwable t) {
                logThrowable(check.name(), event, t);
            }
        }
    }

    private void logThrowable(String checkName, String event, Throwable t) {
        plugin.getLogger().log(Level.WARNING,
                "Tier check " + checkName + " failed during " + event + ": "
                        + t.getClass().getSimpleName() + " - " + t.getMessage(),
                t);
    }

    @Override
    public void onMove(Player p, PlayerData data) {
        dispatch("onMove", c -> c.onMove(p, data));
    }

    @Override
    public void onAttack(Player p, PlayerData data) {
        dispatch("onAttack", c -> c.onAttack(p, data));
    }

    @Override
    public void onRotation(Player p, PlayerData data, float yaw, float pitch) {
        dispatch("onRotation", c -> c.onRotation(p, data, yaw, pitch));
    }

    @Override
    public void onFlyingPacket(Player p, PlayerData data, long nowMs) {
        dispatch("onFlyingPacket", c -> c.onFlyingPacket(p, data, nowMs));
    }

    @Override
    public void onArmSwing(Player p, PlayerData data) {
        dispatch("onArmSwing", c -> c.onArmSwing(p, data));
    }

    @Override
    public void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target) {
        dispatch("onInteractEntity", c -> c.onInteractEntity(p, data, entityId, attack, target));
    }

    @Override
    public void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                          org.bukkit.block.Block block) {
        dispatch("onDigging", c -> c.onDigging(p, data, action, block));
    }

    @Override
    public void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                                   float cursorX, float cursorY, float cursorZ) {
        dispatch("onBlockPlacePacket", c -> c.onBlockPlacePacket(p, data, against, faceId, cursorX, cursorY, cursorZ));
    }

    @Override
    public void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs) {
        dispatch("onWindowConfirmation", c -> c.onWindowConfirmation(p, data, actionId, nowMs));
    }

    @Override
    public void onInventoryAction(Player p, PlayerData data) {
        dispatch("onInventoryAction", c -> c.onInventoryAction(p, data));
    }

    @Override
    public void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block) {
        dispatch("onBlockBreak", c -> c.onBlockBreak(p, data, block));
    }

    @Override
    public void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block) {
        dispatch("onDigStart", c -> c.onDigStart(p, data, block));
    }

    @Override
    public void onEntityAction(Player p, PlayerData data, String actionName) {
        dispatch("onEntityAction", c -> c.onEntityAction(p, data, actionName));
    }

    @Override
    public void onHeldItemChange(Player p, PlayerData data, int slot) {
        dispatch("onHeldItemChange", c -> c.onHeldItemChange(p, data, slot));
    }

    @Override
    public void onUseItem(Player p, PlayerData data) {
        dispatch("onUseItem", c -> c.onUseItem(p, data));
    }

    @Override
    public void onCloseInventory(Player p, PlayerData data) {
        dispatch("onCloseInventory", c -> c.onCloseInventory(p, data));
    }

    @Override
    public void onWindowClick(Player p, PlayerData data, int windowId, int slot) {
        dispatch("onWindowClick", c -> c.onWindowClick(p, data, windowId, slot));
    }

    @Override
    public void onEngineResult(Player p, PlayerData data, com.colin.vezanticheat.engine.EngineResult result, long nowMs) {
        dispatch("onEngineResult", c -> c.onEngineResult(p, data, result, nowMs));
    }

    @FunctionalInterface
    interface TierAction {
        void run(TierCheck check);
    }
}
