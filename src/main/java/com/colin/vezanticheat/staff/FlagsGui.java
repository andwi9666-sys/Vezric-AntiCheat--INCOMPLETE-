package com.colin.vezanticheat.staff;

import com.colin.vezanticheat.VezAntiCheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Polar-style staff flag history GUI (54 slots, tooltip metadata on hover). */
public final class FlagsGui {

    public static final String TITLE = ChatColor.DARK_PURPLE + "Perplexion Flags";

    private static final Set<UUID> OPEN = new HashSet<UUID>();

    private FlagsGui() {}

    public static void open(VezAntiCheat plugin, Player staff) {
        if (plugin == null || staff == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fill(plugin, inv, false);
        staff.openInventory(inv);
        OPEN.add(staff.getUniqueId());
    }

    public static void refreshOpen(VezAntiCheat plugin) {
        if (plugin == null) return;
        long now = System.currentTimeMillis();
        for (UUID id : new HashSet<UUID>(OPEN)) {
            Player staff = Bukkit.getPlayer(id);
            if (staff == null || !staff.isOnline()) {
                OPEN.remove(id);
                continue;
            }
            if (staff.getOpenInventory() == null
                    || staff.getOpenInventory().getTopInventory() == null
                    || !TITLE.equals(staff.getOpenInventory().getTopInventory().getTitle())) {
                OPEN.remove(id);
                continue;
            }
            fill(plugin, staff.getOpenInventory().getTopInventory(), true);
        }
    }

    public static void onClose(Player staff) {
        if (staff != null) OPEN.remove(staff.getUniqueId());
    }

    public static boolean isFlagsInventory(String title) {
        return TITLE.equals(title);
    }

    private static void fill(VezAntiCheat plugin, Inventory inv, boolean refresh) {
        List<PolarFlagRecord> records = plugin.polarFlags().recent();
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < records.size()) {
                inv.setItem(i, toItem(records.get(i), System.currentTimeMillis()));
            } else if (!refresh || inv.getItem(i) != null) {
                inv.setItem(i, filler());
            }
        }
    }

    private static ItemStack toItem(PolarFlagRecord record, long nowMs) {
        ItemStack stack = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwner(record.playerName);
        meta.setDisplayName(ChatColor.RED + ChatColor.BOLD.toString() + record.polarCheck
                + ChatColor.GRAY + " - " + ChatColor.YELLOW + record.playerName);
        meta.setLore(PolarStaffAlertUtil.tooltipLore(record, nowMs, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 10);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
