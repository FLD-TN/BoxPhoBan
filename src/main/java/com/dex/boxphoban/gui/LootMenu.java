package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Shows the loot a chest rolled for the viewer. The items are already in the dungeon bag (given on a win). */
public final class LootMenu extends Menu {

    private final List<ItemStack> items;

    public LootMenu(BoxPhoBan plugin, Player viewer, List<ItemStack> items) {
        super(plugin, viewer);
        this.items = items;
    }

    @Override
    protected String title() {
        return gui().raw("loot.title");
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void draw() {
        filler();
        int[] slots = interior();
        for (int i = 0; i < items.size() && i < slots.length; i++) set(slots[i], items.get(i).clone(), null);
        set(4, item("loot.info", Material.BUNDLE), null);
        set(31, item("close", Material.BARRIER), (p, c) -> close());
    }
}
