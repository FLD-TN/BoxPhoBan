package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.gui.Menu;
import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.ConditionType;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.template.ParamSpec;
import com.dex.boxphoban.template.TriggerType;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Edit the node on one block: trigger, flags, conditions, actions. */
public final class NodeMenu extends Menu {

    private final DungeonTemplate t;
    private final BlockPos pos;

    public NodeMenu(BoxPhoBan plugin, Player viewer, DungeonTemplate t, BlockPos pos) {
        super(plugin, viewer);
        this.t = t;
        this.pos = pos;
    }

    private Node node() {
        return t.nodeAt(pos);
    }

    @Override
    protected String title() {
        Node n = node();
        return Text.replace(gui().raw("editor.node.title"), "id", n == null ? gui().raw("editor.node.new") : n.id);
    }

    @Override
    protected int rows() {
        return 6;
    }

    private void saved() {
        plugin.editor().save(t, viewer);
    }

    /** Lore lines "• label: value" for an element. */
    static List<String> paramLines(BoxPhoBan plugin, Element e, List<ParamSpec> specs) {
        List<String> out = new ArrayList<>();
        for (ParamSpec s : specs) {
            String v = e.params.getOrDefault(s.key(), "");
            out.add(Text.replace(plugin.gui().raw("editor.param-line"), "label", s.label(),
                    "value", v.isEmpty() ? plugin.gui().raw("editor.empty-value") : v));
        }
        return out;
    }

    @Override
    protected void draw() {
        Node n = node();
        if (n == null) {
            set(22, item("editor.node.create", Material.LIME_DYE, "pos", pos.toString()), (p, c) -> {
                sound();
                Node created = new Node(t.newNodeId(), pos);
                t.nodes.put(created.id, created);
                saved();
                plugin.messages().send(p, "editor.node-created", "id", created.id);
                redraw();
            });
            set(49, item("close", Material.BARRIER), (p, c) -> close());
            return;
        }
        set(4, item("editor.node.info", Material.NAME_TAG, "id", n.id, "pos", n.pos.toString()), null);

        TriggerType tt = n.triggerType();
        List<String> tl = new ArrayList<>(Text.replaceLines(gui().list("editor.node.trigger.lore"),
                "type", tt == null ? n.trigger.type : tt.display, "description", tt == null ? "" : tt.description));
        if (tt != null) tl.addAll(1, paramLines(plugin, n.trigger, tt.params));
        set(10, Items.make(tt == null ? Material.BARRIER : tt.icon, gui().raw("editor.node.trigger.name"), tl), (p, c) -> {
            sound();
            if (c.isRightClick() && tt != null && !tt.params.isEmpty()) {
                new ParamMenu(plugin, p, t, n.trigger, tt.params, () -> new NodeMenu(plugin, p, t, pos).open()).open();
            } else {
                new TypePickMenu(plugin, p, TypePickMenu.Kind.TRIGGER, type -> {
                    TriggerType nt = TriggerType.valueOf(type);
                    n.trigger = Element.withDefaults(nt.name(), nt.params);
                    saved();
                    if (!nt.params.isEmpty()) new ParamMenu(plugin, p, t, n.trigger, nt.params, () -> new NodeMenu(plugin, p, t, pos).open()).open();
                    else new NodeMenu(plugin, p, t, pos).open();
                }, () -> new NodeMenu(plugin, p, t, pos).open()).open();
            }
        });
        set(12, item("editor.node.repeat", n.repeat ? Material.LIME_DYE : Material.GRAY_DYE,
                "value", gui().raw(n.repeat ? "editor.state-on" : "editor.state-off")), (p, c) -> {
            sound();
            n.repeat = !n.repeat;
            saved();
            redraw();
        });
        set(13, item("editor.node.per-player", n.perPlayer ? Material.LIME_DYE : Material.GRAY_DYE,
                "value", gui().raw(n.perPlayer ? "editor.state-on" : "editor.state-off")), (p, c) -> {
            sound();
            n.perPlayer = !n.perPlayer;
            saved();
            redraw();
        });
        set(14, item("editor.node.delay", Material.CLOCK, "value", String.valueOf(n.delay)), (p, c) -> {
            sound();
            plugin.input().ask(p, "input.delay", text -> {
                try {
                    n.delay = Math.max(0, Integer.parseInt(text.trim()));
                    saved();
                } catch (NumberFormatException e) {
                    plugin.messages().send(p, "input.invalid");
                }
                new NodeMenu(plugin, p, t, pos).open();
            }, () -> new NodeMenu(plugin, p, t, pos).open());
        });
        set(16, item("editor.node.delete", Material.LAVA_BUCKET), (p, c) -> {
            if (c != ClickType.SHIFT_LEFT && c != ClickType.SHIFT_RIGHT) {
                plugin.messages().send(p, "editor.node-delete-confirm");
                return;
            }
            sound();
            t.nodes.remove(n.id);
            saved();
            plugin.messages().send(p, "editor.node-deleted", "id", n.id);
            close();
        });

        // conditions: row 3
        set(18, item("editor.node.add-condition", Material.COMPARATOR, "count", String.valueOf(n.conditions.size())), (p, c) -> {
            sound();
            if (n.conditions.size() >= 8) return;
            new TypePickMenu(plugin, p, TypePickMenu.Kind.CONDITION, type -> {
                ConditionType ct = ConditionType.valueOf(type);
                Element e = Element.withDefaults(ct.name(), ct.params);
                n.conditions.add(e);
                saved();
                new ParamMenu(plugin, p, t, e, ct.params, () -> new NodeMenu(plugin, p, t, pos).open()).open();
            }, () -> new NodeMenu(plugin, p, t, pos).open()).open();
        });
        for (int i = 0; i < n.conditions.size() && i < 8; i++) {
            Element e = n.conditions.get(i);
            ConditionType ct = ConditionType.parse(e.type);
            List<String> lore = new ArrayList<>();
            if (ct != null) lore.addAll(paramLines(plugin, e, ct.params));
            lore.addAll(gui().list("editor.node.element-actions"));
            int idx = i;
            set(19 + i, Items.make(ct == null ? Material.BARRIER : ct.icon,
                    Text.replace(gui().raw("editor.node.condition-name"), "type", ct == null ? e.type : ct.display), lore), (p, c) ->
                    elementClick(p, c, n.conditions, idx, ct == null ? List.of() : ct.params));
        }

        // actions: rows 4-5
        set(27, item("editor.node.add-action", Material.BLAZE_POWDER, "count", String.valueOf(n.actions.size())), (p, c) -> {
            sound();
            if (n.actions.size() >= 16) return;
            new TypePickMenu(plugin, p, TypePickMenu.Kind.ACTION, type -> {
                ActionType at = ActionType.valueOf(type);
                Element e = Element.withDefaults(at.name(), at.params);
                n.actions.add(e);
                saved();
                if (!at.params.isEmpty()) new ParamMenu(plugin, p, t, e, at.params, () -> new NodeMenu(plugin, p, t, pos).open()).open();
                else new NodeMenu(plugin, p, t, pos).open();
            }, () -> new NodeMenu(plugin, p, t, pos).open()).open();
        });
        int[] actionSlots = {28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43, 44};
        for (int i = 0; i < n.actions.size() && i < actionSlots.length; i++) {
            Element e = n.actions.get(i);
            ActionType at = ActionType.parse(e.type);
            List<String> lore = new ArrayList<>();
            lore.add(Text.replace(gui().raw("editor.node.order"), "index", String.valueOf(i + 1)));
            if (at != null) lore.addAll(paramLines(plugin, e, at.params));
            lore.addAll(gui().list("editor.node.element-actions"));
            int idx = i;
            set(actionSlots[i], Items.make(at == null ? Material.BARRIER : at.icon,
                    Text.replace(gui().raw("editor.node.action-name"), "type", at == null ? e.type : at.display), lore), (p, c) ->
                    elementClick(p, c, n.actions, idx, at == null ? List.of() : at.params));
        }
        set(49, item("close", Material.BARRIER), (p, c) -> close());
    }

    /** Left = edit params, shift+left = move up, shift+right = remove. */
    private void elementClick(Player p, ClickType c, List<Element> list, int idx, List<ParamSpec> specs) {
        sound();
        if (idx >= list.size()) return;
        if (c == ClickType.SHIFT_RIGHT) {
            list.remove(idx);
            saved();
            redraw();
        } else if (c == ClickType.SHIFT_LEFT) {
            if (idx > 0) Collections.swap(list, idx, idx - 1);
            saved();
            redraw();
        } else if (!specs.isEmpty()) {
            new ParamMenu(plugin, p, t, list.get(idx), specs, () -> new NodeMenu(plugin, p, t, pos).open()).open();
        }
    }
}
