package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Open rooms of one dungeon (plus private rooms that invited the viewer). */
public final class RoomListMenu extends Menu {

    private final DungeonTemplate template;

    public RoomListMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate template) {
        super(plugin, viewer);
        this.template = template;
    }

    public DungeonTemplate template() {
        return template;
    }

    @Override
    protected String title() {
        return Text.replace(gui().raw("rooms.title"), "dungeon", template.display);
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        filler();
        Words w = plugin.words();
        List<Room> list = new ArrayList<>();
        for (Room r : plugin.rooms().rooms(template)) {
            if (r.open || r.invites.containsKey(viewer.getUniqueId()) || r.members.contains(viewer.getUniqueId())) list.add(r);
        }
        int[] slots = interior();
        if (list.isEmpty()) set(22, item("rooms.empty", Material.BARRIER), null);
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            Room r = list.get(i);
            OfflinePlayer leader = Bukkit.getOfflinePlayer(r.leader);
            String name = Text.replace(gui().raw("rooms.room.name"), "leader", leader.getName() == null ? "?" : leader.getName());
            List<String> lore = Text.replaceLines(gui().list("rooms.room.lore"), "difficulty", r.difficulty.display(),
                    "count", String.valueOf(r.members.size()), "max", String.valueOf(template.maxPlayers),
                    "status", w.status(r.open),
                    "action", gui().raw(r.members.contains(viewer.getUniqueId()) ? "rooms.action-open"
                            : r.full() ? "rooms.action-full" : "rooms.action-join"));
            set(slots[i], Items.head(leader, name, lore), (p, c) -> {
                sound();
                if (r.members.contains(p.getUniqueId())) {
                    new RoomMenu(plugin, p).open();
                    return;
                }
                if (plugin.rooms().join(p, r, false)) new RoomMenu(plugin, p).open();
                else redraw();
            });
        }
        set(48, item("back", Material.ARROW), (p, c) -> {
            sound();
            new DungeonListMenu(plugin, p).open();
        });
        set(50, item("rooms.create", Material.LIME_DYE), (p, c) -> {
            sound();
            new DifficultyMenu(plugin, p, template, DifficultyMenu.Mode.CREATE).open();
        });
    }
}
