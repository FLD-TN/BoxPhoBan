package com.dex.boxphoban.command;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.engine.InstanceManager;
import com.dex.boxphoban.gui.PendingMenu;
import com.dex.boxphoban.gui.RoomMenu;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /phoban (alias /pb). */
public final class PhoBanCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("create", "join", "invite", "accept", "deny", "kick", "leader",
            "public", "start", "leave", "room", "rewards", "help");

    private final BoxPhoBan plugin;

    public PhoBanCommand(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("boxphoban.use")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (plugin.instances().of(p.getUniqueId()) != null) {
                plugin.messages().sendWithButtons(p, "dungeon.menu-in-dungeon", new String[][]{{"dungeon.button-leave", "/phoban leave"}});
                return true;
            }
            plugin.menus().openMain(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    plugin.messages().send(p, "usage", "usage", "/" + label + " create <phó bản> [độ khó]");
                    return true;
                }
                DungeonTemplate t = plugin.templates().get(args[1]);
                if (t == null || !t.playable()) {
                    plugin.messages().send(p, "dungeon-not-found", "id", args[1]);
                    return true;
                }
                Difficulty d = args.length >= 3 ? t.difficulty(args[2]) : t.firstDifficulty();
                if (d == null) {
                    plugin.messages().send(p, "difficulty-not-found", "id", args.length >= 3 ? args[2] : "");
                    return true;
                }
                if (plugin.rooms().create(p, t, d) != null) new RoomMenu(plugin, p).open();
            }
            case "join" -> {
                if (args.length < 2) {
                    plugin.messages().send(p, "usage", "usage", "/" + label + " join <chủ phòng>");
                    return true;
                }
                Player leader = Bukkit.getPlayerExact(args[1]);
                Room r = leader == null ? null : plugin.rooms().room(leader.getUniqueId());
                if (r == null) {
                    plugin.messages().send(p, "room.gone");
                    return true;
                }
                if (plugin.rooms().join(p, r, false)) new RoomMenu(plugin, p).open();
            }
            case "invite" -> {
                if (args.length < 2) {
                    plugin.messages().send(p, "usage", "usage", "/" + label + " invite <người chơi>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) plugin.messages().send(p, "player-not-found", "player", args[1]);
                else plugin.rooms().invite(p, target);
            }
            case "accept" -> plugin.rooms().accept(p);
            case "deny" -> plugin.rooms().deny(p);
            case "kick", "leader" -> {
                if (args.length < 2) {
                    plugin.messages().send(p, "usage", "usage", "/" + label + " " + sub + " <người chơi>");
                    return true;
                }
                UUID target = member(p, args[1]);
                if (target == null) {
                    plugin.messages().send(p, "room.not-member");
                    return true;
                }
                if (sub.equals("kick")) plugin.rooms().kick(p, target);
                else plugin.rooms().transfer(p, target);
            }
            case "public" -> plugin.rooms().toggleOpen(p);
            case "start" -> plugin.rooms().start(p);
            case "room" -> new RoomMenu(plugin, p).open();
            case "leave" -> {
                Instance in = plugin.instances().of(p.getUniqueId());
                if (in != null) plugin.instances().leave(p, InstanceManager.LeaveReason.LEAVE);
                else plugin.rooms().leave(p);
            }
            case "rewards" -> new PendingMenu(plugin, p).open();
            default -> {
                for (String line : plugin.messages().list("help")) p.sendMessage(com.dex.boxphoban.util.Text.component(line));
            }
        }
        return true;
    }

    private UUID member(Player p, String name) {
        Room r = plugin.rooms().room(p.getUniqueId());
        if (r == null) return null;
        for (UUID u : r.members) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return u;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "create" -> {
                    for (DungeonTemplate t : plugin.templates().all()) if (t.playable() && t.id.startsWith(args[1].toLowerCase())) out.add(t.id);
                }
                case "invite", "join" -> {
                    for (Player o : Bukkit.getOnlinePlayers()) if (o.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(o.getName());
                }
                case "kick", "leader" -> {
                    if (sender instanceof Player p) {
                        Room r = plugin.rooms().room(p.getUniqueId());
                        if (r != null) for (UUID u : r.members) {
                            String n = Bukkit.getOfflinePlayer(u).getName();
                            if (n != null && !u.equals(p.getUniqueId())) out.add(n);
                        }
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && sub.equals("create")) {
            DungeonTemplate t = plugin.templates().get(args[1]);
            if (t != null) out.addAll(t.difficulties.keySet());
        }
        return out;
    }
}
