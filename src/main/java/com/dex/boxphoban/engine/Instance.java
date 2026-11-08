package com.dex.boxphoban.engine;

import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Stage;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Point;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One running copy of a dungeon. */
public final class Instance {

    public enum Phase { PREPARING, RUNNING, ENDED }

    public enum State { ALIVE, WAITING, SPECTATOR }

    /** Runtime data of one node. */
    public static final class NodeState {
        public boolean fired;
        public final Set<UUID> firedPlayers = new HashSet<>();
        public int counter;
        public final Set<UUID> spawned = new HashSet<>();
        public boolean everSpawned;
    }

    public final int id;
    public final DungeonTemplate template;
    public final Difficulty difficulty;
    public final int slot;
    public final BlockPos origin;
    public final World world;
    public final Box box;
    public final boolean test;
    public Phase phase = Phase.PREPARING;

    public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    public final Map<UUID, String> names = new HashMap<>();
    public final Map<UUID, State> states = new HashMap<>();
    public final Set<UUID> watchers = new HashSet<>();
    public final Map<UUID, Integer> personalLives = new HashMap<>();
    public int sharedLives;
    public final Map<UUID, Integer> deaths = new HashMap<>();
    public final Map<UUID, Long> offlineSince = new HashMap<>();
    public final Map<UUID, Location> deathLocation = new HashMap<>();
    public final Map<UUID, Integer> respawnTasks = new HashMap<>();
    public final Map<UUID, List<ItemStack>> bag = new HashMap<>();
    public final List<Runnable> refunds = new ArrayList<>();

    public Point checkpoint;
    public int stageIndex = -1;
    public long stageStartMs;
    public long startMs;
    public long endMs;

    public final Map<String, NodeState> nodeStates = new HashMap<>();
    /** tracked mob entity -> node id that spawned it. */
    public final Map<UUID, String> mobs = new LinkedHashMap<>();
    public final Map<UUID, String> mobTypes = new HashMap<>();
    public final Map<String, List<BlockData>> savedDoors = new HashMap<>();
    public BossBar bossBar;
    public UUID boss;
    public final Set<Long> chunkTickets = new HashSet<>();
    public final Map<String, Walls.Wall> walls = new HashMap<>();
    public final List<Walls.Lock> locks = new ArrayList<>();
    /** Mobs spawned during the current stage that are still alive. */
    public final Set<UUID> stageMobs = new HashSet<>();
    public boolean stageSpawned;
    public boolean stageCleared;
    /** Node runs waiting for their delay (a delayed spawn keeps the stage from counting as cleared). */
    public int pendingDelayed;
    /** Objective set by a node (Đổi mục tiêu); null = the stage's own objective. Reset on stage change. */
    public String objectiveOverride;

    public Instance(int id, DungeonTemplate template, Difficulty difficulty, int slot, BlockPos origin, World world, boolean test) {
        this.id = id;
        this.template = template;
        this.difficulty = difficulty;
        this.slot = slot;
        this.origin = origin;
        this.world = world;
        this.box = template.bounds.shift(origin);
        this.test = test;
    }

    public NodeState state(String nodeId) {
        return nodeStates.computeIfAbsent(nodeId, k -> new NodeState());
    }

    public long elapsedSeconds() {
        if (startMs == 0) return 0;
        long end = endMs > 0 ? endMs : System.currentTimeMillis();
        return Math.max(0, (end - startMs) / 1000);
    }

    public long stageElapsedSeconds() {
        if (stageStartMs == 0) return 0;
        return Math.max(0, (System.currentTimeMillis() - stageStartMs) / 1000);
    }

    /** Seconds left, or -1 without a time limit. */
    public long timeLeft() {
        if (difficulty.timeLimit() <= 0) return -1;
        return Math.max(0, difficulty.timeLimit() - elapsedSeconds());
    }

    public Stage stage() {
        return stageIndex >= 0 && stageIndex < template.stages.size() ? template.stages.get(stageIndex) : null;
    }

    public Location loc(Point p) {
        return p.toLocation(world, origin);
    }

    public Location startLocation() {
        return loc(template.start);
    }

    public Location checkpointLocation() {
        return checkpoint == null ? startLocation() : loc(checkpoint);
    }

    public Location blockCenter(BlockPos rel) {
        return new Location(world, origin.x() + rel.x() + 0.5, origin.y() + rel.y() + 1, origin.z() + rel.z() + 0.5);
    }

    public BlockPos toRelative(BlockPos absolute) {
        return absolute.minus(origin);
    }

    public Box absolute(Box rel) {
        return rel.shift(origin);
    }

    public boolean inside(Location l) {
        return l.getWorld() != null && l.getWorld().equals(world) && box.contains(l);
    }

    public State stateOf(UUID u) {
        return states.getOrDefault(u, State.ALIVE);
    }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && !offlineSince.containsKey(u)) out.add(p);
        }
        return out;
    }

    /** Online members that are alive (not waiting, not spectating). */
    public List<Player> alivePlayers() {
        List<Player> out = new ArrayList<>();
        for (Player p : onlineMembers()) if (stateOf(p.getUniqueId()) == State.ALIVE) out.add(p);
        return out;
    }

    /** Members plus watchers who are online. */
    public List<Player> viewers() {
        List<Player> out = onlineMembers();
        for (UUID u : watchers) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** Members still able to play (alive or waiting to respawn, online or within the disconnect grace). */
    public int playingCount() {
        int n = 0;
        for (UUID u : members) if (stateOf(u) != State.SPECTATOR) n++;
        return n;
    }

    public int livesOf(UUID u) {
        return switch (template.livesMode) {
            case SHARED -> sharedLives;
            case PERSONAL -> personalLives.getOrDefault(u, 0);
            case NONE -> 0;
        };
    }

    public int mobsLeft() {
        return mobs.size();
    }

    /** Key items carry this instance's random token, so a key from another run never matches. */
    public final String token = UUID.randomUUID().toString().substring(0, 8);

    public String keyId(String key) {
        return token + ":" + key;
    }
}
