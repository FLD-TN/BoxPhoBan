package com.dex.boxphoban.template;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.util.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Loads and saves every dungeon in {@code plugins/BoxPhoBan/dungeons/<id>/}. */
public final class TemplateManager {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]{2,32}");

    private final BoxPhoBan plugin;
    private final File root;
    private final Map<String, DungeonTemplate> templates = new LinkedHashMap<>();

    public TemplateManager(BoxPhoBan plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "dungeons");
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public void loadAll() {
        templates.clear();
        if (!root.isDirectory() && !root.mkdirs()) return;
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return;
        java.util.Arrays.sort(dirs);
        for (File dir : dirs) {
            String id = dir.getName().toLowerCase(Locale.ROOT);
            if (!validId(id)) continue;
            DungeonTemplate t = new DungeonTemplate(id, dir);
            try {
                load(t);
                templates.put(id, t);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Không đọc được phó bản " + id, e);
            }
        }
        plugin.getLogger().info("Đã tải " + templates.size() + " phó bản");
    }

    private void load(DungeonTemplate t) {
        t.loadSettings(YamlConfiguration.loadConfiguration(t.settingsFile()));
        if (t.nodesFile().isFile()) t.loadEditorData(YamlConfiguration.loadConfiguration(t.nodesFile()));
    }

    /** Re-reads dungeon.yml of every dungeon (editor data stays in memory). */
    public void reloadSettings() {
        for (DungeonTemplate t : templates.values()) {
            t.loadSettings(YamlConfiguration.loadConfiguration(t.settingsFile()));
        }
    }

    /**
     * Re-reads nodes.yml of every dungeon that is neither being edited nor running (those are skipped and returned),
     * and loads dungeon folders that were added on disk.
     */
    public java.util.List<String> reloadEditorData(java.util.function.Predicate<DungeonTemplate> busy) {
        java.util.List<String> skipped = new java.util.ArrayList<>();
        for (DungeonTemplate t : templates.values()) {
            if (busy.test(t)) {
                skipped.add(t.id);
                continue;
            }
            if (t.nodesFile().isFile()) t.loadEditorData(YamlConfiguration.loadConfiguration(t.nodesFile()));
        }
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String id = dir.getName().toLowerCase(Locale.ROOT);
                if (!validId(id) || templates.containsKey(id) || !new File(dir, "dungeon.yml").isFile()) continue;
                DungeonTemplate t = new DungeonTemplate(id, dir);
                try {
                    load(t);
                    templates.put(id, t);
                    plugin.getLogger().info("Đã tải phó bản mới " + id);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Không đọc được phó bản " + id, e);
                }
            }
        }
        return skipped;
    }

    public DungeonTemplate get(String id) {
        return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<DungeonTemplate> all() {
        return templates.values();
    }

    public DungeonTemplate create(String id) throws IOException {
        File dir = new File(root, id);
        if (!dir.mkdirs() && !dir.isDirectory()) throw new IOException("mkdir " + dir);
        DungeonTemplate t = new DungeonTemplate(id, dir);
        try (InputStream in = plugin.getResource("dungeon-template.yml")) {
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Files.writeString(t.settingsFile().toPath(), text.replace("%id%", id), StandardCharsets.UTF_8);
        }
        t.loadSettings(YamlConfiguration.loadConfiguration(t.settingsFile()));
        t.editorSlot = nextEditorSlot();
        saveEditorData(t);
        templates.put(id, t);
        return t;
    }

    private int nextEditorSlot() {
        int max = -1;
        for (DungeonTemplate t : templates.values()) max = Math.max(max, t.editorSlot);
        return max + 1;
    }

    public void saveEditorData(DungeonTemplate t) throws IOException {
        AtomicYaml.save(t.saveEditorData(), t.nodesFile());
    }

    /** Moves the folder to dungeons/.deleted (nothing is really removed). */
    public boolean delete(DungeonTemplate t) {
        File trash = new File(root, ".deleted");
        if (!trash.isDirectory() && !trash.mkdirs()) return false;
        File target = new File(trash, t.id + "-" + System.currentTimeMillis());
        if (!t.dir.renameTo(target)) return false;
        templates.remove(t.id);
        return true;
    }
}
