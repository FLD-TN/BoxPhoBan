package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.InstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Opens menus and keeps open room menus up to date. */
public final class MenuService {

    private final BoxPhoBan plugin;

    public MenuService(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player p) {
        new DungeonListMenu(plugin, p).open();
    }

    public void openResult(Player p, InstanceManager.Result r) {
        new ResultMenu(plugin, p, r).open();
    }

    public void openLoot(Player p, List<ItemStack> items) {
        new LootMenu(plugin, p, items).open();
    }

    /** Re-draws every open room list / room menu (members changed). */
    public void refreshRooms() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Object holder = p.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof RoomListMenu m) m.redraw();
            else if (holder instanceof RoomMenu m) {
                if (plugin.rooms().room(p.getUniqueId()) == null) p.closeInventory();
                else m.redraw();
            }
        }
    }

    public void closeRoomMenu(Player p) {
        Object holder = p.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof RoomMenu || holder instanceof DifficultyMenu) p.closeInventory();
    }
}
