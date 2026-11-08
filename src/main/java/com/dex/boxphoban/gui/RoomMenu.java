package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The viewer's own room: members, invite, public/private, difficulty, start, leave. */
public final class RoomMenu extends Menu {

    public RoomMenu(BoxPhoBan plugin, Player viewer) {
        super(plugin, viewer);
    }

    private Room room() {
        return plugin.rooms().room(viewer.getUniqueId());
    }

    @Override
    protected String title() {
        Room r = room();
        return Text.replace(gui().raw("room.title"), "dungeon", r == null ? "" : r.template.display);
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    public void open() {
        if (room() == null) {
            plugin.messages().send(viewer, "room.not-in-room");
            return;
        }
        super.open();
    }

    @Override
    protected void draw() {
        filler();
        Room r = room();
        if (r == null) return;
        Words w = plugin.words();
        boolean leader = r.leader.equals(viewer.getUniqueId());
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        List<UUID> members = new ArrayList<>(r.members);
        int max = Math.min(slots.length, r.template.maxPlayers);
        for (int i = 0; i < max; i++) {
            if (i >= members.size()) {
                set(slots[i], item("room.empty-slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE), null);
                continue;
            }
            UUID u = members.get(i);
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            boolean isLeader = r.leader.equals(u);
            String name = Text.replace(gui().raw("room.member.name"), "player", op.getName() == null ? "?" : op.getName(),
                    "tag", gui().raw(isLeader ? "room.leader-tag" : "room.member-tag"));
            List<String> lore = new ArrayList<>(Text.replaceLines(gui().list("room.member.lore"),
                    "status", gui().raw(op.isOnline() ? "room.online" : "room.offline")));
            boolean manage = leader && !isLeader;
            if (manage) lore.addAll(gui().list("room.member-actions"));
            set(slots[i], Items.head(op, name, lore), manage ? (p, c) -> {
                sound();
                if (c == ClickType.RIGHT || c == ClickType.SHIFT_RIGHT) plugin.rooms().kick(p, u);
                else plugin.rooms().transfer(p, u);
                redraw();
            } : null);
        }
        set(4, item("room.info", r.template.icon, true, "dungeon", r.template.display, "difficulty", r.difficulty.display(),
                "count", String.valueOf(r.members.size()), "max", String.valueOf(r.template.maxPlayers),
                "min", String.valueOf(r.template.minPlayers), "status", w.status(r.open), "entry", w.entry(r.template),
                "lives", w.lives(r.template), "time", w.time(r.difficulty)), null);
        if (leader) {
            set(37, item("room.invite", Material.WRITABLE_BOOK), (p, c) -> {
                sound();
                plugin.input().ask(p, "input.invite", text -> {
                    Player target = Bukkit.getPlayerExact(text);
                    if (target == null) plugin.messages().send(p, "player-not-found", "player", text);
                    else plugin.rooms().invite(p, target);
                    if (plugin.rooms().room(p.getUniqueId()) != null) new RoomMenu(plugin, p).open();
                }, () -> new RoomMenu(plugin, p).open());
            });
            set(39, item("room.toggle", r.open ? Material.LIME_DYE : Material.GRAY_DYE, "status", w.status(r.open)), (p, c) -> {
                sound();
                plugin.rooms().toggleOpen(p);
                redraw();
            });
            set(41, item("room.difficulty", Material.NETHER_STAR, "difficulty", r.difficulty.display()), (p, c) -> {
                sound();
                new DifficultyMenu(plugin, p, r.template, DifficultyMenu.Mode.CHANGE).open();
            });
            set(43, item("room.start", Material.LIME_CONCRETE, "count", String.valueOf(r.members.size()),
                    "min", String.valueOf(r.template.minPlayers)), (p, c) -> {
                sound();
                plugin.rooms().start(p);
                if (plugin.rooms().room(p.getUniqueId()) != null) redraw();
            });
        } else {
            set(40, item("room.waiting", Material.CLOCK), null);
        }
        set(44, item("room.leave", Material.RED_DYE), (p, c) -> {
            sound();
            plugin.rooms().leave(p);
            close();
        });
        set(36, item("back", Material.ARROW), (p, c) -> {
            sound();
            new RoomListMenu(plugin, p, r.template).open();
        });
    }
}
