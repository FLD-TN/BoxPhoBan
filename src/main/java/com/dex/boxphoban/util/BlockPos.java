package com.dex.boxphoban.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Integer position, usually relative to a slot origin. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block b) {
        return new BlockPos(b.getX(), b.getY(), b.getZ());
    }

    public static BlockPos parse(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public BlockPos minus(BlockPos o) {
        return new BlockPos(x - o.x, y - o.y, z - o.z);
    }

    public BlockPos plus(BlockPos o) {
        return new BlockPos(x + o.x, y + o.y, z + o.z);
    }

    public Location toLocation(World w) {
        return new Location(w, x, y, z);
    }

    public Block block(World w) {
        return w.getBlockAt(x, y, z);
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
