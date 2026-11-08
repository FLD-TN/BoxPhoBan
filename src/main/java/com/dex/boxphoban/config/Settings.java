package com.dex.boxphoban.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** config.yml values. */
public final class Settings {

    public final String worldName;
    public final int spacing;
    public final int columns;
    public final int maxInstances;
    public final int roomIdleTimeout;
    public final int inviteTimeout;
    public final int graceSeconds;
    public final Set<String> commandWhitelist = new HashSet<>();
    public final boolean pvp;
    public final boolean blockContainers;
    public final boolean blockDrop;
    public final boolean blockPlayerInteract;
    public final int spectatorMargin;
    public final ZoneId zone;
    public final int winDelaySeconds;
    public final int endDelaySeconds;
    public final int resultDelayTicks;
    private final String fallbackWorld;
    private final double fx, fy, fz;
    private final float fyaw, fpitch;

    public Settings(YamlConfiguration c) {
        worldName = c.getString("world.name", "boxphoban");
        spacing = Math.max(256, c.getInt("world.grid-spacing", 1000));
        columns = Math.max(1, c.getInt("world.grid-columns", 32));
        maxInstances = Math.max(1, c.getInt("limits.max-instances", 20));
        roomIdleTimeout = Math.max(30, c.getInt("room.idle-timeout", 300));
        inviteTimeout = Math.max(10, c.getInt("room.invite-timeout", 60));
        graceSeconds = Math.max(0, c.getInt("disconnect.grace-seconds", 180));
        List<String> wl = c.getStringList("commands.whitelist");
        for (String s : wl) commandWhitelist.add(s.toLowerCase(Locale.ROOT).replace("/", "").trim());
        commandWhitelist.add("phoban");
        commandWhitelist.add("pb");
        pvp = c.getBoolean("protection.pvp", false);
        blockContainers = c.getBoolean("protection.block-containers", true);
        blockDrop = c.getBoolean("protection.block-item-drop", true);
        blockPlayerInteract = c.getBoolean("protection.block-player-interact", true);
        spectatorMargin = Math.max(0, c.getInt("spectator.bounds-margin", 8));
        ZoneId z;
        try {
            z = ZoneId.of(c.getString("timezone", "Asia/Ho_Chi_Minh"));
        } catch (Exception e) {
            z = ZoneId.of("Asia/Ho_Chi_Minh");
        }
        zone = z;
        winDelaySeconds = Math.max(0, c.getInt("end.win-delay-seconds", 5));
        endDelaySeconds = Math.max(0, c.getInt("end.fail-delay-seconds", 3));
        resultDelayTicks = Math.max(1, c.getInt("end.result-menu-delay-ticks", 10));
        fallbackWorld = c.getString("fallback-location.world", "");
        fx = c.getDouble("fallback-location.x");
        fy = c.getDouble("fallback-location.y", 64);
        fz = c.getDouble("fallback-location.z");
        fyaw = (float) c.getDouble("fallback-location.yaw");
        fpitch = (float) c.getDouble("fallback-location.pitch");
    }

    /** Where players go when their saved location is gone. */
    public Location fallback() {
        World w = fallbackWorld.isEmpty() ? null : Bukkit.getWorld(fallbackWorld);
        if (w == null) {
            World main = Bukkit.getWorlds().get(0);
            return main.getSpawnLocation();
        }
        return new Location(w, fx, fy, fz, fyaw, fpitch);
    }
}
