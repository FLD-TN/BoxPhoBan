package com.dex.boxphoban.room;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Waiting rooms: create, join, invite, kick, transfer leader, start. No queue: first come, first served. */
public final class RoomManager {

    private final BoxPhoBan plugin;
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, Room> byPlayer = new HashMap<>();
    private int nextId = 1;

    public RoomManager(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    public Room room(UUID u) {
        return byPlayer.get(u);
    }

    public Collection<Room> all() {
        return rooms.values();
    }

    public List<Room> rooms(DungeonTemplate t) {
        List<Room> out = new ArrayList<>();
        for (Room r : rooms.values()) if (r.template == t) out.add(r);
        return out;
    }

    private static String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return op.getName() == null ? u.toString().substring(0, 8) : op.getName();
    }

    private void broadcast(Room r, String key, String... kv) {
        for (UUID u : r.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) msg().send(p, key, kv);
        }
    }

    private boolean busy(Player p) {
        if (plugin.instances().of(p.getUniqueId()) != null) {
            msg().send(p, "room.in-dungeon");
            return true;
        }
        if (plugin.editor().editing(p.getUniqueId())) {
            msg().send(p, "room.in-editor");
            return true;
        }
        return false;
    }

    private boolean denied(Player p, DungeonTemplate t, boolean leader) {
        EntryService.Denial d = plugin.entry().check(p, t, leader);
        if (d == null) return false;
        msg().send(p, d.key(), d.kv());
        return true;
    }

    // ------------------------------------------------------------------ actions

    public Room create(Player p, DungeonTemplate t, Difficulty d) {
        if (byPlayer.containsKey(p.getUniqueId())) {
            msg().send(p, "room.already-in-room");
            return null;
        }
        if (busy(p)) return null;
        if (!t.playable()) {
            msg().send(p, "room.not-playable");
            return null;
        }
        if (denied(p, t, true)) return null;
        Room r = new Room(nextId++, t, d, p.getUniqueId());
        rooms.put(r.id, r);
        byPlayer.put(p.getUniqueId(), r);
        msg().send(p, "room.created", "dungeon", t.display, "difficulty", d.display());
        plugin.menus().refreshRooms();
        return r;
    }

    public boolean join(Player p, Room r, boolean invited) {
        if (byPlayer.get(p.getUniqueId()) == r) {
            msg().send(p, "room.already-in-this-room");
            return false;
        }
        if (byPlayer.containsKey(p.getUniqueId())) {
            msg().send(p, "room.already-in-room");
            return false;
        }
        if (busy(p)) return false;
        if (!rooms.containsKey(r.id) || r.starting) {
            msg().send(p, "room.gone");
            return false;
        }
        if (r.full()) {
            msg().send(p, "room.full", "max", String.valueOf(r.template.maxPlayers));
            return false;
        }
        if (!r.open && !invited && !r.invites.containsKey(p.getUniqueId())) {
            msg().send(p, "room.private");
            return false;
        }
        if (denied(p, r.template, false)) return false;
        r.invites.remove(p.getUniqueId());
        r.members.add(p.getUniqueId());
        r.touch();
        byPlayer.put(p.getUniqueId(), r);
        broadcast(r, "room.joined", "player", p.getName(), "count", String.valueOf(r.members.size()),
                "max", String.valueOf(r.template.maxPlayers));
        plugin.menus().refreshRooms();
        return true;
    }

    private Room leaderRoom(Player p) {
        Room r = byPlayer.get(p.getUniqueId());
        if (r == null) {
            msg().send(p, "room.not-in-room");
            return null;
        }
        if (!r.leader.equals(p.getUniqueId())) {
            msg().send(p, "room.not-leader");
            return null;
        }
        if (r.starting) {
            msg().send(p, "room.starting");
            return null;
        }
        return r;
    }

    public void invite(Player leader, Player target) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        if (target.equals(leader)) {
            msg().send(leader, "room.invite-self");
            return;
        }
        if (byPlayer.containsKey(target.getUniqueId()) || plugin.instances().of(target.getUniqueId()) != null) {
            msg().send(leader, "room.target-busy", "player", target.getName());
            return;
        }
        if (r.full()) {
            msg().send(leader, "room.full", "max", String.valueOf(r.template.maxPlayers));
            return;
        }
        r.invites.put(target.getUniqueId(), System.currentTimeMillis() + plugin.settings().inviteTimeout * 1000L);
        r.touch();
        msg().send(leader, "room.invite-sent", "player", target.getName());
        msg().sendWithButtons(target, "room.invite-received", new String[][]{
                {"room.button-accept", "/phoban accept"}, {"room.button-deny", "/phoban deny"}},
                "player", leader.getName(), "dungeon", r.template.display, "difficulty", r.difficulty.display(),
                "seconds", String.valueOf(plugin.settings().inviteTimeout));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
    }

    private Room latestInvite(UUID u) {
        Room best = null;
        long bestAt = 0;
        for (Room r : rooms.values()) {
            Long at = r.invites.get(u);
            if (at != null && at > bestAt) {
                best = r;
                bestAt = at;
            }
        }
        return best;
    }

    public void accept(Player p) {
        Room r = latestInvite(p.getUniqueId());
        if (r == null) {
            msg().send(p, "room.no-invite");
            return;
        }
        join(p, r, true);
    }

    public void deny(Player p) {
        Room r = latestInvite(p.getUniqueId());
        if (r == null) {
            msg().send(p, "room.no-invite");
            return;
        }
        r.invites.remove(p.getUniqueId());
        msg().send(p, "room.invite-denied-self");
        Player leader = Bukkit.getPlayer(r.leader);
        if (leader != null) msg().send(leader, "room.invite-denied", "player", p.getName());
    }

    public void kick(Player leader, UUID target) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        if (target.equals(leader.getUniqueId()) || !r.members.contains(target)) {
            msg().send(leader, "room.not-member");
            return;
        }
        r.members.remove(target);
        byPlayer.remove(target);
        r.touch();
        Player t = Bukkit.getPlayer(target);
        if (t != null) {
            msg().send(t, "room.kicked-self");
            plugin.menus().closeRoomMenu(t);
        }
        broadcast(r, "room.kicked", "player", name(target));
        plugin.menus().refreshRooms();
    }

    public void transfer(Player leader, UUID target) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        if (target.equals(leader.getUniqueId()) || !r.members.contains(target)) {
            msg().send(leader, "room.not-member");
            return;
        }
        r.leader = target;
        r.touch();
        broadcast(r, "room.leader-changed", "player", name(target));
        plugin.menus().refreshRooms();
    }

    public void toggleOpen(Player leader) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        r.open = !r.open;
        r.touch();
        broadcast(r, r.open ? "room.now-public" : "room.now-private");
        plugin.menus().refreshRooms();
    }

    public void setDifficulty(Player leader, Difficulty d) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        r.difficulty = d;
        r.touch();
        broadcast(r, "room.difficulty-changed", "difficulty", d.display());
        plugin.menus().refreshRooms();
    }

    public void leave(Player p) {
        Room r = byPlayer.get(p.getUniqueId());
        if (r == null) {
            msg().send(p, "room.not-in-room");
            return;
        }
        if (r.starting) {
            msg().send(p, "room.starting");
            return;
        }
        remove(p.getUniqueId(), r);
        msg().send(p, "room.left-self");
    }

    /** Removes a member (leave / quit); passes the leader role on or disbands the empty room. */
    private void remove(UUID u, Room r) {
        r.members.remove(u);
        byPlayer.remove(u);
        if (r.members.isEmpty()) {
            rooms.remove(r.id);
        } else {
            broadcast(r, "room.left", "player", name(u));
            if (r.leader.equals(u)) {
                r.leader = r.members.iterator().next();
                broadcast(r, "room.leader-changed", "player", name(r.leader));
            }
        }
        plugin.menus().refreshRooms();
    }

    public void onQuit(UUID u) {
        Room r = byPlayer.get(u);
        if (r != null && !r.starting) remove(u, r);
    }

    private void disband(Room r, String key) {
        rooms.remove(r.id);
        for (UUID u : r.members) {
            byPlayer.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                msg().send(p, key);
                plugin.menus().closeRoomMenu(p);
            }
        }
        plugin.menus().refreshRooms();
    }

    public void start(Player leader) {
        Room r = leaderRoom(leader);
        if (r == null) return;
        DungeonTemplate t = r.template;
        if (!t.playable()) {
            msg().send(leader, "room.not-playable");
            return;
        }
        if (r.members.size() < t.minPlayers) {
            msg().send(leader, "room.not-enough", "min", String.valueOf(t.minPlayers));
            return;
        }
        List<Player> players = new ArrayList<>();
        for (UUID u : r.members) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) {
                msg().send(leader, "room.member-offline", "player", name(u));
                return;
            }
            players.add(p);
        }
        for (Player p : players) {
            EntryService.Denial d = plugin.entry().check(p, t, p.equals(leader));
            if (d != null) {
                msg().send(p, d.key(), d.kv());
                if (!p.equals(leader)) msg().send(leader, "room.member-denied", "player", p.getName());
                return;
            }
        }
        String full = plugin.instances().capacityProblem(t);
        if (full != null) {
            msg().send(leader, full, "seconds", "30");
            return;
        }
        List<Runnable> refunds = new ArrayList<>();
        for (Player p : players) {
            Runnable refund = plugin.entry().pay(p, t, p.equals(leader));
            if (refund == null) {
                refunds.forEach(Runnable::run);
                msg().send(leader, "room.pay-failed", "player", p.getName());
                return;
            }
            refunds.add(refund);
        }
        r.starting = true;
        rooms.remove(r.id);
        for (UUID u : r.members) byPlayer.remove(u);
        for (Player p : players) plugin.menus().closeRoomMenu(p);
        plugin.menus().refreshRooms();
        plugin.instances().start(t, r.difficulty, players, refunds, false);
    }

    /** /pba test: a solo run that skips tickets, fees and cooldowns. */
    public void startTest(Player admin, DungeonTemplate t, Difficulty d) {
        if (byPlayer.containsKey(admin.getUniqueId())) {
            msg().send(admin, "room.already-in-room");
            return;
        }
        if (busy(admin)) return;
        if (t.bounds == null || t.start == null || !t.schematicFile().isFile() || d == null) {
            msg().send(admin, "admin.test-not-ready");
            return;
        }
        String full = plugin.instances().capacityProblem(t);
        if (full != null) {
            msg().send(admin, full, "seconds", "30");
            return;
        }
        plugin.instances().start(t, d, List.of(admin), List.of(), true);
    }

    /** Every second: idle rooms and expired invites. */
    public void tick() {
        long now = System.currentTimeMillis();
        long idle = plugin.settings().roomIdleTimeout * 1000L;
        Iterator<Room> it = new ArrayList<>(rooms.values()).iterator();
        while (it.hasNext()) {
            Room r = it.next();
            r.invites.values().removeIf(at -> at < now);
            if (!r.starting && now - r.lastActivity > idle) disband(r, "room.idle-disbanded");
        }
    }

    public void shutdown() {
        rooms.clear();
        byPlayer.clear();
    }
}
