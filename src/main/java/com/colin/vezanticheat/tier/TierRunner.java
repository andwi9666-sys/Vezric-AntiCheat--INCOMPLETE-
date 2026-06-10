package com.colin.vezanticheat.tier;

import com.colin.vezanticheat.data.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Dispatches player events to checks within one tier.
 */
public interface TierRunner {

    CheckTier tier();

    void onMove(Player p, PlayerData data);

    void onAttack(Player p, PlayerData data);

    void onRotation(Player p, PlayerData data, float yaw, float pitch);

    void onFlyingPacket(Player p, PlayerData data, long nowMs);

    void onArmSwing(Player p, PlayerData data);

    void onInteractEntity(Player p, PlayerData data, int entityId, boolean attack, Entity target);

    void onDigging(Player p, PlayerData data, com.colin.vezanticheat.utils.BadPacketTracker.DiggingActionType action,
                   org.bukkit.block.Block block);

    void onBlockPlacePacket(Player p, PlayerData data, org.bukkit.block.Block against, int faceId,
                            float cursorX, float cursorY, float cursorZ);

    void onWindowConfirmation(Player p, PlayerData data, short actionId, long nowMs);

    void onInventoryAction(Player p, PlayerData data);

    void onBlockBreak(Player p, PlayerData data, org.bukkit.block.Block block);

    void onDigStart(Player p, PlayerData data, org.bukkit.block.Block block);

    void onEntityAction(Player p, PlayerData data, String actionName);

    void onHeldItemChange(Player p, PlayerData data, int slot);

    void onUseItem(Player p, PlayerData data);

    void onCloseInventory(Player p, PlayerData data);

    void onWindowClick(Player p, PlayerData data, int windowId, int slot);

    void onEngineResult(Player p, PlayerData data, com.colin.vezanticheat.engine.EngineResult result, long nowMs);
}
