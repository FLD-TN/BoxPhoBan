package com.dex.boxphoban.util;

import org.bukkit.Location;
import org.bukkit.World;

/** Position with facing, relative to a slot origin. Stored as "x,y,z,yaw,pitch". */
public record Point(double x, double y, double z, float yaw, float pitch) {

    public static Point relative(Location l, BlockPos origin) {
        return new Point(l.getX() - origin.x(), l.getY() - origin.y(), l.getZ() - origin.z(), l.getYaw(), l.getPitch());
    }

    public static Point parse(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length < 3) return null;
        try {
            return new Point(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                    p.length > 3 ? Float.parseFloat(p[3]) : 0f, p.length > 4 ? Float.parseFloat(p[4]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Location toLocation(World w, BlockPos origin) {
        return new Location(w, origin.x() + x, origin.y() + y, origin.z() + z, yaw, pitch);
    }

    @Override
    public String toString() {
        return round(x) + "," + round(y) + "," + round(z) + "," + round(yaw) + "," + round(pitch);
    }

    private static String round(double d) {
        double r = Math.round(d * 100.0) / 100.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }
}
