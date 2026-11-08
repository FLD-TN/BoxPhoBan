package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.gui.Menu;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Stage;
import com.dex.boxphoban.template.TemplateManager;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.Collections;

/** Stages in order: left = rename, right = objective, shift+left = move up, shift+right = delete. */
public final class StageMenu extends Menu {

    private final DungeonTemplate t;

    public StageMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate t) {
        super(plugin, viewer);
        this.t = t;
    }

    @Override
    protected String title() {
        return gui().raw("editor.stages.title");
    }

    @Override
    protected int rows() {
        return 4;
    }

    private void reopen(Player p) {
        new StageMenu(plugin, p, t).open();
    }

    @Override
    protected void draw() {
        for (int i = 0; i < t.stages.size() && i < 27; i++) {
            Stage s = t.stages.get(i);
            int idx = i;
            set(i, Items.make(Material.WHITE_BANNER, Text.replace(gui().raw("editor.stages.stage.name"), "name", s.display),
                    Text.replaceLines(gui().list("editor.stages.stage.lore"), "id", s.id, "index", String.valueOf(i + 1),
                            "objective", s.objective.isEmpty() ? gui().raw("editor.empty-value") : s.objective)), (p, c) -> {
                sound();
                if (c == ClickType.SHIFT_RIGHT) {
                    t.stages.remove(idx);
                    plugin.editor().save(t, p);
                    redraw();
                } else if (c == ClickType.SHIFT_LEFT) {
                    if (idx > 0) Collections.swap(t.stages, idx, idx - 1);
                    plugin.editor().save(t, p);
                    redraw();
                } else if (c.isRightClick()) {
                    plugin.input().ask(p, "input.stage-objective", text -> {
                        s.objective = text;
                        plugin.editor().save(t, p);
                        reopen(p);
                    }, () -> reopen(p));
                } else {
                    plugin.input().ask(p, "input.stage-display", text -> {
                        s.display = text;
                        plugin.editor().save(t, p);
                        reopen(p);
                    }, () -> reopen(p));
                }
            });
        }
        set(31, item("editor.stages.add", Material.LIME_DYE), (p, c) -> {
            sound();
            plugin.input().ask(p, "input.stage-id", text -> {
                String id = text.trim().toLowerCase();
                if (!TemplateManager.validId(id) || t.stage(id) != null) {
                    plugin.messages().send(p, "input.bad-name");
                } else {
                    t.stages.add(new Stage(id, "&f" + id, ""));
                    plugin.editor().save(t, p);
                    plugin.messages().send(p, "editor.stage-added", "id", id);
                }
                reopen(p);
            }, () -> reopen(p));
        });
        set(27, item("back", Material.ARROW), (p, c) -> {
            sound();
            new PointMenu(plugin, p, t).open();
        });
    }
}
