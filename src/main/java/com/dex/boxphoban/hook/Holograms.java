package com.dex.boxphoban.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.logging.Logger;

/** DecentHolograms (optional). Holograms are never saved to file, so a crash leaves nothing behind. */
public final class Holograms {

    private final boolean enabled;

    public Holograms(Logger log) {
        boolean ok = false;
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            try {
                Class.forName("eu.decentsoftware.holograms.api.DHAPI");
                ok = true;
            } catch (Throwable t) {
                log.warning("Không dùng được DecentHolograms: " + t.getMessage());
            }
        }
        enabled = ok;
        log.info("Hologram: " + (enabled ? "DecentHolograms" : "tắt (không có DecentHolograms)"));
    }

    public boolean enabled() {
        return enabled;
    }

    /** Creates the hologram or replaces its lines. */
    public void show(String name, Location at, List<String> lines) {
        if (!enabled) return;
        try {
            Dh.show(name, at, lines);
        } catch (Throwable ignored) {
            // a broken hologram must never break the dungeon
        }
    }

    public void remove(String name) {
        if (!enabled) return;
        try {
            Dh.remove(name);
        } catch (Throwable ignored) {
            // already gone
        }
    }

    /** Only loaded when DecentHolograms is present. */
    private static final class Dh {
        static void show(String name, Location at, List<String> lines) {
            var h = eu.decentsoftware.holograms.api.DHAPI.getHologram(name);
            if (h == null) {
                eu.decentsoftware.holograms.api.DHAPI.createHologram(name, at, false, lines);
            } else {
                eu.decentsoftware.holograms.api.DHAPI.setHologramLines(h, lines);
            }
        }

        static void remove(String name) {
            eu.decentsoftware.holograms.api.DHAPI.removeHologram(name);
        }
    }
}
