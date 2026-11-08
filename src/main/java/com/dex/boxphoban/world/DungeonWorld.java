package com.dex.boxphoban.world;

import com.dex.boxphoban.config.Settings;
import com.dex.boxphoban.util.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

/** The single void world holding every instance and editor slot. */
public final class DungeonWorld {

    private final Settings settings;
    private World world;

    public DungeonWorld(Settings settings) {
        this.settings = settings;
    }

    public World load() {
        world = Bukkit.getWorld(settings.worldName);
        if (world == null) {
            world = new WorldCreator(settings.worldName)
                    .generator(new VoidGenerator())
                    .type(WorldType.FLAT)
                    .generateStructures(false)
                    .createWorld();
        }
        if (world != null) applyRules(world);
        return world;
    }

    private static void applyRules(World w) {
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        w.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        w.setGameRule(GameRule.DO_INSOMNIA, false);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setDifficulty(Difficulty.HARD);
        w.setTime(6000);
        w.setStorm(false);
        w.setThundering(false);
        w.setAutoSave(false);
    }

    public World world() {
        return world;
    }

    public boolean is(World w) {
        return w != null && world != null && w.getUID().equals(world.getUID());
    }

    /** Origin of instance slot {@code i} (rows start one spacing away from the editor row). */
    public BlockPos instanceOrigin(int i) {
        int gx = i % settings.columns, gz = i / settings.columns;
        return new BlockPos(gx * settings.spacing, 64, (gz + 1) * settings.spacing);
    }

    /** Origin of editor slot {@code e} (row z = -spacing). */
    public BlockPos editorOrigin(int e) {
        return new BlockPos(e * settings.spacing, 64, -settings.spacing);
    }

    /** Largest relative bounds a template may use so slots never overlap. */
    public int maxHalfSize() {
        return settings.spacing / 2 - 32;
    }
}
