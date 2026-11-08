package com.dex.boxphoban.template;

import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Resets;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A dungeon: settings from {@code dungeon.yml} (edited by hand) plus editor data from {@code nodes.yml}
 * (written by the in-game editor). All positions are relative to the slot origin.
 */
public final class DungeonTemplate {

    public enum Consume { CREATOR, EVERY_MEMBER }

    public enum ApplyOn { START, FINISH, LEAVE }

    public enum LivesMode { SHARED, PERSONAL, NONE }

    public final String id;
    public final File dir;

    // dungeon.yml
    public boolean enabled;
    public String display;
    public String color;
    public Material icon;
    public List<String> description = new ArrayList<>();
    public int minPlayers;
    public int maxPlayers;
    public int maxInstances;
    public boolean ticketEnabled;
    public String ticketId;
    public int ticketAmount;
    public Consume consume;
    public double costMoney;
    public String permission;
    public List<String> requiredClears = new ArrayList<>();
    public Resets.Type cooldownType;
    public int cooldownMinutes;
    public int resetHour;
    public DayOfWeek resetDay;
    public ApplyOn applyOn;
    public int dailyLimit;
    public LivesMode livesMode;
    public int lives;
    public int respawnDelay;
    public boolean rewardDeadPlayers;
    public final LinkedHashMap<String, Difficulty> difficulties = new LinkedHashMap<>();

    // nodes.yml
    public int editorSlot = -1;
    public Box bounds;
    public Point start;
    public final LinkedHashMap<String, Point> points = new LinkedHashMap<>();
    public final LinkedHashMap<String, Box> regions = new LinkedHashMap<>();
    public final List<Stage> stages = new ArrayList<>();
    public final LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    public int nextNodeId = 1;

    public DungeonTemplate(String id, File dir) {
        this.id = id;
        this.dir = dir;
    }

    public File settingsFile() {
        return new File(dir, "dungeon.yml");
    }

    public File nodesFile() {
        return new File(dir, "nodes.yml");
    }

    public File schematicFile() {
        return new File(dir, "map.schem");
    }

    public boolean playable() {
        return enabled && bounds != null && start != null && schematicFile().isFile() && !difficulties.isEmpty();
    }

    public Difficulty difficulty(String id) {
        if (id == null) return firstDifficulty();
        Difficulty d = difficulties.get(id.toLowerCase(Locale.ROOT));
        return d == null ? null : d;
    }

    public Difficulty firstDifficulty() {
        return difficulties.isEmpty() ? null : difficulties.values().iterator().next();
    }

    public Stage stage(String id) {
        for (Stage s : stages) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }

    public int stageIndex(String id) {
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).id.equalsIgnoreCase(id)) return i;
        return -1;
    }

    public Node nodeAt(BlockPos rel) {
        for (Node n : nodes.values()) if (n.pos.equals(rel)) return n;
        return null;
    }

    public String newNodeId() {
        String id;
        do {
            id = "n" + nextNodeId++;
        } while (nodes.containsKey(id));
        return id;
    }

    // ------------------------------------------------------------------ dungeon.yml

    public void loadSettings(YamlConfiguration c) {
        enabled = c.getBoolean("enabled", true);
        display = c.getString("display", id);
        color = c.getString("color", "&#5EEAD4");
        icon = Items.material(c.getString("icon"), Material.IRON_SWORD);
        description = c.getStringList("description");
        minPlayers = Math.max(1, c.getInt("players.min", 1));
        maxPlayers = Math.max(minPlayers, c.getInt("players.max", 4));
        maxInstances = Math.max(1, c.getInt("max-instances", 5));
        ticketEnabled = c.getBoolean("entry.ticket.enabled", false);
        ticketId = c.getString("entry.ticket.id", "");
        ticketAmount = Math.max(1, c.getInt("entry.ticket.amount", 1));
        consume = parse(Consume.class, c.getString("entry.ticket.consume"), Consume.CREATOR);
        costMoney = Math.max(0, c.getDouble("entry.cost-money", 0));
        permission = c.getString("entry.permission", "");
        requiredClears = c.getStringList("entry.required-clears");
        cooldownType = parse(Resets.Type.class, c.getString("entry.cooldown.type"), Resets.Type.NONE);
        cooldownMinutes = Math.max(0, c.getInt("entry.cooldown.minutes", 30));
        resetHour = c.getInt("entry.cooldown.reset-hour", 0);
        resetDay = parse(DayOfWeek.class, c.getString("entry.cooldown.reset-day"), DayOfWeek.MONDAY);
        applyOn = parse(ApplyOn.class, c.getString("entry.cooldown.apply-on"), ApplyOn.FINISH);
        dailyLimit = Math.max(0, c.getInt("entry.daily-limit", 0));
        livesMode = parse(LivesMode.class, c.getString("lives.mode"), LivesMode.SHARED);
        lives = Math.max(0, c.getInt("lives.amount", 5));
        respawnDelay = Math.max(0, c.getInt("lives.respawn-delay", 5));
        rewardDeadPlayers = c.getBoolean("reward-dead-players", true);
        difficulties.clear();
        ConfigurationSection ds = c.getConfigurationSection("difficulties");
        if (ds != null) {
            for (String key : ds.getKeys(false)) {
                ConfigurationSection d = ds.getConfigurationSection(key);
                if (d == null) continue;
                String k = key.toLowerCase(Locale.ROOT);
                difficulties.put(k, new Difficulty(k, d.getString("display", key),
                        Items.material(d.getString("icon"), Material.IRON_SWORD),
                        Math.max(1, d.getInt("mob-level", 1)), Math.max(0.01, d.getDouble("health-multiplier", 1)),
                        Math.max(0.0, d.getDouble("damage-multiplier", 1)), Math.max(0, d.getInt("time-limit", 0)),
                        d.getString("rewards", k)));
            }
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String s, E def) {
        if (s == null) return def;
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ nodes.yml

    public void loadEditorData(YamlConfiguration c) {
        editorSlot = c.getInt("editor-slot", -1);
        bounds = Box.parse(c.getString("bounds"));
        start = Point.parse(c.getString("start"));
        nextNodeId = Math.max(1, c.getInt("next-node-id", 1));
        points.clear();
        ConfigurationSection ps = c.getConfigurationSection("points");
        if (ps != null) for (String k : ps.getKeys(false)) {
            Point p = Point.parse(ps.getString(k));
            if (p != null) points.put(k, p);
        }
        regions.clear();
        ConfigurationSection rs = c.getConfigurationSection("regions");
        if (rs != null) for (String k : rs.getKeys(false)) {
            Box b = Box.parse(rs.getString(k));
            if (b != null) regions.put(k, b);
        }
        stages.clear();
        for (Map<?, ?> m : c.getMapList("stages")) {
            Object id = m.get("id");
            if (id == null) continue;
            Object disp = m.get("display");
            Object obj = m.get("objective");
            stages.add(new Stage(id.toString(), disp == null ? id.toString() : disp.toString(), obj == null ? "" : obj.toString()));
        }
        nodes.clear();
        ConfigurationSection ns = c.getConfigurationSection("nodes");
        if (ns != null) for (String k : ns.getKeys(false)) {
            ConfigurationSection n = ns.getConfigurationSection(k);
            if (n == null) continue;
            BlockPos pos = BlockPos.parse(n.getString("pos"));
            if (pos == null) continue;
            Node node = new Node(k, pos);
            node.trigger = readElement(n.getConfigurationSection("trigger"));
            if (node.trigger == null) node.trigger = Element.withDefaults(TriggerType.INTERACT.name(), TriggerType.INTERACT.params);
            node.repeat = n.getBoolean("repeat", false);
            node.perPlayer = n.getBoolean("per-player", false);
            node.delay = Math.max(0, n.getInt("delay", 0));
            readList(n, "conditions", node.conditions);
            readList(n, "actions", node.actions);
            nodes.put(k, node);
        }
    }

    private static void readList(ConfigurationSection n, String key, List<Element> out) {
        for (Map<?, ?> m : n.getMapList(key)) {
            Object type = m.get("type");
            if (type == null) continue;
            Element e = new Element(type.toString());
            for (Map.Entry<?, ?> en : m.entrySet()) {
                if (en.getKey().equals("type") || en.getValue() == null) continue;
                e.params.put(en.getKey().toString(), en.getValue().toString());
            }
            out.add(e);
        }
    }

    private static Element readElement(ConfigurationSection s) {
        if (s == null || s.getString("type") == null) return null;
        Element e = new Element(s.getString("type"));
        for (String k : s.getKeys(false)) {
            if (k.equals("type")) continue;
            e.params.put(k, String.valueOf(s.get(k)));
        }
        return e;
    }

    public YamlConfiguration saveEditorData() {
        YamlConfiguration c = new YamlConfiguration();
        c.options().setHeader(List.of("Dữ liệu editor của phó bản " + id + ". File này do /pba save ghi, nên sửa bằng editor trong game."));
        c.set("editor-slot", editorSlot);
        c.set("bounds", bounds == null ? null : bounds.toString());
        c.set("start", start == null ? null : start.toString());
        c.set("next-node-id", nextNodeId);
        for (Map.Entry<String, Point> e : points.entrySet()) c.set("points." + e.getKey(), e.getValue().toString());
        for (Map.Entry<String, Box> e : regions.entrySet()) c.set("regions." + e.getKey(), e.getValue().toString());
        List<Map<String, Object>> st = new ArrayList<>();
        for (Stage s : stages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("display", s.display);
            m.put("objective", s.objective);
            st.add(m);
        }
        c.set("stages", st);
        for (Node n : nodes.values()) {
            String p = "nodes." + n.id + ".";
            c.set(p + "pos", n.pos.toString());
            c.set(p + "trigger.type", n.trigger.type);
            for (Map.Entry<String, String> e : n.trigger.params.entrySet()) c.set(p + "trigger." + e.getKey(), e.getValue());
            c.set(p + "repeat", n.repeat);
            c.set(p + "per-player", n.perPlayer);
            c.set(p + "delay", n.delay);
            c.set(p + "conditions", toMaps(n.conditions));
            c.set(p + "actions", toMaps(n.actions));
        }
        return c;
    }

    private static List<Map<String, Object>> toMaps(List<Element> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Element e : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.type);
            m.putAll(e.params);
            out.add(m);
        }
        return out;
    }
}
