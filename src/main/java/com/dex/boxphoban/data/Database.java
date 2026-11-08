package com.dex.boxphoban.data;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SQLite storage (clears, best times, cooldowns, daily runs, pending reward items). Small, quick queries on the
 * main thread, with an in-memory cache for the per-player rows read by GUIs and placeholders.
 */
public final class Database {

    public record Stats(int clears, long bestTime) {
        public static final Stats NONE = new Stats(0, 0);
    }

    public record Pending(long id, byte[] item) {
    }

    private final Logger log;
    private Connection con;
    private final Map<String, Stats> statsCache = new HashMap<>();
    private final Map<String, Long> lastRunCache = new HashMap<>();

    public Database(File file, Logger log) throws SQLException {
        this.log = log;
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new SQLException("mkdir " + parent);
        con = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("CREATE TABLE IF NOT EXISTS stats (uuid TEXT NOT NULL, dungeon TEXT NOT NULL, clears INTEGER NOT NULL,"
                    + " best_time INTEGER NOT NULL, PRIMARY KEY (uuid, dungeon))");
            st.execute("CREATE TABLE IF NOT EXISTS runs (uuid TEXT NOT NULL, dungeon TEXT NOT NULL, last INTEGER NOT NULL,"
                    + " PRIMARY KEY (uuid, dungeon))");
            st.execute("CREATE TABLE IF NOT EXISTS daily (uuid TEXT NOT NULL, dungeon TEXT NOT NULL, day TEXT NOT NULL,"
                    + " count INTEGER NOT NULL, PRIMARY KEY (uuid, dungeon))");
            st.execute("CREATE TABLE IF NOT EXISTS pending (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL,"
                    + " item BLOB NOT NULL, created INTEGER NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS pending_uuid ON pending (uuid)");
        }
    }

    public void close() {
        try {
            if (con != null) con.close();
        } catch (SQLException ignored) {
            // shutting down
        }
        con = null;
    }

    private static String key(UUID u, String d) {
        return u + "|" + d;
    }

    // ------------------------------------------------------------------ stats

    public Stats stats(UUID u, String dungeon) {
        String k = key(u, dungeon);
        Stats cached = statsCache.get(k);
        if (cached != null) return cached;
        Stats s = Stats.NONE;
        try (PreparedStatement ps = con.prepareStatement("SELECT clears, best_time FROM stats WHERE uuid=? AND dungeon=?")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) s = new Stats(rs.getInt(1), rs.getLong(2));
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "stats read", e);
        }
        statsCache.put(k, s);
        return s;
    }

    /** Adds one clear; returns true when it was the first clear. */
    public boolean recordClear(UUID u, String dungeon, long seconds) {
        Stats old = stats(u, dungeon);
        long best = old.bestTime() <= 0 ? seconds : Math.min(old.bestTime(), seconds);
        Stats now = new Stats(old.clears() + 1, best);
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO stats (uuid, dungeon, clears, best_time) VALUES (?,?,?,?)"
                + " ON CONFLICT(uuid, dungeon) DO UPDATE SET clears=excluded.clears, best_time=excluded.best_time")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            ps.setInt(3, now.clears());
            ps.setLong(4, now.bestTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "stats write", e);
        }
        statsCache.put(key(u, dungeon), now);
        return old.clears() == 0;
    }

    // ------------------------------------------------------------------ cooldown (last run time)

    public long lastRun(UUID u, String dungeon) {
        String k = key(u, dungeon);
        Long cached = lastRunCache.get(k);
        if (cached != null) return cached;
        long last = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT last FROM runs WHERE uuid=? AND dungeon=?")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) last = rs.getLong(1);
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "runs read", e);
        }
        lastRunCache.put(k, last);
        return last;
    }

    public void setLastRun(UUID u, String dungeon, long epochSecond) {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO runs (uuid, dungeon, last) VALUES (?,?,?)"
                + " ON CONFLICT(uuid, dungeon) DO UPDATE SET last=excluded.last")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            ps.setLong(3, epochSecond);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "runs write", e);
        }
        lastRunCache.put(key(u, dungeon), epochSecond);
    }

    public void resetCooldown(UUID u, String dungeon) {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM runs WHERE uuid=? AND dungeon=?")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "runs delete", e);
        }
        lastRunCache.remove(key(u, dungeon));
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM daily WHERE uuid=? AND dungeon=?")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "daily delete", e);
        }
    }

    // ------------------------------------------------------------------ daily runs

    public int dailyCount(UUID u, String dungeon, String day) {
        try (PreparedStatement ps = con.prepareStatement("SELECT day, count FROM daily WHERE uuid=? AND dungeon=?")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && day.equals(rs.getString(1))) return rs.getInt(2);
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "daily read", e);
        }
        return 0;
    }

    public void addDaily(UUID u, String dungeon, String day) {
        int now = dailyCount(u, dungeon, day) + 1;
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO daily (uuid, dungeon, day, count) VALUES (?,?,?,?)"
                + " ON CONFLICT(uuid, dungeon) DO UPDATE SET day=excluded.day, count=excluded.count")) {
            ps.setString(1, u.toString());
            ps.setString(2, dungeon);
            ps.setString(3, day);
            ps.setInt(4, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "daily write", e);
        }
    }

    // ------------------------------------------------------------------ pending reward items

    public void addPending(UUID u, byte[] item) {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO pending (uuid, item, created) VALUES (?,?,?)")) {
            ps.setString(1, u.toString());
            ps.setBytes(2, item);
            ps.setLong(3, System.currentTimeMillis() / 1000);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "pending write", e);
        }
    }

    public List<Pending> pending(UUID u) {
        List<Pending> out = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT id, item FROM pending WHERE uuid=? ORDER BY id")) {
            ps.setString(1, u.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Pending(rs.getLong(1), rs.getBytes(2)));
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "pending read", e);
        }
        return out;
    }

    /** True if the row existed (so the item may be handed out exactly once). */
    public boolean removePending(long id) {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM pending WHERE id=?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.log(Level.WARNING, "pending delete", e);
            return false;
        }
    }

    public void forget(UUID u) {
        String prefix = u + "|";
        statsCache.keySet().removeIf(k -> k.startsWith(prefix));
        lastRunCache.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
