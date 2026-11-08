package com.dex.boxphoban.reward;

import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Tags;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Ticket items from tickets.yml, recognised by their PDC id (renaming an item never makes a ticket). */
public final class TicketService {

    private record Def(Material material, String name, java.util.List<String> lore, int model) {
    }

    private final Map<String, Def> defs = new LinkedHashMap<>();

    public void load(YamlConfiguration y) {
        defs.clear();
        ConfigurationSection s = y.getConfigurationSection("tickets");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection t = s.getConfigurationSection(id);
            if (t == null) continue;
            defs.put(id, new Def(Items.material(t.getString("material"), Material.PAPER), t.getString("name", id),
                    t.getStringList("lore"), t.getInt("custom-model-data", 0)));
        }
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(defs.keySet());
    }

    public boolean exists(String id) {
        return defs.containsKey(id);
    }

    public String name(String id) {
        Def d = defs.get(id);
        return d == null ? id : d.name;
    }

    public ItemStack create(String id, int amount) {
        Def d = defs.get(id);
        if (d == null) return null;
        ItemStack it = Items.make(d.material, d.name, d.lore);
        it.setAmount(Math.max(1, Math.min(64, amount)));
        if (d.model > 0) {
            ItemMeta m = it.getItemMeta();
            m.setCustomModelData(d.model);
            it.setItemMeta(m);
        }
        return Tags.set(it, Tags.TICKET, id);
    }

    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (id.equals(Tags.get(it, Tags.TICKET))) n += it.getAmount();
        }
        return n;
    }

    /** Removes {@code amount} tickets, all or nothing. */
    public boolean take(Player p, String id, int amount) {
        if (count(p, id) < amount) return false;
        int left = amount;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!id.equals(Tags.get(it, Tags.TICKET))) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (take == it.getAmount()) contents[i] = null;
            else it.setAmount(it.getAmount() - take);
        }
        p.getInventory().setStorageContents(contents);
        return true;
    }
}
