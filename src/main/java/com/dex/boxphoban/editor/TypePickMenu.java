package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.gui.Menu;
import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.ConditionType;
import com.dex.boxphoban.template.TriggerType;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Pick a trigger / condition / action type. */
public final class TypePickMenu extends Menu {

    public enum Kind { TRIGGER, CONDITION, ACTION }

    private final Kind kind;
    private final Consumer<String> picked;
    private final Runnable back;

    public TypePickMenu(BoxPhoBan plugin, Player viewer, Kind kind, Consumer<String> picked, Runnable back) {
        super(plugin, viewer);
        this.kind = kind;
        this.picked = picked;
        this.back = back;
    }

    @Override
    protected String title() {
        return gui().raw("editor.pick." + kind.name().toLowerCase() + "-title");
    }

    @Override
    protected int rows() {
        return 3;
    }

    private void add(int slot, String name, Material icon, String display, String description) {
        set(slot, Items.make(icon, Text.replace(gui().raw("editor.pick.name"), "type", display),
                Text.replaceLines(gui().list("editor.pick.lore"), "description", description)), (p, c) -> {
            sound();
            picked.accept(name);
        });
    }

    @Override
    protected void draw() {
        int slot = 0;
        switch (kind) {
            case TRIGGER -> {
                for (TriggerType t : TriggerType.values()) add(slot++, t.name(), t.icon, t.display, t.description);
            }
            case CONDITION -> {
                for (ConditionType t : ConditionType.values()) add(slot++, t.name(), t.icon, t.display, t.description);
            }
            case ACTION -> {
                for (ActionType t : ActionType.values()) add(slot++, t.name(), t.icon, t.display, t.description);
            }
        }
        set(22, item("back", Material.ARROW), (p, c) -> {
            sound();
            back.run();
        });
    }
}
