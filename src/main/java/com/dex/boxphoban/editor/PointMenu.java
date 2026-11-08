package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.gui.Menu;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.TemplateManager;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Start point and named points (checkpoints, teleport targets). */
public final class PointMenu extends Menu {

    private final DungeonTemplate t;

    public PointMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate t) {
        super(plugin, viewer);
        this.t = t;
    }

    @Override
    protected String title() {
        return gui().raw("editor.points.title");
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void draw() {
        set(0, item("editor.points.set-start", Material.LIME_BED, "value", t.start == null ? gui().raw("editor.empty-value") : t.start.toString()),
                (p, c) -> {
                    sound();
                    Point pt = plugin.editor().relativePoint(t, p.getLocation());
                    if (pt == null) {
                        plugin.messages().send(p, "editor.outside");
                        return;
                    }
                    t.start = pt;
                    plugin.editor().save(t, p);
                    plugin.messages().send(p, "editor.start-set");
                    redraw();
                });
        set(1, item("editor.points.add", Material.LIME_DYE), (p, c) -> {
            sound();
            plugin.input().ask(p, "input.point-name", text -> {
                String name = text.trim().toLowerCase();
                if (!TemplateManager.validId(name)) {
                    plugin.messages().send(p, "input.bad-name");
                } else {
                    Point pt = plugin.editor().relativePoint(t, p.getLocation());
                    if (pt == null) plugin.messages().send(p, "editor.outside");
                    else {
                        t.points.put(name, pt);
                        plugin.editor().save(t, p);
                        plugin.messages().send(p, "editor.point-set", "name", name);
                    }
                }
                new PointMenu(plugin, p, t).open();
            }, () -> new PointMenu(plugin, p, t).open());
        });
        List<Map.Entry<String, Point>> list = new ArrayList<>(t.points.entrySet());
        for (int i = 0; i < list.size() && i < 27; i++) {
            String name = list.get(i).getKey();
            Point pt = list.get(i).getValue();
            set(9 + i, Items.make(Material.RESPAWN_ANCHOR, Text.replace(gui().raw("editor.points.point.name"), "name", name),
                    Text.replaceLines(gui().list("editor.points.point.lore"), "value", pt.toString())), (p, c) -> {
                sound();
                if (c == ClickType.SHIFT_RIGHT) {
                    t.points.remove(name);
                    plugin.editor().save(t, p);
                    redraw();
                } else if (c == ClickType.SHIFT_LEFT) {
                    Point np = plugin.editor().relativePoint(t, p.getLocation());
                    if (np == null) {
                        plugin.messages().send(p, "editor.outside");
                        return;
                    }
                    t.points.put(name, np);
                    plugin.editor().save(t, p);
                    plugin.messages().send(p, "editor.point-set", "name", name);
                    redraw();
                } else {
                    plugin.sessions().teleport(p, pt.toLocation(plugin.editor().world(), plugin.editor().origin(t)));
                    close();
                }
            });
        }
        set(8, item("editor.points.stages", Material.WHITE_BANNER, "count", String.valueOf(t.stages.size())), (p, c) -> {
            sound();
            new StageMenu(plugin, p, t).open();
        });
    }
}
