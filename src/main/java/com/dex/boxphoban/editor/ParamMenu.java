package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.gui.Menu;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.ParamSpec;
import com.dex.boxphoban.template.Stage;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Edit the parameters of a trigger / condition / action; each value is typed in chat. */
public final class ParamMenu extends Menu {

    private final DungeonTemplate t;
    private final Element element;
    private final List<ParamSpec> specs;
    private final Runnable back;

    public ParamMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate t, Element element, List<ParamSpec> specs, Runnable back) {
        super(plugin, viewer);
        this.t = t;
        this.element = element;
        this.specs = specs;
        this.back = back;
    }

    @Override
    protected String title() {
        return gui().raw("editor.param.title");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < specs.size() && i < slots.length; i++) {
            ParamSpec s = specs.get(i);
            String v = element.params.getOrDefault(s.key(), "");
            set(slots[i], Items.make(Material.PAPER, Text.replace(gui().raw("editor.param.name"), "label", s.label()),
                    Text.replaceLines(gui().list("editor.param.lore"), "value", v.isEmpty() ? gui().raw("editor.empty-value") : v,
                            "hint", s.hint().isEmpty() ? "-" : s.hint(), "options", options(s))), (p, c) -> {
                sound();
                if (c.isRightClick()) {
                    element.params.put(s.key(), s.def());
                    plugin.editor().save(t, p);
                    redraw();
                    return;
                }
                plugin.input().ask(p, "input.param", text -> {
                    String err = validate(s, text);
                    if (err != null) {
                        plugin.messages().send(p, err, "value", text);
                    } else {
                        element.params.put(s.key(), s.normalise(text) == null ? text : s.normalise(text));
                        plugin.editor().save(t, p);
                    }
                    new ParamMenu(plugin, p, t, element, specs, back).open();
                }, () -> new ParamMenu(plugin, p, t, element, specs, back).open(), "label", s.label(), "options", options(s));
            });
        }
        set(22, item("back", Material.ARROW), (p, c) -> {
            sound();
            back.run();
        });
    }

    private String options(ParamSpec s) {
        return switch (s.kind()) {
            case REGION, REGIONS -> t.regions.isEmpty() ? "-" : String.join(", ", t.regions.keySet());
            case POINT -> t.points.isEmpty() ? "-" : String.join(", ", t.points.keySet());
            case STAGE -> t.stages.isEmpty() ? "-" : String.join(", ", t.stages.stream().map(st -> st.id).toList());
            case NODE -> t.nodes.isEmpty() ? "-" : String.join(", ", t.nodes.keySet());
            case CHOICE -> s.hint().replace("|", ", ");
            case BOOL -> "true, false";
            default -> "-";
        };
    }

    /** messages.yml key of the problem, or null when valid. */
    private String validate(ParamSpec s, String text) {
        String v = text.trim();
        if (s.normalise(v) == null) return "input.invalid";
        switch (s.kind()) {
            case REGION -> {
                if (!t.regions.containsKey(v)) return "input.no-region";
            }
            case REGIONS -> {
                for (String r : v.split(",")) if (!t.regions.containsKey(r.trim())) return "input.no-region";
            }
            case POINT -> {
                if (!t.points.containsKey(v)) return "input.no-point";
            }
            case STAGE -> {
                if (!v.equalsIgnoreCase("next")) {
                    Stage st = t.stage(v);
                    if (st == null) return "input.no-stage";
                }
            }
            case NODE -> {
                if (!t.nodes.containsKey(v)) return "input.no-node";
            }
            case MOB -> {
                if (!v.equals("*") && MythicBukkit.inst().getMobManager().getMythicMob(v).isEmpty()) return "input.no-mob";
            }
            default -> {
            }
        }
        return null;
    }
}
