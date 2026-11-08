package com.dex.boxphoban.world;

import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.function.Consumer;

/** Schematic save / paste / clear through the WorldEdit API (FAWE runs it off the main thread). */
public final class MapService {

    /** Selection of a player: world + absolute box. */
    public record Selection(World world, Box box) {
    }

    private final Plugin plugin;

    public MapService(Plugin plugin) {
        this.plugin = plugin;
    }

    private static BlockVector3 bv(BlockPos p) {
        return BlockVector3.at(p.x(), p.y(), p.z());
    }

    private static CuboidRegion region(World w, Box b) {
        return new CuboidRegion(BukkitAdapter.adapt(w), bv(b.min()), bv(b.max()));
    }

    private static EditSession session(World w) {
        return WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(w)).limitUnlimited().build();
    }

    private void async(Runnable work, Consumer<Throwable> done) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Throwable err = null;
            try {
                work.run();
            } catch (Throwable t) {
                err = t;
            }
            Throwable fe = err;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> done.accept(fe));
        });
    }

    private static void closeQuietly(Object o) {
        if (o instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception ignored) {
                // nothing to do
            }
        }
    }

    /** Copies {@code absolute} into a schematic whose origin is {@code origin}. */
    public void save(World w, Box absolute, BlockPos origin, File file, Consumer<Throwable> done) {
        async(() -> {
            CuboidRegion region = region(w, absolute);
            BlockArrayClipboard clip = new BlockArrayClipboard(region);
            try {
                clip.setOrigin(bv(origin));
                try (EditSession es = session(w)) {
                    ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clip, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    Operations.complete(copy);
                }
                File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
                try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(tmp))) {
                    writer.write(clip);
                }
                if (file.exists() && !file.delete()) throw new IllegalStateException("cannot replace " + file);
                if (!tmp.renameTo(file)) throw new IllegalStateException("cannot move " + tmp);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(clip);
            }
        }, done);
    }

    /**
     * Pastes a schematic so that its origin lands on {@code to}. Air is skipped: a slot is only handed out again
     * after it was cleared to air (SlotPool keeps failed slots busy), so only the real blocks need writing.
     */
    public void paste(File file, World w, BlockPos to, Consumer<Throwable> done) {
        async(() -> {
            ClipboardFormat fmt = ClipboardFormats.findByFile(file);
            if (fmt == null) throw new IllegalStateException("unknown schematic format " + file);
            Clipboard clip = null;
            try (ClipboardReader reader = fmt.getReader(new FileInputStream(file))) {
                clip = reader.read();
                try (EditSession es = session(w)) {
                    Operation op = new ClipboardHolder(clip).createPaste(es).to(bv(to))
                            .ignoreAirBlocks(true).copyEntities(false).build();
                    Operations.complete(op);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(clip);
            }
        }, done);
    }

    /** Sets every block of {@code absolute} to air. */
    public void clear(World w, Box absolute, Consumer<Throwable> done) {
        async(() -> {
            try (EditSession es = session(w)) {
                es.setBlocks((Region) region(w, absolute), BlockTypes.AIR.getDefaultState());
            }
        }, done);
    }

    /** Copies a box from one place to another (import of a WorldEdit selection). */
    public void copy(World from, Box source, World to, BlockPos targetMin, Consumer<Throwable> done) {
        async(() -> {
            CuboidRegion region = region(from, source);
            BlockArrayClipboard clip = new BlockArrayClipboard(region);
            try {
                clip.setOrigin(region.getMinimumPoint());
                try (EditSession es = session(from)) {
                    ForwardExtentCopy copy = new ForwardExtentCopy(es, region, clip, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    Operations.complete(copy);
                }
                try (EditSession es = session(to)) {
                    Operation op = new ClipboardHolder(clip).createPaste(es).to(bv(targetMin))
                            .ignoreAirBlocks(false).copyEntities(false).build();
                    Operations.complete(op);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                closeQuietly(clip);
            }
        }, done);
    }

    /** Current cuboid WorldEdit selection of a player, or null. */
    public Selection selection(Player p) {
        try {
            LocalSession s = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(p));
            com.sk89q.worldedit.world.World w = s.getSelectionWorld();
            if (w == null) return null;
            Region r = s.getSelection(w);
            World bw = BukkitAdapter.adapt(w);
            BlockVector3 a = r.getMinimumPoint(), b = r.getMaximumPoint();
            return new Selection(bw, Box.of(new BlockPos(a.x(), a.y(), a.z()), new BlockPos(b.x(), b.y(), b.z())));
        } catch (IncompleteRegionException e) {
            return null;
        }
    }
}
