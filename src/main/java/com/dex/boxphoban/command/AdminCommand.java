package com.dex.boxphoban.command;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.editor.EditorService;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.TemplateManager;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Text;
import com.dex.boxphoban.world.MapService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /phobanadmin (alias /pba). */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("create", "edit", "exit", "import", "bounds", "region", "save", "info",
            "delete", "test", "list", "stop", "watch", "ticket", "reward", "loot", "resetcooldown", "reload", "help");

    private final BoxPhoBan plugin;

    public AdminCommand(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String key, String... kv) {
        plugin.messages().send(s, key, kv);
    }

    private DungeonTemplate template(CommandSender s, String[] args, int idx) {
        if (args.length > idx) {
            DungeonTemplate t = plugin.templates().get(args[idx]);
            if (t == null) send(s, "dungeon-not-found", "id", args[idx]);
            return t;
        }
        if (s instanceof Player p && plugin.editor().editing(p.getUniqueId())) return plugin.editor().session(p.getUniqueId()).template;
        send(s, "admin.need-dungeon");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("boxphoban.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player pl ? pl : null;
        switch (sub) {
            case "create" -> {
                if (p == null || args.length < 2) {
                    send(sender, "usage", "usage", "/" + label + " create <id>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!TemplateManager.validId(id)) {
                    send(p, "admin.bad-id");
                    return true;
                }
                if (plugin.templates().get(id) != null) {
                    send(p, "admin.exists", "id", id);
                    return true;
                }
                try {
                    DungeonTemplate t = plugin.templates().create(id);
                    send(p, "admin.created", "id", id);
                    plugin.editor().enter(p, t);
                } catch (IOException e) {
                    send(p, "admin.io-error", "error", e.getMessage());
                }
            }
            case "edit" -> {
                if (p == null) return players(sender);
                DungeonTemplate t = template(p, args, 1);
                if (t != null) plugin.editor().enter(p, t);
            }
            case "exit" -> {
                if (p == null) return players(sender);
                plugin.editor().exit(p, true);
            }
            case "import" -> {
                if (p == null) return players(sender);
                DungeonTemplate t = template(p, args, 1);
                if (t != null) importSelection(p, t);
            }
            case "bounds" -> {
                if (p == null) return players(sender);
                DungeonTemplate t = template(p, args, 1);
                if (t != null) bounds(p, t);
            }
            case "region" -> {
                if (p == null) return players(sender);
                region(p, args);
            }
            case "save" -> {
                DungeonTemplate t = template(sender, args, 1);
                if (t != null) save(sender, t);
            }
            case "info" -> {
                DungeonTemplate t = template(sender, args, 1);
                if (t != null) info(sender, t);
            }
            case "delete" -> {
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    send(sender, "usage", "usage", "/" + label + " delete <id> confirm");
                    return true;
                }
                DungeonTemplate t = template(sender, args, 1);
                if (t == null) return true;
                if (plugin.instances().running(t) > 0) {
                    send(sender, "admin.delete-running");
                    return true;
                }
                send(sender, plugin.templates().delete(t) ? "admin.deleted" : "admin.delete-failed", "id", t.id);
            }
            case "test" -> {
                if (p == null) return players(sender);
                DungeonTemplate t = template(p, args, 1);
                if (t == null) return true;
                Difficulty d = args.length >= 3 ? t.difficulty(args[2]) : t.firstDifficulty();
                if (d == null) {
                    send(p, "difficulty-not-found", "id", args.length >= 3 ? args[2] : "");
                    return true;
                }
                if (plugin.editor().editing(p.getUniqueId())) plugin.editor().exit(p, true);
                plugin.rooms().startTest(p, t, d);
            }
            case "list" -> {
                if (plugin.instances().all().isEmpty()) {
                    send(sender, "admin.list-empty");
                    return true;
                }
                send(sender, "admin.list-header", "count", String.valueOf(plugin.instances().all().size()),
                        "slots", String.valueOf(plugin.slots().busyCount()), "max", String.valueOf(plugin.settings().maxInstances));
                for (Instance in : plugin.instances().all()) {
                    List<String> names = new ArrayList<>();
                    for (var u : in.members) names.add(in.names.getOrDefault(u, "?"));
                    send(sender, "admin.list-line", "id", String.valueOf(in.id), "dungeon", in.template.display,
                            "difficulty", in.difficulty.display(), "players", String.join(", ", names),
                            "stage", in.stage() == null ? "-" : in.stage().display, "time", Text.clock(in.elapsedSeconds()),
                            "slot", String.valueOf(in.slot), "phase", in.phase.name());
                }
            }
            case "stop", "watch" -> {
                if (args.length < 2) {
                    send(sender, "usage", "usage", "/" + label + " " + sub + " <id phòng>");
                    return true;
                }
                Instance in;
                try {
                    in = plugin.instances().get(Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    in = null;
                }
                if (in == null) {
                    send(sender, "admin.instance-not-found", "id", args[1]);
                    return true;
                }
                if (sub.equals("stop")) {
                    plugin.instances().stop(in);
                    send(sender, "admin.stopped", "id", String.valueOf(in.id));
                } else {
                    if (p == null) return players(sender);
                    plugin.instances().watch(p, in);
                }
            }
            case "ticket" -> ticket(sender, label, args);
            case "reward", "loot" -> {
                if (p == null) return players(sender);
                if (args.length < 3 || !args[1].equalsIgnoreCase("additem")) {
                    send(p, "usage", "usage", "/" + label + " " + sub + " additem <bảng>" + (sub.equals("loot") ? " [trọng số]" : ""));
                    return true;
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    send(p, "admin.hold-item");
                    return true;
                }
                boolean ok;
                if (sub.equals("reward")) {
                    ok = plugin.rewards().addRewardItem(args[2], hand.clone());
                } else {
                    int weight = 1;
                    if (args.length >= 4) {
                        try {
                            weight = Math.max(1, Integer.parseInt(args[3]));
                        } catch (NumberFormatException ignored) {
                            // default 1
                        }
                    }
                    ok = plugin.rewards().addLootItem(args[2], hand.clone(), weight);
                }
                send(p, ok ? "admin.item-added" : "admin.io-error", "table", args[2], "error", "");
            }
            case "resetcooldown" -> {
                if (args.length < 3) {
                    send(sender, "usage", "usage", "/" + label + " resetcooldown <người chơi> <phó bản>");
                    return true;
                }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                DungeonTemplate t = template(sender, args, 2);
                if (t == null) return true;
                plugin.db().resetCooldown(op.getUniqueId(), t.id);
                send(sender, "admin.cooldown-reset", "player", args[1], "dungeon", t.display);
            }
            case "reload" -> {
                plugin.reload();
                send(sender, "admin.reloaded");
            }
            default -> help(sender);
        }
        return true;
    }

    private boolean players(CommandSender s) {
        send(s, "players-only");
        return true;
    }

    private void help(CommandSender s) {
        for (String line : plugin.messages().list("admin-help")) s.sendMessage(Text.component(line));
    }

    private void importSelection(Player p, DungeonTemplate t) {
        MapService.Selection sel = plugin.maps().selection(p);
        if (sel == null) {
            send(p, "admin.no-selection");
            return;
        }
        Box src = sel.box();
        int half = plugin.dungeonWorld().maxHalfSize();
        if (src.sizeX() > half || src.sizeZ() > half) {
            send(p, "admin.too-big", "max", String.valueOf(half));
            return;
        }
        if (src.volume() > 20_000_000L) {
            send(p, "admin.too-big", "max", String.valueOf(half));
            return;
        }
        World w = plugin.dungeonWorld().world();
        BlockPos o = plugin.editor().origin(t);
        BlockPos targetMin = new BlockPos(o.x(), src.min().y(), o.z());
        Box rel = Box.of(new BlockPos(0, src.min().y() - o.y(), 0),
                new BlockPos(src.sizeX() - 1, src.max().y() - o.y(), src.sizeZ() - 1));
        send(p, "admin.import-started", "size", src.sizeX() + "x" + src.sizeY() + "x" + src.sizeZ());
        plugin.maps().copy(sel.world(), src, w, targetMin, err -> {
            if (err != null) {
                plugin.getLogger().warning("Import lỗi: " + err);
                send(p, "admin.io-error", "error", String.valueOf(err.getMessage()));
                return;
            }
            t.bounds = rel;
            plugin.editor().save(t, p);
            send(p, "admin.imported", "id", t.id);
            if (p.isOnline()) plugin.editor().enter(p, t);
        });
    }

    private void bounds(Player p, DungeonTemplate t) {
        Box rel;
        EditorService.Session s = plugin.editor().session(p.getUniqueId());
        if (s != null && s.template == t && s.pos1 != null && s.pos2 != null) {
            rel = Box.of(s.pos1, s.pos2);
        } else {
            MapService.Selection sel = plugin.maps().selection(p);
            if (sel == null || !plugin.dungeonWorld().is(sel.world())) {
                send(p, "admin.no-selection");
                return;
            }
            BlockPos o = plugin.editor().origin(t);
            rel = new Box(sel.box().min().minus(o), sel.box().max().minus(o));
        }
        int half = plugin.dungeonWorld().maxHalfSize();
        if (Math.abs(rel.min().x()) > half || Math.abs(rel.max().x()) > half || Math.abs(rel.min().z()) > half || Math.abs(rel.max().z()) > half) {
            send(p, "admin.too-big", "max", String.valueOf(half));
            return;
        }
        t.bounds = rel;
        plugin.editor().save(t, p);
        send(p, "admin.bounds-set", "size", rel.sizeX() + "x" + rel.sizeY() + "x" + rel.sizeZ());
    }

    private void region(Player p, String[] args) {
        EditorService.Session s = plugin.editor().session(p.getUniqueId());
        if (s == null) {
            send(p, "editor.not-editing");
            return;
        }
        DungeonTemplate t = s.template;
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (action) {
            case "add" -> {
                if (args.length < 3 || !TemplateManager.validId(args[2].toLowerCase(Locale.ROOT))) {
                    send(p, "input.bad-name");
                    return;
                }
                if (s.pos1 == null || s.pos2 == null) {
                    send(p, "editor.need-positions");
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                t.regions.put(name, Box.of(s.pos1, s.pos2));
                plugin.editor().save(t, p);
                send(p, "editor.region-added", "name", name);
            }
            case "remove" -> {
                if (args.length < 3 || t.regions.remove(args[2].toLowerCase(Locale.ROOT)) == null) {
                    send(p, "input.no-region", "value", args.length < 3 ? "" : args[2]);
                    return;
                }
                plugin.editor().save(t, p);
                send(p, "editor.region-removed", "name", args[2]);
            }
            case "wall" -> {
                if (args.length < 4 || !t.regions.containsKey(args[2].toLowerCase(Locale.ROOT))
                        || !(args[3].equalsIgnoreCase("open") || args[3].equalsIgnoreCase("closed"))) {
                    send(p, "usage", "usage", "/pba region wall <vùng> open|closed");
                    return;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                boolean closed = args[3].equalsIgnoreCase("closed");
                if (closed) t.wallsClosedAtStart.add(name);
                else t.wallsClosedAtStart.remove(name);
                plugin.editor().save(t, p);
                send(p, closed ? "editor.wall-start-closed" : "editor.wall-start-open", "name", name);
            }
            default -> send(p, "editor.region-list", "list", t.regions.isEmpty() ? "-" : String.join(", ", t.regions.keySet()));
        }
    }

    private void save(CommandSender s, DungeonTemplate t) {
        if (t.bounds == null) {
            send(s, "admin.no-bounds");
            return;
        }
        if (t.start == null) {
            send(s, "admin.no-start");
            return;
        }
        if (!t.bounds.contains(new BlockPos((int) Math.floor(t.start.x()), (int) Math.floor(t.start.y()), (int) Math.floor(t.start.z())))) {
            send(s, "admin.start-outside");
        }
        World w = plugin.dungeonWorld().world();
        BlockPos o = plugin.editor().origin(t);
        plugin.editor().save(t, s instanceof Player pl ? pl : null);
        send(s, "admin.saving");
        plugin.maps().save(w, t.bounds.shift(o), o, t.schematicFile(), err -> {
            if (err != null) {
                plugin.getLogger().warning("Lưu schematic lỗi: " + err);
                send(s, "admin.io-error", "error", String.valueOf(err.getMessage()));
                return;
            }
            send(s, "admin.saved", "id", t.id, "nodes", String.valueOf(t.nodes.size()));
        });
    }

    private void info(CommandSender s, DungeonTemplate t) {
        String yes = plugin.messages().raw("admin.info-yes"), no = plugin.messages().raw("admin.info-no");
        send(s, "admin.info", "id", t.id, "dungeon", t.display,
                "enabled", t.enabled ? yes : no, "map", t.schematicFile().isFile() ? yes : no,
                "bounds", t.bounds == null ? no : t.bounds.sizeX() + "x" + t.bounds.sizeY() + "x" + t.bounds.sizeZ(),
                "start", t.start == null ? no : yes, "nodes", String.valueOf(t.nodes.size()),
                "stages", String.valueOf(t.stages.size()), "regions", String.valueOf(t.regions.size()),
                "points", String.valueOf(t.points.size()), "difficulties", String.join(", ", t.difficulties.keySet()),
                "playable", t.playable() ? yes : no, "running", String.valueOf(plugin.instances().running(t)));
    }

    private void ticket(CommandSender s, String label, String[] args) {
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            send(s, "usage", "usage", "/" + label + " ticket give <người chơi> <vé> [số lượng]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            send(s, "player-not-found", "player", args[2]);
            return;
        }
        if (!plugin.tickets().exists(args[3])) {
            send(s, "admin.ticket-not-found", "id", args[3]);
            return;
        }
        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[4])));
            } catch (NumberFormatException ignored) {
                // 1
            }
        }
        ItemStack it = plugin.tickets().create(args[3], amount);
        target.getInventory().addItem(it).values().forEach(l -> target.getWorld().dropItemNaturally(target.getLocation(), l));
        send(s, "admin.ticket-given", "player", target.getName(), "amount", String.valueOf(amount), "ticket", plugin.tickets().name(args[3]));
        send(target, "ticket-received", "amount", String.valueOf(amount), "ticket", plugin.tickets().name(args[3]));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("boxphoban.admin")) return out;
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(last)) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        switch (sub) {
            case "edit", "import", "bounds", "save", "info", "delete", "test" -> {
                if (args.length == 2) for (DungeonTemplate t : plugin.templates().all()) options.add(t.id);
                else if (args.length == 3 && sub.equals("test")) {
                    DungeonTemplate t = plugin.templates().get(args[1]);
                    if (t != null) options.addAll(t.difficulties.keySet());
                } else if (args.length == 3 && sub.equals("delete")) options.add("confirm");
            }
            case "stop", "watch" -> {
                if (args.length == 2) for (Instance in : plugin.instances().all()) options.add(String.valueOf(in.id));
            }
            case "region" -> {
                if (args.length == 2) options.addAll(List.of("add", "remove", "list", "wall"));
                else if (args.length == 4 && args[1].equalsIgnoreCase("wall")) options.addAll(List.of("open", "closed"));
                else if (args.length == 3 && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("wall")) && sender instanceof Player p
                        && plugin.editor().editing(p.getUniqueId())) {
                    options.addAll(plugin.editor().session(p.getUniqueId()).template.regions.keySet());
                }
            }
            case "ticket" -> {
                if (args.length == 2) options.add("give");
                else if (args.length == 3) for (Player o : Bukkit.getOnlinePlayers()) options.add(o.getName());
                else if (args.length == 4) options.addAll(plugin.tickets().ids());
            }
            case "reward", "loot" -> {
                if (args.length == 2) options.add("additem");
            }
            case "resetcooldown" -> {
                if (args.length == 2) for (Player o : Bukkit.getOnlinePlayers()) options.add(o.getName());
                else if (args.length == 3) for (DungeonTemplate t : plugin.templates().all()) options.add(t.id);
            }
            default -> {
            }
        }
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }
}
