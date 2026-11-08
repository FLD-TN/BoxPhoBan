package com.dex.boxphoban.util;

import org.bukkit.Location;

/** Inclusive block box. Stored as "x1,y1,z1;x2,y2,z2". */
public record Box(BlockPos min, BlockPos max) {

    public static Box of(BlockPos a, BlockPos b) {
        return new Box(new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    public static Box parse(String s) {
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length != 2) return null;
        BlockPos a = BlockPos.parse(p[0]), b = BlockPos.parse(p[1]);
        return a == null || b == null ? null : of(a, b);
    }

    public Box shift(BlockPos o) {
        return new Box(min.plus(o), max.plus(o));
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.x() && x < max.x() + 1 && y >= min.y() && y < max.y() + 1 && z >= min.z() && z < max.z() + 1;
    }

    public boolean contains(Location l) {
        return contains(l.getX(), l.getY(), l.getZ());
    }

    public boolean contains(BlockPos p) {
        return p.x() >= min.x() && p.x() <= max.x() && p.y() >= min.y() && p.y() <= max.y() && p.z() >= min.z() && p.z() <= max.z();
    }

    /** Same box grown by {@code m} blocks on every side. */
    public Box grow(int m) {
        return new Box(new BlockPos(min.x() - m, min.y() - m, min.z() - m), new BlockPos(max.x() + m, max.y() + m, max.z() + m));
    }

    public long volume() {
        return (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
    }

    public int sizeX() {
        return max.x() - min.x() + 1;
    }

    public int sizeY() {
        return max.y() - min.y() + 1;
    }

    public int sizeZ() {
        return max.z() - min.z() + 1;
    }

    @Override
    public String toString() {
        return min + ";" + max;
    }
}
