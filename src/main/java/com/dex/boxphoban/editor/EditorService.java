package com.dex.boxphoban.editor;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.player.SessionStore;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-game editor: editor slots, hotbar tools, particles, saving. */
public final class EditorService implements Listener {

    public static final class Session {
        public final DungeonTemplate template;
        public BlockPos pos1, pos2;
        public boolean show = true;

        Session(DungeonTemplate template) {
            this.template = template;
        }
    }

    private final BoxPhoBan plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public EditorService(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    public boolean editing(UUID u) {
        return sessions.containsKey(u);
    }

    public boolean editingTemplate(DungeonTemplate t) {
        for (Session s : sessions.values()) if (s.template == t) return true;
        return false;
    }

    public Session session(UUID u) {
        return sessions.get(u);
    }

    public BlockPos origin(DungeonTemplate t) {
        return plugin.dungeonWorld().editorOrigin(t.editorSlot);
    }

    public World world() {
        return plugin.dungeonWorld().world();
    }

    /** Relative position inside the editor slot of {@code t}, or null when outside it. */
    public BlockPos relative(DungeonTemplate t, Block b) {
        if (!plugin.dungeonWorld().is(b.getWorld())) return null;
        BlockPos rel = BlockPos.of(b).minus(origin(t));
        int half = plugin.dungeonWorld().maxHalfSize();
        if (Math.abs(rel.x()) > half || Math.abs(rel.z()) > half) return null;
        return rel;
    }

    public Point relativePoint(DungeonTemplate t, Location l) {
        if (!plugin.dungeonWorld().is(l.getWorld())) return null;
        Point p = Point.relative(l, origin(t));
        int half = plugin.dungeonWorld().maxHalfSize();
        if (Math.abs(p.x()) > half || Math.abs(p.z()) > half) return null;
        return p;
    }

    public void save(DungeonTemplate t, Player feedback) {
        try {
            plugin.templates().saveEditorData(t);
        } catch (IOException e) {
            plugin.getLogger().warning("Không lưu được nodes.yml của " + t.id + ": " + e.getMessage());
            if (feedback != null) plugin.messages().send(feedback, "editor.save-failed");
        }
    }

    // ------------------------------------------------------------------ enter / exit

    public void enter(Player p, DungeonTemplate t) {
        if (plugin.instances().of(p.getUniqueId()) != null || plugin.rooms().room(p.getUniqueId()) != null) {
            plugin.messages().send(p, "editor.busy");
            return;
        }
        World w = world();
        if (w == null) {
            plugin.messages().send(p, "dungeon.world-missing");
            return;
        }
        Session old = sessions.get(p.getUniqueId());
        if (old == null && !plugin.sessions().save(p, SessionStore.Kind.EDIT)) return;
        sessions.put(p.getUniqueId(), new Session(t));
        BlockPos o = origin(t);
        Block floor = w.getBlockAt(o.x(), o.y() - 1, o.z());
        if (floor.getType().isAir() && t.bounds == null) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) w.getBlockAt(o.x() + x, o.y() - 1, o.z() + z).setType(Material.STONE);
        }
        Location to = t.start != null ? t.start.toLocation(w, o) : new Location(w, o.x() + 0.5, o.y(), o.z() + 0.5);
        plugin.sessions().teleport(p, to);
        p.setGameMode(GameMode.CREATIVE);
        giveTools(p);
        plugin.messages().send(p, "editor.entered", "dungeon", t.display, "id", t.id);
    }

    public void exit(Player p, boolean teleport) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) {
            plugin.messages().send(p, "editor.not-editing");
            return;
        }
        Tags.strip(p, Tags.TOOL);
        save(s.template, p);
        plugin.sessions().restore(p, teleport);
        plugin.messages().send(p, "editor.exited");
    }

    /** Quit / world change: forget the session, the snapshot restores the player later. */
    public void drop(UUID u) {
        Session s = sessions.remove(u);
        if (s != null) save(s.template, null);
    }

    private void giveTools(Player p) {
        Tags.strip(p, Tags.TOOL);
        String[] ids = {"node", "region", "point", "view"};
        Material[] mats = {Material.FEATHER, Material.GOLDEN_AXE, Material.COMPASS, Material.SPIDER_EYE};
        for (int i = 0; i < ids.length; i++) {
            ItemStack it = Items.make(mats[i], plugin.gui().raw("editor.tools." + ids[i] + ".name"),
                    plugin.gui().list("editor.tools." + ids[i] + ".lore"));
            Tags.set(it, Tags.TOOL, ids[i]);
            p.getInventory().setItem(i, it);
        }
    }

    // ------------------------------------------------------------------ tools

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String tool = Tags.get(e.getItem(), Tags.TOOL);
        if (tool == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        if (s == null) {
            Tags.strip(p, Tags.TOOL);
            return;
        }
        DungeonTemplate t = s.template;
        Block b = e.getClickedBlock();
        boolean right = e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR;
        switch (tool) {
            case "node" -> {
                if (b == null || !right) return;
                BlockPos rel = relative(t, b);
                if (rel == null) {
                    plugin.messages().send(p, "editor.outside");
                    return;
                }
                new NodeMenu(plugin, p, t, rel).open();
            }
            case "region" -> {
                if (b == null) return;
                BlockPos rel = relative(t, b);
                if (rel == null) {
                    plugin.messages().send(p, "editor.outside");
                    return;
                }
                if (right) s.pos2 = rel;
                else s.pos1 = rel;
                String size = "";
                if (s.pos1 != null && s.pos2 != null) {
                    Box box = Box.of(s.pos1, s.pos2);
                    size = box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ();
                }
                plugin.messages().send(p, right ? "editor.pos2" : "editor.pos1", "pos", rel.toString(), "size", size);
            }
            case "point" -> {
                if (!right) return;
                new PointMenu(plugin, p, t).open();
            }
            case "view" -> {
                if (!right) return;
                s.show = !s.show;
                plugin.messages().send(p, s.show ? "editor.view-on" : "editor.view-off");
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (Tags.get(e.getItemDrop().getItemStack(), Tags.TOOL) != null) e.getItemDrop().remove();
    }

    // ------------------------------------------------------------------ particles (every 10 ticks)

    public void tickParticles() {
        World w = world();
        if (w == null) return;
        for (Map.Entry<UUID, Session> en : sessions.entrySet()) {
            Session s = en.getValue();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !s.show || !p.getWorld().equals(w)) continue;
            DungeonTemplate t = s.template;
            BlockPos o = origin(t);
            Location eye = p.getLocation();
            for (Node n : t.nodes.values()) {
                Location l = new Location(w, o.x() + n.pos.x() + 0.5, o.y() + n.pos.y() + 1.2, o.z() + n.pos.z() + 0.5);
                if (l.distanceSquared(eye) > 48 * 48) continue;
                p.spawnParticle(Particle.DUST, l, 4, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.fromRGB(0x5EEAD4), 1.2f));
            }
            if (t.start != null) {
                Location l = t.start.toLocation(w, o);
                if (l.distanceSquared(eye) < 48 * 48) p.spawnParticle(Particle.HAPPY_VILLAGER, l.add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0);
            }
            for (Point pt : t.points.values()) {
                Location l = pt.toLocation(w, o);
                if (l.distanceSquared(eye) < 48 * 48) {
                    p.spawnParticle(Particle.DUST, l.add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.fromRGB(0xFFD166), 1f));
                }
            }
            for (Box r : t.regions.values()) outline(p, w, r.shift(o), Color.fromRGB(0x7FDBFF), eye);
            if (t.bounds != null) outline(p, w, t.bounds.shift(o), Color.fromRGB(0xFF8A8A), eye);
            if (s.pos1 != null && s.pos2 != null) outline(p, w, Box.of(s.pos1, s.pos2).shift(o), Color.WHITE, eye);
        }
    }

    private static void outline(Player p, World w, Box b, Color c, Location eye) {
        double x0 = b.min().x(), y0 = b.min().y(), z0 = b.min().z();
        double x1 = b.max().x() + 1, y1 = b.max().y() + 1, z1 = b.max().z() + 1;
        double step = Math.max(1, Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0)) / 24.0);
        Particle.DustOptions dust = new Particle.DustOptions(c, 0.8f);
        double[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (double[] e : edges) {
            double len = Math.sqrt(Math.pow(e[3] - e[0], 2) + Math.pow(e[4] - e[1], 2) + Math.pow(e[5] - e[2], 2));
            int n = (int) Math.max(1, len / step);
            for (int i = 0; i <= n; i++) {
                double f = (double) i / n;
                Location l = new Location(w, e[0] + (e[3] - e[0]) * f, e[1] + (e[4] - e[1]) * f, e[2] + (e[5] - e[2]) * f);
                if (l.distanceSquared(eye) < 64 * 64) p.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    public void shutdown() {
        for (UUID u : new java.util.ArrayList<>(sessions.keySet())) {
            Player p = Bukkit.getPlayer(u);
            Session s = sessions.remove(u);
            if (s != null) save(s.template, null);
            if (p != null) {
                Tags.strip(p, Tags.TOOL);
                plugin.sessions().restore(p, true);
            }
        }
    }
}
