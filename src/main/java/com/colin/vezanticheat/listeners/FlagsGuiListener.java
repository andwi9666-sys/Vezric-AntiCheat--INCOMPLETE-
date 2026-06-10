package com.colin.vezanticheat.listeners;

import com.colin.vezanticheat.staff.FlagsGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class FlagsGuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTopInventory() == null) return;
        if (!FlagsGui.isFlagsInventory(event.getView().getTopInventory().getTitle())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView() == null || event.getView().getTopInventory() == null) return;
        if (!FlagsGui.isFlagsInventory(event.getView().getTopInventory().getTitle())) return;
        if (event.getPlayer() instanceof org.bukkit.entity.Player) {
            FlagsGui.onClose((org.bukkit.entity.Player) event.getPlayer());
        }
    }
}
