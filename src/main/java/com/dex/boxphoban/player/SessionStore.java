package com.dex.boxphoban.player;

import com.dex.boxphoban.config.Settings;
import com.dex.boxphoban.util.AtomicYaml;
import com.dex.boxphoban.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crash-safe player snapshots ({@code data/sessions/<uuid>.yml}). A snapshot is written BEFORE a player is moved
 * into the dungeon world and deleted only AFTER their state has been restored, so whatever happens (leave, end,
 * quit, crash, restart) the next restore puts them back where they were.
 */
public final class SessionStore {

    public enum Kind { PLAY, WATCH, EDIT }

    private final File dir;
    private final Logger log;
    private final Settings settings;
    /** Teleports done by BoxPhoBan itself; protection listeners let these through. */
    private final Set<UUID> internalTeleport = new HashSet<>();

    public SessionStore(File dir, Settings settings, Logger log) {
        this.dir = dir;
        this.settings = settings;
        this.log = log;
    }

    private File file(UUID u) {
        return new File(dir, u + ".yml");
    }

    public boolean has(UUID u) {
        return file(u).isFile();
    }

    public Kind kind(UUID u) {
        File f = file(u);
        if (!f.isFile()) return null;
        try {
            return Kind.valueOf(YamlConfiguration.loadConfiguration(f).getString("kind", "PLAY"));
        } catch (Exception e) {
            return Kind.PLAY;
        }
    }

    /** Saves the current state unless a snapshot already exists (the first one is the real "before" state). */
    public boolean save(Player p, Kind kind) {
        if (has(p.getUniqueId())) return true;
        YamlConfiguration y = new YamlConfiguration();
        Location l = p.getLocation();
        y.set("kind", kind.name());
        y.set("name", p.getName());
        y.set("world", l.getWorld() == null ? "" : l.getWorld().getName());
        y.set("x", l.getX());
        y.set("y", l.getY());
        y.set("z", l.getZ());
        y.set("yaw", l.getYaw());
        y.set("pitch", l.getPitch());
        y.set("gamemode", p.getGameMode().name());
        y.set("allow-flight", p.getAllowFlight());
        y.set("flying", p.isFlying());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        y.set("saturation", p.getSaturation());
        y.set("saved-at", System.currentTimeMillis());
        try {
            AtomicYaml.save(y, file(p.getUniqueId()));
            return true;
        } catch (IOException e) {
            log.log(Level.SEVERE, "Không lưu được snapshot của " + p.getName(), e);
            return false;
        }
    }

    /**
     * Restores the saved state. {@code teleport} = also send the player back to the saved location.
     * Returns false when there was nothing to restore.
     */
    public boolean restore(Player p, boolean teleport) {
        File f = file(p.getUniqueId());
        if (!f.isFile()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try {
            Tags.strip(p, Tags.KEY);
            Tags.strip(p, Tags.TOOL);
        } catch (Exception e) {
            log.log(Level.WARNING, "strip items", e);
        }
        GameMode gm;
        try {
            gm = GameMode.valueOf(y.getString("gamemode", "SURVIVAL"));
        } catch (Exception e) {
            gm = GameMode.SURVIVAL;
        }
        try {
            if (p.isDead()) p.spigot().respawn();
        } catch (Exception ignored) {
            // still restore the rest
        }
        try {
            p.setGameMode(gm);
            p.setAllowFlight(y.getBoolean("allow-flight", gm == GameMode.CREATIVE));
            p.setFlying(p.getAllowFlight() && y.getBoolean("flying", false));
            p.setFallDistance(0);
            p.setFireTicks(0);
        } catch (Exception e) {
            log.log(Level.WARNING, "restore gamemode", e);
        }
        try {
            AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double cap = max == null ? 20 : max.getValue();
            double hp = y.getDouble("health", cap);
            p.setHealth(Math.max(1, Math.min(cap, hp)));
            p.setFoodLevel(Math.max(0, Math.min(20, y.getInt("food", 20))));
            p.setSaturation((float) y.getDouble("saturation", 5));
        } catch (Exception e) {
            log.log(Level.WARNING, "restore health", e);
        }
        if (teleport) {
            World w = Bukkit.getWorld(y.getString("world", ""));
            Location to = w == null ? settings.fallback()
                    : new Location(w, y.getDouble("x"), y.getDouble("y"), y.getDouble("z"),
                    (float) y.getDouble("yaw"), (float) y.getDouble("pitch"));
            teleport(p, to);
        }
        if (!f.delete()) log.warning("Không xoá được snapshot " + f.getName());
        return true;
    }

    /** Teleport marked as internal (protection listeners skip it). */
    public boolean teleport(Player p, Location to) {
        internalTeleport.add(p.getUniqueId());
        try {
            return p.teleport(to);
        } finally {
            internalTeleport.remove(p.getUniqueId());
        }
    }

    public boolean isInternalTeleport(UUID u) {
        return internalTeleport.contains(u);
    }
}
