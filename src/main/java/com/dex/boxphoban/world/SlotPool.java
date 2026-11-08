package com.dex.boxphoban.world;

import com.dex.boxphoban.util.AtomicYaml;
import com.dex.boxphoban.util.Box;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Instance slots. A slot is "dirty" from the moment blocks may be pasted into it until it is cleared again,
 * and dirty slots are written to {@code data/slots.yml} so a crash never leaves a used slot behind.
 */
public final class SlotPool {

    private final File file;
    private final Logger log;
    /** slot -> absolute box that may contain blocks. */
    private final Map<Integer, Box> dirty = new LinkedHashMap<>();
    private final TreeSet<Integer> busy = new TreeSet<>();

    public SlotPool(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /** Slots left dirty by the previous run (crash / restart). They stay busy until {@link #free} is called. */
    public Map<Integer, Box> loadLeftovers() {
        dirty.clear();
        busy.clear();
        if (!file.isFile()) return Map.of();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                int slot = Integer.parseInt(k);
                Box b = Box.parse(y.getString(k));
                if (b != null) {
                    dirty.put(slot, b);
                    busy.add(slot);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return new LinkedHashMap<>(dirty);
    }

    public int busyCount() {
        return busy.size();
    }

    /** Lowest free slot, marked busy. */
    public int allocate() {
        int i = 0;
        while (busy.contains(i)) i++;
        busy.add(i);
        return i;
    }

    public void markDirty(int slot, Box absolute) {
        dirty.put(slot, absolute);
        save();
    }

    public void free(int slot) {
        busy.remove(slot);
        if (dirty.remove(slot) != null) save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Integer, Box> e : dirty.entrySet()) y.set(String.valueOf(e.getKey()), e.getValue().toString());
        try {
            AtomicYaml.save(y, file);
        } catch (IOException e) {
            log.warning("Không ghi được slots.yml: " + e.getMessage());
        }
    }
}
