package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** /phoban: every playable dungeon. Left click = rooms, right click = create a room. */
public final class DungeonListMenu extends Menu {

    public DungeonListMenu(BoxPhoBan plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return gui().raw("main.title");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        filler();
        Words w = plugin.words();
        List<DungeonTemplate> list = new ArrayList<>();
        for (DungeonTemplate t : plugin.templates().all()) if (t.playable()) list.add(t);
        int[] slots = interior();
        if (list.isEmpty()) {
            set(22, item("main.empty", Material.BARRIER), null);
        }
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            DungeonTemplate t = list.get(i);
            int open = 0;
            for (Room r : plugin.rooms().rooms(t)) if (r.open) open++;
            set(slots[i], item("main.dungeon", t.icon, true,
                    "name", t.display, "color", t.color,
                    "description", String.join("\n", t.description.stream().map(l -> "&#BFC7D5" + l).toList()),
                    "players", t.minPlayers == t.maxPlayers ? String.valueOf(t.maxPlayers) : t.minPlayers + "-" + t.maxPlayers,
                    "difficulties", w.difficultyList(t),
                    "entry", w.entry(t), "lives", w.lives(t),
                    "cooldown", w.cooldown(viewer, t), "clears", w.clears(viewer, t), "best", w.best(viewer, t),
                    "rooms", String.valueOf(open)), (p, click) -> {
                sound();
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    new DifficultyMenu(plugin, p, t, DifficultyMenu.Mode.CREATE).open();
                } else {
                    new RoomListMenu(plugin, p, t).open();
                }
            });
        }
        Room mine = plugin.rooms().room(viewer.getUniqueId());
        if (mine != null) {
            set(48, item("main.my-room", Material.OAK_DOOR, "dungeon", mine.template.display,
                    "count", String.valueOf(mine.members.size()), "max", String.valueOf(mine.template.maxPlayers)), (p, c) -> {
                sound();
                new RoomMenu(plugin, p).open();
            });
        }
        int pending = plugin.db().pending(viewer.getUniqueId()).size();
        set(50, item("main.pending", Material.CHEST, "count", Text.number(pending)), (p, c) -> {
            sound();
            new PendingMenu(plugin, p).open();
        });
        set(49, item("close", Material.BARRIER), (p, c) -> close());
    }
}
