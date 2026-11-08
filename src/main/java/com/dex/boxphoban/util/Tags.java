package com.dex.boxphoban.util;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** PDC keys used on items: dungeon keys, editor tools, tickets. */
public final class Tags {

    public static NamespacedKey KEY;
    public static NamespacedKey TOOL;
    public static NamespacedKey TICKET;

    private Tags() {
    }

    public static void init(Plugin plugin) {
        KEY = new NamespacedKey(plugin, "key");
        TOOL = new NamespacedKey(plugin, "tool");
        TICKET = new NamespacedKey(plugin, "ticket");
    }

    public static String get(ItemStack it, NamespacedKey k) {
        if (it == null || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        return m.getPersistentDataContainer().get(k, PersistentDataType.STRING);
    }

    public static ItemStack set(ItemStack it, NamespacedKey k, String v) {
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.getPersistentDataContainer().set(k, PersistentDataType.STRING, v);
            it.setItemMeta(m);
        }
        return it;
    }

    /** Removes every item carrying {@code k} from the player's inventory (and cursor). */
    public static void strip(Player p, NamespacedKey k) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (get(contents[i], k) != null) p.getInventory().setItem(i, null);
        }
        if (get(p.getItemOnCursor(), k) != null) p.setItemOnCursor(null);
    }
}
