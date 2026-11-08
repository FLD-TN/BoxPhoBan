package com.dex.boxphoban.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Base64;
import java.util.List;

/** Small item helpers. */
public final class Items {

    private Items() {
    }

    public static ItemStack make(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat == null ? Material.STONE : mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(Text.component(name));
            if (lore != null && !lore.isEmpty()) meta.lore(Text.components(lore));
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack it = make(Material.PLAYER_HEAD, name, lore);
        if (it.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(owner);
            it.setItemMeta(sm);
        }
        return it;
    }

    public static Material material(String s, Material def) {
        if (s == null || s.isBlank()) return def;
        Material m = Material.matchMaterial(s.trim());
        if (m == null) return def;
        try {
            return m.isItem() ? m : def;
        } catch (Throwable registryNotReady) {
            return m; // outside a running server (unit tests)
        }
    }

    public static String encode(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    public static ItemStack decode(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
        } catch (Exception e) {
            return null;
        }
    }

    /** "x3 Kiếm Kim Cương" style label: custom name if present, otherwise the material in title case. */
    public static String label(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        String name;
        if (meta != null && meta.hasDisplayName()) {
            name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                    .serialize(meta.displayName());
        } else {
            String raw = it.getType().name().toLowerCase().replace('_', ' ');
            StringBuilder sb = new StringBuilder();
            for (String w : raw.split(" ")) {
                if (w.isEmpty()) continue;
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
            name = "&f" + sb.toString().trim();
        }
        return "&fx" + it.getAmount() + " " + name;
    }
}
