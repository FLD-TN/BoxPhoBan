package com.dex.boxphoban.engine;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Moving walls (portcullis down, rising up, two halves sliding) and room locks.
 *
 * <p>A wall is a region built in its CLOSED position in the editor. When an instance starts, its blocks are
 * remembered and (unless the region is marked closed-at-start) removed, so the path is open. Closing puts
 * invisible barriers in place at once (nobody slips through), animates BlockDisplays into place, then swaps in
 * the real blocks. Opening removes the blocks at once and animates the displays away.
 */
public final class Walls {

    public enum Style { DOWN, UP, SIDE }

    public static final int MAX_BLOCKS = 4096;
    public static final String DISPLAY_TAG = "boxphoban_wall";

    /** Runtime state of one wall region in one instance. */
    public static final class Wall {
        final String name;
        final Box box;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockData> data = new ArrayList<>();
        boolean closed;
        int task = -1;
        final List<BlockDisplay> displays = new ArrayList<>();
        final List<Vector3f> offsets = new ArrayList<>();

        Wall(String name, Box box) {
            this.name = name;
            this.box = box;
        }
    }

    /** An active room lock (Khoá phòng). */
    public static final class Lock {
        final String room;
        final List<String> entrances;
        final List<String> exits;
        final Style style;
        final int ticks;
        final String sound;
        final Location pushTo;
        boolean entranceClosed;
        boolean sealedOnce;

        public Lock(String room, List<String> entrances, List<String> exits, Style style, int ticks, String sound, Location pushTo) {
            this.room = room;
            this.entrances = entrances;
            this.exits = exits;
            this.style = style;
            this.ticks = ticks;
            this.sound = sound;
            this.pushTo = pushTo;
        }
    }

    private final BoxPhoBan plugin;

    public Walls(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    public static Style style(String s) {
        if (s == null) return Style.DOWN;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "up", "len", "lên" -> Style.UP;
            case "side", "ngang" -> Style.SIDE;
            default -> Style.DOWN;
        };
    }

    public static List<String> names(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /**
     * Where a block of the wall sits when the wall is fully open, relative to its closed position.
     * DOWN = hidden above (drops like a portcullis), UP = hidden below (rises from the floor),
     * SIDE = the two halves along the longer horizontal axis are pushed apart.
     */
    public static Vector3f openOffset(Style style, Box box, BlockPos p) {
        int h = box.sizeY();
        return switch (style) {
            case DOWN -> new Vector3f(0, h, 0);
            case UP -> new Vector3f(0, -h, 0);
            case SIDE -> {
                boolean alongX = box.sizeX() >= box.sizeZ();
                int size = alongX ? box.sizeX() : box.sizeZ();
                int min = alongX ? box.min().x() : box.min().z();
                int coord = alongX ? p.x() : p.z();
                float half = (float) Math.ceil(size / 2.0);
                boolean firstHalf = (coord - min) < size / 2.0;
                float d = firstHalf ? -half : half;
                yield alongX ? new Vector3f(d, 0, 0) : new Vector3f(0, 0, d);
            }
        };
    }

    // ------------------------------------------------------------------ setup

    /** Region names used as moving walls by the dungeon's nodes. */
    public static Set<String> wallRegions(Iterable<Node> nodes) {
        Set<String> out = new LinkedHashSet<>();
        for (Node n : nodes) {
            for (Element a : n.actions) {
                ActionType t = ActionType.parse(a.type);
                if (t == ActionType.WALL_CLOSE || t == ActionType.WALL_OPEN) out.addAll(names(a.s("region", "")));
                if (t == ActionType.ROOM_LOCK) {
                    out.addAll(names(a.s("entrances", "")));
                    out.addAll(names(a.s("exits", "")));
                }
            }
        }
        return out;
    }

    /** Right after the paste: remember every wall and open the ones that start open. */
    public void prepare(Instance in) {
        for (String name : wallRegions(in.template.nodes.values())) {
            Box rel = in.template.regions.get(name);
            if (rel == null) {
                plugin.getLogger().warning("[" + in.template.id + "] tường '" + name + "' chưa có vùng");
                continue;
            }
            Box abs = in.absolute(rel);
            if (abs.volume() > MAX_BLOCKS) {
                plugin.getLogger().warning("[" + in.template.id + "] tường '" + name + "' quá lớn (" + abs.volume() + " block, tối đa " + MAX_BLOCKS + ")");
                continue;
            }
            Wall w = new Wall(name, abs);
            for (int x = abs.min().x(); x <= abs.max().x(); x++)
                for (int y = abs.min().y(); y <= abs.max().y(); y++)
                    for (int z = abs.min().z(); z <= abs.max().z(); z++) {
                        Block b = in.world.getBlockAt(x, y, z);
                        if (b.getType().isAir()) continue;
                        w.positions.add(new BlockPos(x, y, z));
                        w.data.add(b.getBlockData());
                    }
            boolean startClosed = in.template.wallsClosedAtStart.contains(name);
            if (!startClosed) for (BlockPos p : w.positions) p.block(in.world).setType(Material.AIR, false);
            w.closed = startClosed;
            in.walls.put(name, w);
        }
    }

    // ------------------------------------------------------------------ move

    public void move(Instance in, String name, boolean close, Style style, int ticks, String sound, Location pushTo) {
        Wall w = in.walls.get(name);
        if (w == null) {
            plugin.getLogger().warning("[" + in.template.id + "] không có tường '" + name + "' (vùng chưa tạo hoặc không có block)");
            return;
        }
        finish(in, w);
        if (w.closed == close) return;
        w.closed = close;
        ticks = Math.max(1, ticks);
        Location center = new Location(in.world, (w.box.min().x() + w.box.max().x() + 1) / 2.0,
                (w.box.min().y() + w.box.max().y() + 1) / 2.0, (w.box.min().z() + w.box.max().z() + 1) / 2.0);
        if (sound != null && !sound.isEmpty()) {
            for (Player v : in.viewers()) v.playSound(center, sound.toLowerCase(Locale.ROOT), 1f, close ? 0.7f : 0.9f);
        }
        if (close) {
            // barriers now: nobody can slip through while the wall is still moving
            for (BlockPos p : w.positions) p.block(in.world).setType(Material.BARRIER, false);
            push(in, w, pushTo);
            spawnDisplays(in, w, style, true);
        } else {
            for (BlockPos p : w.positions) p.block(in.world).setType(Material.AIR, false);
            spawnDisplays(in, w, style, false);
        }
        final int t = ticks;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < w.displays.size(); i++) {
                BlockDisplay d = w.displays.get(i);
                if (!d.isValid()) continue;
                Transformation tr = d.getTransformation();
                Vector3f target = close ? new Vector3f() : new Vector3f(w.offsets.get(i));
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(t);
                d.setTransformation(new Transformation(target, tr.getLeftRotation(), tr.getScale(), tr.getRightRotation()));
            }
        }, 2L);
        w.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            w.task = -1;
            finish(in, w);
            if (close) for (Player v : in.viewers()) v.playSound(center, "block.anvil.land", 0.4f, 0.6f);
        }, t + 3L).getTaskId();
    }

    private void spawnDisplays(Instance in, Wall w, Style style, boolean closing) {
        for (int i = 0; i < w.positions.size(); i++) {
            BlockPos p = w.positions.get(i);
            Vector3f off = openOffset(style, w.box, p);
            Location at = new Location(in.world, p.x(), p.y(), p.z());
            BlockData data = w.data.get(i);
            BlockDisplay d = in.world.spawn(at, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.addScoreboardTag(DISPLAY_TAG);
                e.setBlock(data);
                e.setBrightness(new Display.Brightness(Math.max(8, at.getBlock().getLightFromBlocks()), 15));
                e.setTransformation(new Transformation(closing ? new Vector3f(off) : new Vector3f(),
                        new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
            });
            w.displays.add(d);
            w.offsets.add(off);
        }
    }

    /** Ends any running animation: real blocks in place (closed) or air (open), displays removed. */
    private void finish(Instance in, Wall w) {
        if (w.task != -1) {
            Bukkit.getScheduler().cancelTask(w.task);
            w.task = -1;
        }
        for (BlockDisplay d : w.displays) d.remove();
        w.displays.clear();
        w.offsets.clear();
        for (int i = 0; i < w.positions.size(); i++) {
            Block b = w.positions.get(i).block(in.world);
            if (w.closed) b.setBlockData(w.data.get(i), false);
            else if (b.getType() == Material.BARRIER || b.getType() == w.data.get(i).getMaterial()) b.setType(Material.AIR, false);
        }
    }

    /** Players standing inside a closing wall are moved to {@code to}. */
    private void push(Instance in, Wall w, Location to) {
        if (to == null) return;
        Box grown = w.box.grow(1);
        for (Player p : in.onlineMembers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Location l = p.getLocation();
            if (!grown.contains(l)) continue;
            int bx = l.getBlockX(), by = l.getBlockY(), bz = l.getBlockZ();
            boolean inside = false;
            for (BlockPos q : w.positions) {
                if (q.x() == bx && q.z() == bz && (q.y() == by || q.y() == by + 1)) {
                    inside = true;
                    break;
                }
            }
            if (inside) plugin.sessions().teleport(p, to);
        }
    }

    // ------------------------------------------------------------------ room locks

    public void lock(Instance in, Lock lock) {
        for (Lock l : in.locks) if (l.room.equals(lock.room)) return;
        in.locks.add(lock);
        // a lock opens on the next "stage cleared": re-arm it even if the stage was cleared before
        in.stageCleared = false;
        in.stageSpawned = !in.stageMobs.isEmpty();
        for (String exit : lock.exits) move(in, exit, true, lock.style, lock.ticks, lock.sound, lock.pushTo);
        for (Player p : in.onlineMembers()) plugin.messages().send(p, "dungeon.room-locked");
    }

    /** Every 10 ticks: close the entrances once every living member is inside, re-open them if nobody is. */
    public void tick(Instance in) {
        if (in.locks.isEmpty()) return;
        List<Player> alive = in.alivePlayers();
        for (Lock lock : new ArrayList<>(in.locks)) {
            Box rel = in.template.regions.get(lock.room);
            if (rel == null) continue;
            Box room = in.absolute(rel);
            int inside = 0;
            for (Player p : alive) if (room.contains(p.getLocation())) inside++;
            boolean all = !alive.isEmpty() && inside == alive.size();
            if (all && !lock.entranceClosed) {
                lock.entranceClosed = true;
                for (String e : lock.entrances) move(in, e, true, lock.style, lock.ticks, lock.sound, lock.pushTo);
                for (Player p : in.onlineMembers()) plugin.messages().send(p, "dungeon.room-sealed");
                if (!lock.sealedOnce) {
                    lock.sealedOnce = true;
                    plugin.engine().onRoomSealed(in, lock.room);
                }
            } else if (inside == 0 && lock.entranceClosed) {
                lock.entranceClosed = false;
                for (String e : lock.entrances) move(in, e, false, lock.style, lock.ticks, lock.sound, null);
                for (Player p : in.onlineMembers()) plugin.messages().send(p, "dungeon.room-reopened");
            }
        }
    }

    public void unlock(Instance in, String room) {
        for (Lock lock : new ArrayList<>(in.locks)) {
            if (room != null && !lock.room.equals(room)) continue;
            in.locks.remove(lock);
            for (String e : lock.entrances) move(in, e, false, lock.style, lock.ticks, lock.sound, null);
            for (String e : lock.exits) move(in, e, false, lock.style, lock.ticks, lock.sound, null);
        }
    }

    /** Instance end / cleanup: stop animations and remove displays. */
    public void clear(Instance in) {
        for (Wall w : in.walls.values()) {
            if (w.task != -1) Bukkit.getScheduler().cancelTask(w.task);
            w.task = -1;
            for (BlockDisplay d : w.displays) d.remove();
            w.displays.clear();
            w.offsets.clear();
        }
        in.locks.clear();
    }
}
