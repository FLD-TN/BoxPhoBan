package com.dex.boxphoban.engine;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.player.SessionStore;
import com.dex.boxphoban.reward.RewardService;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Lifecycle of instances: start (paste), run, deaths, leaving, disconnects, end, cleanup. */
public final class InstanceManager {

    public enum Outcome { WIN, FAIL, TIMEOUT, STOPPED, ABANDONED }

    /** What the result menu shows. */
    public record Result(String dungeon, String difficulty, Outcome outcome, long seconds, int deaths, List<String> rewards,
                         boolean firstClear) {
    }

    private final BoxPhoBan plugin;
    private final Map<Integer, Instance> instances = new LinkedHashMap<>();
    private final Map<UUID, Instance> byPlayer = new HashMap<>();
    private int nextId = 1;

    public InstanceManager(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    private SessionStore sessions() {
        return plugin.sessions();
    }

    public Instance of(UUID u) {
        return byPlayer.get(u);
    }

    public Instance get(int id) {
        return instances.get(id);
    }

    public Collection<Instance> all() {
        return instances.values();
    }

    public int running(DungeonTemplate t) {
        int n = 0;
        for (Instance in : instances.values()) if (in.template == t) n++;
        return n;
    }

    /** messages.yml key when no slot can be used right now, else null. */
    public String capacityProblem(DungeonTemplate t) {
        if (plugin.dungeonWorld().world() == null) return "dungeon.world-missing";
        if (plugin.slots().busyCount() >= plugin.settings().maxInstances) return "room.no-slot";
        if (running(t) >= t.maxInstances) return "room.no-slot-dungeon";
        return null;
    }

    // ------------------------------------------------------------------ start

    public void start(DungeonTemplate t, Difficulty d, List<Player> players, List<Runnable> refunds, boolean test) {
        World w = plugin.dungeonWorld().world();
        int slot = plugin.slots().allocate();
        BlockPos origin = plugin.dungeonWorld().instanceOrigin(slot);
        Instance in = new Instance(nextId++, t, d, slot, origin, w, test);
        in.refunds.addAll(refunds);
        instances.put(in.id, in);
        for (Player p : players) {
            in.members.add(p.getUniqueId());
            in.names.put(p.getUniqueId(), p.getName());
            byPlayer.put(p.getUniqueId(), in);
            msg().title(p, "dungeon.preparing-title", "dungeon.preparing-subtitle", 5, 200, 10, "dungeon", t.display);
        }
        plugin.slots().markDirty(slot, in.box);
        plugin.maps().paste(t.schematicFile(), w, origin, err -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Paste phó bản " + t.id + " lỗi", err);
                abort(in, "dungeon.paste-failed");
                return;
            }
            begin(in);
        });
    }

    /** Paste failed: refund, release players, clean the slot. */
    private void abort(Instance in, String key) {
        in.phase = Instance.Phase.ENDED;
        in.refunds.forEach(r -> {
            try {
                r.run();
            } catch (Exception ignored) {
                // best effort
            }
        });
        for (UUID u : in.members) {
            byPlayer.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                p.resetTitle();
                msg().send(p, key);
            }
        }
        instances.remove(in.id);
        cleanup(in);
    }

    private void begin(Instance in) {
        if (in.phase != Instance.Phase.PREPARING) return;
        DungeonTemplate t = in.template;
        Location start = in.startLocation();
        for (UUID u : new ArrayList<>(in.members)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline() || !sessions().save(p, SessionStore.Kind.PLAY)) {
                in.members.remove(u);
                byPlayer.remove(u);
                continue;
            }
            p.closeInventory();
            if (p.isInsideVehicle()) p.leaveVehicle();
            if (!sessions().teleport(p, start) || !p.getWorld().equals(in.world)) {
                // another plugin blocked the teleport (combat tag...): this player does not take part
                in.members.remove(u);
                byPlayer.remove(u);
                sessions().restore(p, false);
                msg().send(p, "dungeon.teleport-blocked");
                continue;
            }
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
            p.setFlying(false);
            AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFoodLevel(20);
            p.setSaturation(10);
            p.setFireTicks(0);
            p.setFallDistance(0);
            in.states.put(u, Instance.State.ALIVE);
            if (t.livesMode == DungeonTemplate.LivesMode.PERSONAL) in.personalLives.put(u, t.lives);
            if (!in.test) plugin.entry().onStart(u, t);
        }
        if (in.members.isEmpty()) {
            abort(in, "dungeon.paste-failed");
            return;
        }
        in.sharedLives = t.livesMode == DungeonTemplate.LivesMode.SHARED ? t.lives : 0;
        in.phase = Instance.Phase.RUNNING;
        in.startMs = System.currentTimeMillis();
        ticketChunks(in);
        for (Player p : in.onlineMembers()) {
            msg().title(p, "dungeon.start-title", "dungeon.start-subtitle", 5, 50, 15,
                    "dungeon", t.display, "difficulty", in.difficulty.display());
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        plugin.engine().onStart(in);
        if (in.phase == Instance.Phase.RUNNING && in.stageIndex < 0 && !t.stages.isEmpty()) setStage(in, 0);
    }

    /** Keeps every chunk of the instance loaded while it runs (so tracked mobs never unload). */
    private void ticketChunks(Instance in) {
        Box b = in.box;
        int cx0 = b.min().x() >> 4, cx1 = b.max().x() >> 4, cz0 = b.min().z() >> 4, cz1 = b.max().z() >> 4;
        long count = (long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        if (count > 400) return; // huge maps: only the chunks where mobs spawn get a ticket
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                long key = ((long) x << 32) | (z & 0xffffffffL);
                if (in.chunkTickets.add(key)) in.world.getChunkAt(x, z).addPluginChunkTicket(plugin);
            }
        }
    }

    public void setStage(Instance in, int idx) {
        if (idx < 0 || idx >= in.template.stages.size()) return;
        in.stageIndex = idx;
        in.stageStartMs = System.currentTimeMillis();
        var st = in.template.stages.get(idx);
        for (Player p : in.viewers()) {
            msg().title(p, "dungeon.stage-title", "dungeon.stage-subtitle", 5, 40, 10,
                    "stage", st.display, "index", String.valueOf(idx + 1), "total", String.valueOf(in.template.stages.size()),
                    "objective", st.objective);
        }
        plugin.engine().onStageStart(in, st.id);
    }

    public void addLives(Instance in, int amount) {
        switch (in.template.livesMode) {
            case SHARED -> in.sharedLives = Math.max(0, in.sharedLives + amount);
            case PERSONAL -> {
                for (UUID u : in.members) in.personalLives.merge(u, amount, (a, b) -> Math.max(0, a + b));
            }
            case NONE -> {
                return;
            }
        }
        String key = amount >= 0 ? "dungeon.lives-added" : "dungeon.lives-removed";
        for (Player p : in.onlineMembers()) msg().send(p, key, "amount", String.valueOf(Math.abs(amount)));
    }

    // ------------------------------------------------------------------ ticking

    /** Every second. */
    public void tickSecond() {
        long now = System.currentTimeMillis();
        for (Instance in : new ArrayList<>(instances.values())) {
            if (in.phase != Instance.Phase.RUNNING) continue;
            long left = in.timeLeft();
            if (left == 0) {
                end(in, Outcome.TIMEOUT);
                continue;
            }
            if (left > 0 && (left == 60 || left == 30 || left == 10)) {
                for (Player p : in.onlineMembers()) msg().send(p, "dungeon.time-warning", "time", msg().duration(left));
            }
            long grace = plugin.settings().graceSeconds * 1000L;
            for (Map.Entry<UUID, Long> e : new ArrayList<>(in.offlineSince.entrySet())) {
                if (now - e.getValue() >= grace) dropOffline(in, e.getKey());
            }
            if (in.phase != Instance.Phase.RUNNING) continue;
            if (in.members.isEmpty()) {
                end(in, Outcome.ABANDONED);
                continue;
            }
            for (UUID mob : new ArrayList<>(in.mobs.keySet())) {
                Entity e = Bukkit.getEntity(mob);
                if (e == null || !e.isValid() || e.isDead()) plugin.engine().onMobGone(in, mob, false, null);
            }
            if (in.phase != Instance.Phase.RUNNING) continue;
            updateBossBar(in);
            plugin.engine().tickTimers(in);
        }
    }

    private void updateBossBar(Instance in) {
        if (in.bossBar == null || in.boss == null) return;
        Entity e = Bukkit.getEntity(in.boss);
        if (e instanceof LivingEntity le) {
            AttributeInstance max = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double m = max == null ? 20 : max.getValue();
            in.bossBar.setProgress(Math.max(0, Math.min(1, le.getHealth() / m)));
        }
        for (Player p : in.viewers()) if (!in.bossBar.getPlayers().contains(p)) in.bossBar.addPlayer(p);
    }

    /** Every 10 ticks: area triggers and spectator bounds. */
    public void tickFast() {
        int margin = plugin.settings().spectatorMargin;
        for (Instance in : new ArrayList<>(instances.values())) {
            if (in.phase != Instance.Phase.RUNNING) continue;
            plugin.engine().tickArea(in);
            Box allowed = in.box.grow(margin);
            for (Player p : in.viewers()) {
                if (p.getGameMode() != GameMode.SPECTATOR) continue;
                if (!p.getWorld().equals(in.world) || !allowed.contains(p.getLocation())) {
                    sessions().teleport(p, in.checkpointLocation());
                }
            }
        }
    }

    // ------------------------------------------------------------------ deaths

    /** Called from PlayerDeathEvent for a member: keeps items, spends a life. */
    public void onDeath(Instance in, Player p) {
        UUID u = p.getUniqueId();
        in.deathLocation.put(u, p.getLocation());
        if (in.phase != Instance.Phase.RUNNING || in.stateOf(u) != Instance.State.ALIVE) return;
        in.deaths.merge(u, 1, Integer::sum);
        plugin.engine().onPlayerDeath(in, p);
        DungeonTemplate t = in.template;
        boolean respawn = switch (t.livesMode) {
            case SHARED -> {
                if (in.sharedLives > 0) {
                    in.sharedLives--;
                    yield true;
                }
                yield false;
            }
            case PERSONAL -> {
                int left = in.personalLives.getOrDefault(u, 0);
                if (left > 0) {
                    in.personalLives.put(u, left - 1);
                    yield true;
                }
                yield false;
            }
            case NONE -> false;
        };
        in.states.put(u, respawn ? Instance.State.WAITING : Instance.State.SPECTATOR);
        for (Player v : in.onlineMembers()) {
            if (v.equals(p)) continue;
            msg().send(v, respawn ? "dungeon.member-died" : "dungeon.member-out", "player", p.getName(),
                    "lives", String.valueOf(in.livesOf(v.getUniqueId())));
        }
    }

    /** Where a member respawns (PlayerRespawnEvent). */
    public Location respawnLocation(Instance in, Player p) {
        Location death = in.deathLocation.get(p.getUniqueId());
        if (in.phase == Instance.Phase.RUNNING && in.stateOf(p.getUniqueId()) != Instance.State.ALIVE
                && death != null && death.getWorld() != null && death.getY() > death.getWorld().getMinHeight() + 4
                && in.box.grow(4).contains(death)) {
            return death;
        }
        return in.checkpointLocation();
    }

    /** One tick after respawning: spectator wait or permanent spectator. */
    public void afterRespawn(Instance in, Player p) {
        if (in.phase != Instance.Phase.RUNNING || !in.members.contains(p.getUniqueId())) return;
        Instance.State st = in.stateOf(p.getUniqueId());
        if (st == Instance.State.ALIVE) {
            sessions().teleport(p, in.checkpointLocation());
            return;
        }
        p.setGameMode(GameMode.SPECTATOR);
        if (st == Instance.State.WAITING) {
            scheduleRespawn(in, p.getUniqueId());
        } else {
            msg().title(p, "dungeon.out-title", "dungeon.out-subtitle", 5, 60, 10);
            msg().sendWithButtons(p, "dungeon.out-chat", new String[][]{{"dungeon.button-leave", "/phoban leave"}});
            checkFail(in);
        }
    }

    private void scheduleRespawn(Instance in, UUID u) {
        int delay = in.template.respawnDelay;
        Integer old = in.respawnTasks.remove(u);
        if (old != null) Bukkit.getScheduler().cancelTask(old);
        final int[] left = {delay};
        int task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Player p = Bukkit.getPlayer(u);
            if (in.phase != Instance.Phase.RUNNING || !in.members.contains(u) || p == null
                    || in.stateOf(u) != Instance.State.WAITING) {
                Integer id = in.respawnTasks.remove(u);
                if (id != null) Bukkit.getScheduler().cancelTask(id);
                return;
            }
            if (left[0] <= 0) {
                Integer id = in.respawnTasks.remove(u);
                if (id != null) Bukkit.getScheduler().cancelTask(id);
                revive(in, p);
                return;
            }
            msg().title(p, "dungeon.respawn-title", "dungeon.respawn-subtitle", 0, 25, 0,
                    "seconds", String.valueOf(left[0]), "lives", String.valueOf(in.livesOf(u)));
            left[0]--;
        }, 0L, 20L);
        in.respawnTasks.put(u, task);
    }

    private void revive(Instance in, Player p) {
        in.states.put(p.getUniqueId(), Instance.State.ALIVE);
        sessions().teleport(p, in.checkpointLocation());
        p.setGameMode(GameMode.SURVIVAL);
        AttributeInstance max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.resetTitle();
        msg().send(p, "dungeon.respawned", "lives", String.valueOf(in.livesOf(p.getUniqueId())));
    }

    private void checkFail(Instance in) {
        if (in.phase == Instance.Phase.RUNNING && in.playingCount() == 0) end(in, Outcome.FAIL);
    }

    // ------------------------------------------------------------------ leaving

    public enum LeaveReason { LEAVE, WORLD_CHANGE, GRACE, KICK }

    /** A member or watcher leaves early: no rewards, state restored. */
    public void leave(Player p, LeaveReason reason) {
        UUID u = p.getUniqueId();
        Instance in = byPlayer.get(u);
        if (in == null) return;
        if (in.watchers.remove(u)) {
            byPlayer.remove(u);
            if (in.bossBar != null) in.bossBar.removePlayer(p);
            sessions().restore(p, reason != LeaveReason.WORLD_CHANGE);
            msg().send(p, "admin.watch-stopped");
            return;
        }
        if (in.phase == Instance.Phase.PREPARING) {
            msg().send(p, "dungeon.preparing-wait");
            return;
        }
        removeMember(in, u);
        if (in.bossBar != null) in.bossBar.removePlayer(p);
        sessions().restore(p, reason != LeaveReason.WORLD_CHANGE);
        if (in.phase != Instance.Phase.ENDED) {
            msg().send(p, "dungeon.left-self");
            for (Player v : in.onlineMembers()) msg().send(v, "dungeon.member-left", "player", p.getName());
            afterMemberRemoved(in);
        }
    }

    private void removeMember(Instance in, UUID u) {
        in.members.remove(u);
        in.offlineSince.remove(u);
        in.bag.remove(u);
        byPlayer.remove(u);
        Integer task = in.respawnTasks.remove(u);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
        if (in.phase == Instance.Phase.RUNNING && !in.test) plugin.entry().onEnd(u, in.template, false);
    }

    private void afterMemberRemoved(Instance in) {
        if (in.phase != Instance.Phase.RUNNING) return;
        if (in.members.isEmpty()) end(in, Outcome.ABANDONED);
        else checkFail(in);
    }

    /** Disconnect grace ran out: the member is out (their snapshot restores them when they join again). */
    private void dropOffline(Instance in, UUID u) {
        removeMember(in, u);
        for (Player v : in.onlineMembers()) msg().send(v, "dungeon.member-timed-out", "player", in.names.getOrDefault(u, "?"));
        afterMemberRemoved(in);
    }

    public void onQuit(Player p) {
        UUID u = p.getUniqueId();
        Instance in = byPlayer.get(u);
        if (in == null) return;
        if (in.watchers.remove(u)) {
            byPlayer.remove(u);
            return;
        }
        if (in.phase == Instance.Phase.ENDED) {
            byPlayer.remove(u);
            in.members.remove(u);
            return;
        }
        in.offlineSince.put(u, System.currentTimeMillis());
        Integer task = in.respawnTasks.remove(u);
        if (task != null) Bukkit.getScheduler().cancelTask(task);
        if (in.phase == Instance.Phase.RUNNING) {
            for (Player v : in.onlineMembers()) {
                msg().send(v, "dungeon.member-offline", "player", p.getName(),
                        "time", msg().duration(plugin.settings().graceSeconds));
            }
        }
    }

    /** One tick after join. Returns true when the player went back into a running instance. */
    public boolean onJoin(Player p) {
        UUID u = p.getUniqueId();
        Instance in = byPlayer.get(u);
        if (in == null || !in.members.contains(u) || !in.offlineSince.containsKey(u)) return false;
        in.offlineSince.remove(u);
        if (in.phase == Instance.Phase.PREPARING) return true;
        if (in.phase != Instance.Phase.RUNNING) return false;
        Instance.State st = in.stateOf(u);
        sessions().teleport(p, in.checkpointLocation());
        if (st == Instance.State.SPECTATOR) {
            p.setGameMode(GameMode.SPECTATOR);
        } else {
            in.states.put(u, Instance.State.ALIVE);
            p.setGameMode(GameMode.SURVIVAL);
            p.setAllowFlight(false);
        }
        if (in.bossBar != null) in.bossBar.addPlayer(p);
        msg().send(p, "dungeon.rejoined");
        for (Player v : in.onlineMembers()) if (!v.equals(p)) msg().send(v, "dungeon.member-back", "player", p.getName());
        return true;
    }

    // ------------------------------------------------------------------ watching

    public void watch(Player admin, Instance in) {
        if (byPlayer.containsKey(admin.getUniqueId())) {
            msg().send(admin, "room.in-dungeon");
            return;
        }
        if (in.phase != Instance.Phase.RUNNING) {
            msg().send(admin, "admin.watch-not-running");
            return;
        }
        if (!sessions().save(admin, SessionStore.Kind.WATCH)) return;
        in.watchers.add(admin.getUniqueId());
        byPlayer.put(admin.getUniqueId(), in);
        sessions().teleport(admin, in.checkpointLocation());
        admin.setGameMode(GameMode.SPECTATOR);
        if (in.bossBar != null) in.bossBar.addPlayer(admin);
        msg().sendWithButtons(admin, "admin.watch-started", new String[][]{{"dungeon.button-leave", "/phoban leave"}},
                "id", String.valueOf(in.id), "dungeon", in.template.display);
    }

    // ------------------------------------------------------------------ end

    public void end(Instance in, Outcome outcome) {
        if (in.phase == Instance.Phase.ENDED) return;
        boolean wasRunning = in.phase == Instance.Phase.RUNNING;
        in.phase = Instance.Phase.ENDED;
        in.endMs = System.currentTimeMillis();
        DungeonTemplate t = in.template;
        long seconds = in.elapsedSeconds();
        Map<UUID, Result> results = new HashMap<>();
        for (UUID u : new ArrayList<>(in.members)) {
            Integer task = in.respawnTasks.remove(u);
            if (task != null) Bukkit.getScheduler().cancelTask(task);
            Player p = Bukkit.getPlayer(u);
            boolean online = p != null && !in.offlineSince.containsKey(u);
            boolean won = outcome == Outcome.WIN && online;
            List<String> lines = new ArrayList<>();
            boolean first = false;
            if (won && !in.test) {
                boolean spectating = in.stateOf(u) == Instance.State.SPECTATOR;
                if (!spectating || t.rewardDeadPlayers) {
                    first = plugin.db().recordClear(u, t.id, seconds);
                    RewardService.Table table = plugin.rewards().table(in.difficulty.rewards());
                    String reason = "phoban:" + t.id + "/" + in.difficulty.id();
                    if (table != null) {
                        lines.addAll(plugin.rewards().give(p, table.normal(), reason));
                        if (first) lines.addAll(plugin.rewards().give(p, table.firstClear(), reason + "/first"));
                    } else {
                        plugin.getLogger().warning("Không có bảng thưởng '" + in.difficulty.rewards() + "' (" + t.id + ")");
                    }
                    List<ItemStack> bag = in.bag.getOrDefault(u, List.of());
                    if (!bag.isEmpty()) {
                        plugin.rewards().giveItems(p, new ArrayList<>(bag));
                        for (ItemStack it : bag) lines.add(Text.color(com.dex.boxphoban.util.Items.label(it)));
                    }
                } else {
                    msg().send(p, "dungeon.no-reward-spectator");
                }
            }
            if (wasRunning && !in.test) plugin.entry().onEnd(u, t, won);
            if (online) results.put(u, new Result(t.display, in.difficulty.display(), outcome, seconds,
                    in.deaths.getOrDefault(u, 0), lines, first));
        }
        in.bag.clear();
        if (in.bossBar != null) {
            in.bossBar.removeAll();
            in.bossBar = null;
        }
        removeMobs(in);
        String titleKey = switch (outcome) {
            case WIN -> "dungeon.win";
            case TIMEOUT -> "dungeon.timeout";
            case STOPPED -> "dungeon.stopped";
            default -> "dungeon.fail";
        };
        for (Player p : in.viewers()) {
            msg().title(p, titleKey + "-title", titleKey + "-subtitle", 5, 60, 15, "time", Text.clock(seconds),
                    "dungeon", t.display);
            p.playSound(p.getLocation(), outcome == Outcome.WIN ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH,
                    0.7f, 1f);
        }
        int delay = outcome == Outcome.WIN ? plugin.settings().winDelaySeconds : plugin.settings().endDelaySeconds;
        if (outcome == Outcome.ABANDONED || !plugin.isEnabled()) delay = 0;
        if (delay <= 0) teardown(in, results);
        else Bukkit.getScheduler().runTaskLater(plugin, () -> teardown(in, results), delay * 20L);
    }

    private void teardown(Instance in, Map<UUID, Result> results) {
        for (UUID u : new ArrayList<>(in.members)) {
            byPlayer.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                sessions().restore(p, true);
                Result r = results.get(u);
                if (r != null && plugin.isEnabled()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) plugin.menus().openResult(p, r);
                    }, plugin.settings().resultDelayTicks);
                }
            }
        }
        for (UUID u : new ArrayList<>(in.watchers)) {
            byPlayer.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) sessions().restore(p, true);
        }
        in.members.clear();
        in.watchers.clear();
        instances.remove(in.id);
        cleanup(in);
    }

    private void removeMobs(Instance in) {
        for (UUID mob : new ArrayList<>(in.mobs.keySet())) {
            Entity e = Bukkit.getEntity(mob);
            if (e != null) e.remove();
        }
        in.mobs.clear();
        in.mobTypes.clear();
    }

    /** Removes leftover entities, clears the blocks and frees the slot. */
    private void cleanup(Instance in) {
        removeMobs(in);
        World w = in.world;
        if (w != null) {
            Box b = in.box;
            org.bukkit.util.BoundingBox bb = new org.bukkit.util.BoundingBox(b.min().x(), b.min().y(), b.min().z(),
                    b.max().x() + 1, b.max().y() + 1, b.max().z() + 1);
            for (Entity e : w.getNearbyEntities(bb)) {
                if (e instanceof Player || e instanceof Display) continue;
                e.remove();
            }
            for (long key : in.chunkTickets) {
                int cx = (int) (key >> 32), cz = (int) key;
                Chunk c = w.getChunkAt(cx, cz);
                c.removePluginChunkTicket(plugin);
            }
        }
        in.chunkTickets.clear();
        if (!plugin.isEnabled() || w == null) return; // slot stays dirty, cleared on next start
        plugin.maps().clear(w, in.box, err -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Dọn ô " + in.slot + " lỗi, sẽ thử lại khi khởi động", err);
                return;
            }
            plugin.slots().free(in.slot);
        });
    }

    /** Admin stop. */
    public void stop(Instance in) {
        if (in.phase == Instance.Phase.PREPARING) {
            abort(in, "dungeon.stopped-chat");
            return;
        }
        end(in, Outcome.STOPPED);
    }

    /** Plugin disable: everyone goes back right now; slots are cleared on the next start. */
    public void shutdown() {
        for (Instance in : new ArrayList<>(instances.values())) {
            in.phase = Instance.Phase.ENDED;
            for (Integer task : in.respawnTasks.values()) Bukkit.getScheduler().cancelTask(task);
            if (in.bossBar != null) in.bossBar.removeAll();
            removeMobs(in);
            List<UUID> all = new ArrayList<>(in.members);
            all.addAll(in.watchers);
            for (UUID u : all) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    try {
                        sessions().restore(p, true);
                        msg().send(p, "dungeon.server-stopping");
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "restore " + p.getName(), e);
                    }
                }
            }
            if (in.world != null) {
                for (long key : in.chunkTickets) in.world.getChunkAt((int) (key >> 32), (int) key).removePluginChunkTicket(plugin);
            }
        }
        instances.clear();
        byPlayer.clear();
    }

    /** Tracked mob of any instance (damage multiplier, deaths). */
    public Instance mobOwner(UUID entity) {
        for (Instance in : instances.values()) if (in.mobs.containsKey(entity)) return in;
        return null;
    }

    /** Instance whose box contains this location. */
    public Instance at(Location l) {
        if (!plugin.dungeonWorld().is(l.getWorld())) return null;
        for (Instance in : instances.values()) if (in.box.contains(l)) return in;
        return null;
    }
}
