package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.data.Database;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Reward items that did not fit in the inventory. Click one to take it, or take everything that fits. */
public final class PendingMenu extends Menu {

    public PendingMenu(BoxPhoBan plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return gui().raw("pending.title");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        filler();
        List<Database.Pending> list = plugin.db().pending(viewer.getUniqueId());
        int[] slots = interior();
        if (list.isEmpty()) set(22, item("pending.empty", Material.BARRIER), null);
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            Database.Pending pe = list.get(i);
            ItemStack it;
            try {
                it = ItemStack.deserializeBytes(pe.item());
            } catch (Exception e) {
                continue;
            }
            ItemStack shown = it.clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.addAll(Text.components(gui().list("pending.item-lore")));
                meta.lore(lore);
                shown.setItemMeta(meta);
            }
            set(slots[i], shown, (p, c) -> {
                sound();
                claim(p, pe, it);
                redraw();
            });
        }
        set(48, item("back", Material.ARROW), (p, c) -> {
            sound();
            new DungeonListMenu(plugin, p).open();
        });
        set(50, item("pending.claim-all", Material.HOPPER, "count", String.valueOf(list.size())), (p, c) -> {
            sound();
            int got = 0;
            for (Database.Pending pe : plugin.db().pending(p.getUniqueId())) {
                ItemStack it;
                try {
                    it = ItemStack.deserializeBytes(pe.item());
                } catch (Exception e) {
                    continue;
                }
                if (!fits(p, it)) break;
                if (claim(p, pe, it)) got++;
            }
            plugin.messages().send(p, "rewards.claimed", "count", String.valueOf(got));
            redraw();
        });
    }

    private static boolean fits(Player p, ItemStack it) {
        int left = it.getAmount();
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) return true;
            if (s.isSimilar(it)) left -= s.getMaxStackSize() - s.getAmount();
            if (left <= 0) return true;
        }
        return false;
    }

    private boolean claim(Player p, Database.Pending pe, ItemStack it) {
        if (!fits(p, it)) {
            plugin.messages().send(p, "rewards.inventory-full");
            return false;
        }
        if (!plugin.db().removePending(pe.id())) return false;
        p.getInventory().addItem(it).values().forEach(l -> plugin.db().addPending(p.getUniqueId(), l.serializeAsBytes()));
        return true;
    }
}
