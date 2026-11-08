package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Pick a difficulty: create a room, or change the difficulty of your room. */
public final class DifficultyMenu extends Menu {

    public enum Mode { CREATE, CHANGE }

    private final DungeonTemplate template;
    private final Mode mode;

    public DifficultyMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate template, Mode mode) {
        super(plugin, viewer);
        this.template = template;
        this.mode = mode;
    }

    @Override
    protected String title() {
        return Text.replace(gui().raw("difficulty.title"), "dungeon", template.display);
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void draw() {
        filler();
        Words w = plugin.words();
        List<Difficulty> list = new ArrayList<>(template.difficulties.values());
        int[] slots = layout(list.size());
        for (int i = 0; i < list.size() && i < slots.length; i++) {
            Difficulty d = list.get(i);
            set(slots[i], item("difficulty.item", d.icon(), true, "name", d.display(), "time", w.time(d),
                    "level", String.valueOf(d.mobLevel()), "health", "x" + Text.number(d.healthMultiplier()),
                    "damage", "x" + Text.number(d.damageMultiplier()), "lives", w.lives(template),
                    "action", gui().raw(mode == Mode.CREATE ? "difficulty.action-create" : "difficulty.action-change")),
                    (p, c) -> {
                        sound();
                        if (mode == Mode.CREATE) {
                            Room r = plugin.rooms().create(p, template, d);
                            if (r != null) new RoomMenu(plugin, p).open();
                            else close();
                        } else {
                            plugin.rooms().setDifficulty(p, d);
                            new RoomMenu(plugin, p).open();
                        }
                    });
        }
        set(31, item("back", Material.ARROW), (p, c) -> {
            sound();
            if (mode == Mode.CHANGE) new RoomMenu(plugin, p).open();
            else new DungeonListMenu(plugin, p).open();
        });
    }

    /** Centres up to 7 items on row 1 (one slot apart when there are at most 4). */
    private static int[] layout(int n) {
        return switch (n) {
            case 1 -> new int[]{13};
            case 2 -> new int[]{12, 14};
            case 3 -> new int[]{11, 13, 15};
            case 4 -> new int[]{10, 12, 14, 16};
            default -> new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        };
    }
}
