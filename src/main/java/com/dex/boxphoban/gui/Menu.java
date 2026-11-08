package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Base chest menu: every click is cancelled, slots may carry a handler. */
public abstract class Menu implements InventoryHolder {

    @FunctionalInterface
    public interface Click {
        void on(Player p, ClickType type);
    }

    protected final BoxPhoBan plugin;
    protected final Player viewer;
    private Inventory inv;
    private final Map<Integer, Click> handlers = new HashMap<>();

    protected Menu(BoxPhoBan plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void draw();

    protected Messages gui() {
        return plugin.gui();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void open() {
        inv = Bukkit.createInventory(this, rows() * 9, Text.component(title()));
        handlers.clear();
        draw();
        viewer.openInventory(inv);
    }

    /** Re-draws in place (same window). */
    public void redraw() {
        if (inv == null) return;
        inv.clear();
        handlers.clear();
        draw();
    }

    public void click(int slot, ClickType type) {
        Click c = handlers.get(slot);
        if (c != null) c.on(viewer, type);
    }

    protected void set(int slot, ItemStack item, Click click) {
        if (slot < 0 || slot >= inv.getSize()) return;
        inv.setItem(slot, item);
        if (click != null) handlers.put(slot, click);
        else handlers.remove(slot);
    }

    /** Item from gui.yml: {@code <path>.material|name|lore}. Lore lines equal to a multi-line value are expanded. */
    protected ItemStack item(String path, Material def, String... kv) {
        Material m = Items.material(gui().raw(path + ".material"), def);
        return item(path, m, true, kv);
    }

    protected ItemStack item(String path, Material material, boolean fixed, String... kv) {
        String name = Text.replace(gui().raw(path + ".name"), kv);
        List<String> lore = Text.replaceLines(gui().list(path + ".lore"), kv);
        return Items.make(material, name, lore);
    }

    protected void filler() {
        String mat = gui().raw("filler.material");
        Material m = Items.material(mat, Material.GRAY_STAINED_GLASS_PANE);
        ItemStack f = Items.make(m, " ", null);
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9, col = i % 9;
            if (row == 0 || row == rows() - 1 || col == 0 || col == 8) inv.setItem(i, f);
        }
    }

    protected void close() {
        viewer.closeInventory();
    }

    protected void sound() {
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /** Interior slots of a bordered menu (rows 1 .. rows-2, cols 1..7). */
    protected int[] interior() {
        int n = (rows() - 2) * 7;
        int[] out = new int[n];
        int k = 0;
        for (int r = 1; r < rows() - 1; r++) for (int c = 1; c < 8; c++) out[k++] = r * 9 + c;
        return out;
    }
}
