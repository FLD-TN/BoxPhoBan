package com.dex.boxphoban.engine;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.ConditionType;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.template.TriggerType;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Tags;
import com.dex.boxphoban.util.Text;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.DespawnMode;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Fires node triggers, checks conditions and runs actions. */
public final class NodeEngine {

    private static final int MAX_DOOR_BLOCKS = 32768;

    private final BoxPhoBan plugin;
    private final NamespacedKey healthKey;
    private final Set<String> warned = new HashSet<>();

    public NodeEngine(BoxPhoBan plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "health");
    }

    private void warnOnce(Instance in, String what) {
        if (warned.add(in.template.id + "|" + what)) {
            plugin.getLogger().warning("[" + in.template.id + "] " + what);
        }
    }

    // ------------------------------------------------------------------ triggers

    private void fire(Instance in, TriggerType type, Player who, Predicate<Node> filter) {
        if (in.phase != Instance.Phase.RUNNING) return;
        for (Node n : new ArrayList<>(in.template.nodes.values())) {
            if (n.triggerType() != type) continue;
            if (filter != null && !filter.test(n)) continue;
            tryRun(in, n, who);
        }
    }

    /** Runs a node if its once / per-player rule and conditions allow it. */
    private boolean tryRun(Instance in, Node n, Player who) {
        if (in.disabledNodes.contains(n.id)) return false;
        Instance.NodeState st = in.state(n.id);
        if (!n.repeat) {
            if (n.perPlayer) {
                if (who == null || st.firedPlayers.contains(who.getUniqueId())) return false;
            } else if (st.fired) {
                return false;
            }
        }
        for (Element c : n.conditions) {
            if (!check(in, c)) return false;
        }
        st.fired = true;
        if (who != null) st.firedPlayers.add(who.getUniqueId());
        if (n.delay > 0) {
            UUID wu = who == null ? null : who.getUniqueId();
            in.pendingDelayed++;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                in.pendingDelayed = Math.max(0, in.pendingDelayed - 1);
                if (in.phase != Instance.Phase.RUNNING) return;
                run(in, n, wu == null ? null : Bukkit.getPlayer(wu));
            }, n.delay);
        } else {
            run(in, n, who);
        }
        return true;
    }

    public void onStart(Instance in) {
        fire(in, TriggerType.DUNGEON_START, null, null);
    }

    public void onStageStart(Instance in, String stageId) {
        for (Node n : in.template.nodes.values()) {
            if (n.triggerType() == TriggerType.TIMER && n.trigger.s("from", "dungeon").equals("stage")) {
                Instance.NodeState st = in.state(n.id);
                st.counter = 0;
                if (n.repeat) st.fired = false;
            }
        }
        fire(in, TriggerType.STAGE_START, null, n -> n.trigger.s("stage", "").equalsIgnoreCase(stageId));
    }

    public void onRoomSealed(Instance in, String room) {
        fire(in, TriggerType.ROOM_SEALED, null, n -> n.trigger.s("room", "").equalsIgnoreCase(room));
    }

    /** Called once per stage when every mob spawned during it is dead (and no delayed node is pending). */
    public void onStageCleared(Instance in) {
        String current = in.stage() == null ? "" : in.stage().id;
        fire(in, TriggerType.STAGE_CLEARED, null, n -> {
            String want = n.trigger.s("stage", "");
            return want.isEmpty() || want.equalsIgnoreCase(current);
        });
    }

    public void onPlayerDeath(Instance in, Player p) {
        fire(in, TriggerType.PLAYER_DEATH, p, null);
    }

    public void signal(Instance in, String name, Player who) {
        fire(in, TriggerType.SIGNAL, who, n -> n.trigger.s("name", "").equalsIgnoreCase(name));
    }

    /** Every 10 ticks: area triggers. */
    public void tickArea(Instance in) {
        if (in.phase != Instance.Phase.RUNNING) return;
        List<Player> alive = in.alivePlayers();
        if (alive.isEmpty()) return;
        for (Node n : new ArrayList<>(in.template.nodes.values())) {
            if (n.triggerType() != TriggerType.AREA) continue;
            Instance.NodeState st = in.state(n.id);
            if (!n.repeat && !n.perPlayer && st.fired) continue;
            List<Player> inside = new ArrayList<>();
            String region = n.trigger.s("region", "");
            Box rel = region.isEmpty() ? null : in.template.regions.get(region);
            if (!region.isEmpty() && rel == null) {
                warnOnce(in, "Node " + n.id + ": không có vùng " + region);
                continue;
            }
            Box abs = rel == null ? null : in.absolute(rel);
            Location center = in.blockCenter(n.pos);
            double r = n.trigger.d("radius", 4);
            for (Player p : alive) {
                Location l = p.getLocation();
                if (abs != null ? abs.contains(l) : l.distanceSquared(center) <= r * r) inside.add(p);
            }
            if (inside.isEmpty()) continue;
            String need = n.trigger.s("players", "1");
            int required = need.equalsIgnoreCase("all") ? alive.size() : parseInt(need, 1);
            if (inside.size() < Math.max(1, required)) continue;
            if (n.perPlayer) {
                for (Player p : inside) {
                    if (n.repeat || !st.firedPlayers.contains(p.getUniqueId())) tryRun(in, n, p);
                }
            } else {
                tryRun(in, n, inside.get(0));
            }
        }
    }

    /** Right after the paste: pick the nodes of every random group, switch the others off. */
    public void pickGroups(Instance in) {
        java.util.Map<String, List<Node>> groups = new java.util.HashMap<>();
        for (Node n : in.template.nodes.values()) {
            if (!n.group.isEmpty()) groups.computeIfAbsent(n.group.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(n);
        }
        for (List<Node> list : groups.values()) {
            int pick = 1;
            for (Node n : list) pick = Math.max(pick, n.groupPick);
            java.util.Collections.shuffle(list, ThreadLocalRandom.current());
            for (int i = pick; i < list.size(); i++) {
                Node off = list.get(i);
                in.disabledNodes.add(off.id);
                if (off.groupRemoveBlock) off.pos.plus(in.origin).block(in.world).setType(Material.AIR, false);
            }
        }
    }

    /** Every 10 ticks: hold-area triggers (someone must stay inside the region long enough). */
    public void tickHold(Instance in) {
        if (in.phase != Instance.Phase.RUNNING) return;
        List<Player> alive = in.alivePlayers();
        String text = "";
        for (Node n : new ArrayList<>(in.template.nodes.values())) {
            if (n.triggerType() != TriggerType.HOLD_AREA || in.disabledNodes.contains(n.id)) continue;
            Instance.NodeState st = in.state(n.id);
            if (st.fired && !n.repeat) continue;
            boolean allowed = true;
            for (Element c : n.conditions) if (!check(in, c)) allowed = false;
            if (!allowed) continue;
            Box rel = in.template.regions.get(n.trigger.s("region", ""));
            if (rel == null) {
                warnOnce(in, "Node " + n.id + ": không có vùng " + n.trigger.s("region", ""));
                continue;
            }
            Box box = in.absolute(rel);
            int inside = 0;
            for (Player p : alive) if (box.contains(p.getLocation())) inside++;
            int need = Math.max(1, n.trigger.i("players", 1));
            int total = Math.max(1, n.trigger.i("seconds", 30)) * 20;
            if (inside >= need) st.counter += 10;
            else if (n.trigger.s("mode", "reset").equals("reset")) st.counter = 0;
            if (n.trigger.b("border", true)) border(in, box, inside >= need);
            int secs = Math.min(total, st.counter) / 20;
            if (st.counter > 0 || inside > 0) {
                String line = plugin.messages().text(inside >= need ? "dungeon.hold-progress" : "dungeon.hold-paused",
                        "seconds", String.valueOf(secs), "total", String.valueOf(total / 20),
                        "bar", bar(secs, total / 20), "inside", String.valueOf(inside), "need", String.valueOf(need));
                for (Player p : in.onlineMembers()) p.sendActionBar(Text.component(line));
                text = secs + "/" + total / 20;
            }
            if (st.counter >= total) {
                st.counter = 0;
                tryRun(in, n, null);
            }
        }
        in.holdText = text;
    }

    private static String bar(int done, int total) {
        int filled = total <= 0 ? 20 : Math.min(20, done * 20 / total);
        return Text.color("&#7CFC9A" + "|".repeat(filled) + "&8" + "|".repeat(20 - filled));
    }

    private void border(Instance in, Box b, boolean active) {
        org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                active ? org.bukkit.Color.fromRGB(0x7CFC9A) : org.bukkit.Color.fromRGB(0xFFD166), 1.2f);
        double y = b.min().y() + 0.2;
        double x0 = b.min().x(), x1 = b.max().x() + 1, z0 = b.min().z(), z1 = b.max().z() + 1;
        List<Player> viewers = in.viewers();
        for (double x = x0; x <= x1; x += 0.5) {
            for (Player p : viewers) {
                p.spawnParticle(org.bukkit.Particle.DUST, x, y, z0, 1, 0, 0, 0, 0, dust);
                p.spawnParticle(org.bukkit.Particle.DUST, x, y, z1, 1, 0, 0, 0, 0, dust);
            }
        }
        for (double z = z0; z <= z1; z += 0.5) {
            for (Player p : viewers) {
                p.spawnParticle(org.bukkit.Particle.DUST, x0, y, z, 1, 0, 0, 0, 0, dust);
                p.spawnParticle(org.bukkit.Particle.DUST, x1, y, z, 1, 0, 0, 0, 0, dust);
            }
        }
    }

    /** Every second: timer triggers. */
    public void tickTimers(Instance in) {
        if (in.phase != Instance.Phase.RUNNING) return;
        for (Node n : new ArrayList<>(in.template.nodes.values())) {
            if (n.triggerType() != TriggerType.TIMER) continue;
            int seconds = Math.max(1, n.trigger.i("seconds", 30));
            boolean fromStage = n.trigger.s("from", "dungeon").equals("stage");
            if (fromStage) {
                String stage = n.trigger.s("stage", "");
                if (!stage.isEmpty() && (in.stage() == null || !in.stage().id.equalsIgnoreCase(stage))) continue;
                if (in.stage() == null) continue;
            }
            long elapsed = fromStage ? in.stageElapsedSeconds() : in.elapsedSeconds();
            Instance.NodeState st = in.state(n.id);
            if (n.repeat) {
                if (elapsed >= (long) seconds * (st.counter + 1)) {
                    st.counter++;
                    tryRun(in, n, null);
                }
            } else if (!st.fired && elapsed >= seconds) {
                tryRun(in, n, null);
            }
        }
    }

    /** A tracked mob died or vanished; {@code killed} = it died (counts for kill triggers). */
    public void onMobGone(Instance in, UUID entity, boolean killed, Player killer) {
        String nodeId = in.mobs.remove(entity);
        String type = in.mobTypes.remove(entity);
        in.stageMobs.remove(entity);
        if (nodeId == null) return;
        if (entity.equals(in.boss)) {
            in.boss = null;
            if (in.bossBar != null) {
                in.bossBar.removeAll();
                in.bossBar = null;
            }
        }
        if (in.phase != Instance.Phase.RUNNING) return;
        if (killed) {
            for (Node n : new ArrayList<>(in.template.nodes.values())) {
                if (n.triggerType() != TriggerType.KILL_MOB) continue;
                String want = n.trigger.s("mob", "*");
                if (!want.equals("*") && (type == null || !want.equalsIgnoreCase(type))) continue;
                Instance.NodeState st = in.state(n.id);
                st.counter++;
                if (st.counter >= Math.max(1, n.trigger.i("amount", 1))) {
                    if (tryRun(in, n, killer) && n.repeat) st.counter = 0;
                }
            }
        }
        Instance.NodeState spawner = in.state(nodeId);
        spawner.spawned.remove(entity);
        if (spawner.spawned.isEmpty() && spawner.everSpawned) {
            fire(in, TriggerType.WAVE_CLEARED, killer, n -> n.trigger.s("node", "").equalsIgnoreCase(nodeId));
        }
    }

    /** Right click on a block. Returns true when a node handled it (the click is then cancelled for containers). */
    public boolean onInteract(Instance in, Player p, Block block, ItemStack hand) {
        if (in.phase != Instance.Phase.RUNNING) return false;
        var rel = in.toRelative(com.dex.boxphoban.util.BlockPos.of(block));
        Node n = in.template.nodeAt(rel);
        if (n != null && in.disabledNodes.contains(n.id)) n = null;
        if (n != null && n.triggerType() != TriggerType.INTERACT && n.triggerType() != TriggerType.KEY_USE) n = null;
        if (n == null) {
            // a node may listen on a whole region (e.g. every block of a locked gate)
            for (Node c : in.template.nodes.values()) {
                TriggerType ct = c.triggerType();
                if (ct != TriggerType.INTERACT && ct != TriggerType.KEY_USE) continue;
                if (in.disabledNodes.contains(c.id)) continue;
                Box r = in.template.regions.get(c.trigger.s("region", ""));
                if (r != null && r.contains(rel)) {
                    n = c;
                    break;
                }
            }
        }
        if (n == null) return false;
        TriggerType t = n.triggerType();
        if (t == TriggerType.INTERACT) {
            tryRun(in, n, p);
            return true;
        }
        if (t == TriggerType.KEY_USE) {
            String want = in.keyId(n.trigger.s("key", "chia"));
            String has = Tags.get(hand, Tags.KEY);
            if (!want.equals(has)) {
                plugin.messages().actionbar(p, "dungeon.need-key");
                return true;
            }
            if (tryRun(in, n, p) && n.trigger.b("consume", true) && hand != null) {
                hand.setAmount(hand.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ conditions

    private boolean check(Instance in, Element c) {
        ConditionType type = ConditionType.parse(c.type);
        if (type == null) return true;
        return switch (type) {
            case DIFFICULTY -> {
                boolean match = false;
                for (String id : c.s("ids", "").split(",")) {
                    if (id.trim().equalsIgnoreCase(in.difficulty.id())) match = true;
                }
                yield match != c.b("negate", false);
            }
            case PLAYERS_IN_REGION -> {
                Box rel = in.template.regions.get(c.s("region", ""));
                if (rel == null) yield false;
                Box abs = in.absolute(rel);
                int count = 0;
                for (Player p : in.alivePlayers()) if (abs.contains(p.getLocation())) count++;
                yield count >= c.i("min", 1);
            }
            case CHANCE -> ThreadLocalRandom.current().nextDouble(100) < c.d("percent", 50);
            case TIME_ELAPSED -> in.elapsedSeconds() >= c.i("seconds", 60);
            case STAGE_IS -> in.stage() != null && in.stage().id.equalsIgnoreCase(c.s("stage", ""));
        };
    }

    // ------------------------------------------------------------------ actions

    private void run(Instance in, Node n, Player who) {
        for (Element a : n.actions) {
            if (in.phase != Instance.Phase.RUNNING) return;
            try {
                act(in, n, a, who);
            } catch (Exception e) {
                warnOnce(in, "Node " + n.id + " hành động " + a.type + " lỗi: " + e);
            }
        }
    }

    private void act(Instance in, Node n, Element a, Player who) {
        ActionType type = ActionType.parse(a.type);
        if (type == null) return;
        DungeonTemplate t = in.template;
        switch (type) {
            case SPAWN_MOB -> {
                int amount = Math.max(1, Math.min(100, a.i("amount", 1)));
                double radius = Math.max(0, a.d("radius", 3));
                for (int i = 0; i < amount; i++) {
                    Location l = in.blockCenter(n.pos);
                    if (radius > 0) {
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        l.add(r.nextDouble(-radius, radius), 0, r.nextDouble(-radius, radius));
                    }
                    spawn(in, n, a.s("mob", ""), a.i("level", 0), l);
                }
            }
            case SPAWN_BOSS -> {
                LivingEntity boss = spawn(in, n, a.s("mob", ""), a.i("level", 0), in.blockCenter(n.pos));
                if (boss != null) {
                    if (in.bossBar != null) in.bossBar.removeAll();
                    BarColor color;
                    try {
                        color = BarColor.valueOf(a.s("bar-color", "RED").toUpperCase(Locale.ROOT));
                    } catch (Exception e) {
                        color = BarColor.RED;
                    }
                    String name = boss.customName() == null ? a.s("mob", "Boss")
                            : net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(boss.customName());
                    in.bossBar = Bukkit.createBossBar(Text.color(name), color, BarStyle.SOLID);
                    in.boss = boss.getUniqueId();
                    for (Player p : in.viewers()) in.bossBar.addPlayer(p);
                }
            }
            case WALL_CLOSE, WALL_OPEN -> {
                boolean close = type == ActionType.WALL_CLOSE;
                int ticks = (int) Math.round(Math.max(0.05, a.d("seconds", 2)) * 20);
                for (String r : Walls.names(a.s("region", ""))) {
                    plugin.walls().move(in, r, close, Walls.style(a.s("style", "down")), ticks, a.s("sound", ""),
                            in.blockCenter(n.pos));
                }
            }
            case ROOM_LOCK -> {
                String room = a.s("room", "");
                if (!t.regions.containsKey(room)) {
                    warnOnce(in, "Node " + n.id + ": không có vùng phòng " + room);
                    return;
                }
                int ticks = (int) Math.round(Math.max(0.05, a.d("seconds", 2)) * 20);
                plugin.walls().lock(in, new Walls.Lock(room, Walls.names(a.s("entrances", "")), Walls.names(a.s("exits", "")),
                        Walls.style(a.s("style", "down")), ticks, a.s("sound", ""), in.blockCenter(n.pos)));
            }
            case ROOM_UNLOCK -> plugin.walls().unlock(in, a.s("room", ""));
            case DOOR_OPEN -> door(in, a.s("region", ""), true, null);
            case DOOR_CLOSE -> door(in, a.s("region", ""), false, a.s("material", ""));
            case CHECKPOINT -> {
                String pt = a.s("point", "");
                Point p = pt.isEmpty() ? null : t.points.get(pt);
                if (!pt.isEmpty() && p == null) {
                    warnOnce(in, "Node " + n.id + ": không có điểm " + pt);
                    return;
                }
                if (p == null) p = new Point(n.pos.x() + 0.5, n.pos.y() + 1, n.pos.z() + 0.5, 0, 0);
                in.checkpoint = p;
                for (Player v : in.onlineMembers()) plugin.messages().send(v, "dungeon.checkpoint");
            }
            case SET_STAGE -> {
                String s = a.s("stage", "next");
                int idx = s.equalsIgnoreCase("next") ? in.stageIndex + 1 : t.stageIndex(s);
                if (idx < 0 || idx >= t.stages.size()) {
                    warnOnce(in, "Node " + n.id + ": không có ải " + s);
                    return;
                }
                plugin.instances().setStage(in, idx);
            }
            case SET_OBJECTIVE -> {
                String text = a.s("text", "");
                in.objectiveOverride = text.isEmpty() ? null : text;
            }
            case MESSAGE -> {
                String text = a.s("text", "").replace("%player%", who == null ? "" : who.getName());
                String sub = a.s("subtitle", "").replace("%player%", who == null ? "" : who.getName());
                String mode = a.s("mode", "chat");
                for (Player v : in.viewers()) {
                    switch (mode) {
                        case "title" -> v.showTitle(Title.title(Text.component(text), Text.component(sub),
                                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(500))));
                        case "actionbar" -> v.sendActionBar(Text.component(text));
                        default -> v.sendMessage(Text.component(text));
                    }
                }
            }
            case SOUND -> {
                String key = a.s("sound", "entity.experience_orb.pickup").toLowerCase(Locale.ROOT);
                float vol = (float) a.d("volume", 1), pitch = (float) a.d("pitch", 1);
                for (Player v : in.viewers()) v.playSound(v.getLocation(), key, vol, pitch);
            }
            case TELEPORT -> {
                Point p = t.points.get(a.s("point", ""));
                if (p == null) {
                    warnOnce(in, "Node " + n.id + ": không có điểm " + a.s("point", ""));
                    return;
                }
                Location to = in.loc(p);
                if (a.s("who", "all").equals("trigger")) {
                    if (who != null) plugin.sessions().teleport(who, to);
                } else {
                    for (Player v : in.onlineMembers()) plugin.sessions().teleport(v, to);
                }
            }
            case GIVE_KEY -> {
                ItemStack key = Items.make(Material.TRIAL_KEY, a.s("name", "&#FFD166Chìa khoá"),
                        plugin.messages().list("dungeon.key-lore"));
                Tags.set(key, Tags.KEY, in.keyId(a.s("key", "chia")));
                List<Player> to = a.s("who", "trigger").equals("all") || who == null ? in.onlineMembers() : List.of(who);
                for (Player v : to) {
                    v.getInventory().addItem(key.clone()).values().forEach(l -> v.getWorld().dropItem(v.getLocation(), l));
                    plugin.messages().send(v, "dungeon.key-received", "key", a.s("name", "Chìa khoá"));
                }
                if (to.size() == 1) {
                    for (Player v : in.onlineMembers()) {
                        if (!v.equals(to.get(0))) plugin.messages().send(v, "dungeon.key-received-other",
                                "player", to.get(0).getName(), "key", a.s("name", "Chìa khoá"));
                    }
                }
            }
            case LIVES -> plugin.instances().addLives(in, a.i("amount", 1));
            case SIGNAL -> signal(in, a.s("name", ""), who);
            case COMMAND -> {
                String cmd = a.s("command", "");
                if (cmd.isEmpty()) return;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                switch (a.s("who", "trigger")) {
                    case "all" -> {
                        for (Player v : in.onlineMembers()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", v.getName()));
                        }
                    }
                    case "once" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            cmd.replace("%player%", who == null ? "" : who.getName()));
                    default -> {
                        if (who != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", who.getName()));
                    }
                }
            }
            case LOOT -> {
                if (who == null) return;
                String table = a.s("table", "");
                if (!plugin.rewards().hasLoot(table)) {
                    warnOnce(in, "Node " + n.id + ": không có bảng loot " + table);
                    return;
                }
                List<ItemStack> items = plugin.rewards().roll(table);
                in.bag.computeIfAbsent(who.getUniqueId(), k -> new ArrayList<>()).addAll(items);
                plugin.menus().openLoot(who, items);
            }
            case FINISH -> plugin.instances().end(in, InstanceManager.Outcome.WIN);
            case FAIL -> plugin.instances().end(in, InstanceManager.Outcome.FAIL);
        }
    }

    private LivingEntity spawn(Instance in, Node n, String mobId, int levelBonus, Location at) {
        if (mobId.isEmpty() || MythicBukkit.inst().getMobManager().getMythicMob(mobId).isEmpty()) {
            warnOnce(in, "Node " + n.id + ": không có mob MythicMobs '" + mobId + "'");
            return null;
        }
        Chunk c = at.getChunk();
        long ck = ((long) c.getX() << 32) | (c.getZ() & 0xffffffffL);
        if (in.chunkTickets.add(ck)) c.addPluginChunkTicket(plugin);
        double level = Math.max(1, in.difficulty.mobLevel() + levelBonus);
        ActiveMob am = MythicBukkit.inst().getMobManager().getMythicMob(mobId).get().spawn(BukkitAdapter.adapt(at), level);
        if (am == null) return null;
        try {
            am.setDespawnMode(DespawnMode.NEVER);
        } catch (Exception ignored) {
            // older MM: keep default
        }
        Entity e = am.getEntity().getBukkitEntity();
        if (!(e instanceof LivingEntity le)) return null;
        double mult = in.difficulty.healthMultiplier();
        if (Math.abs(mult - 1) > 1e-6) {
            AttributeInstance max = le.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                max.removeModifier(healthKey);
                max.addModifier(new AttributeModifier(healthKey, mult - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                le.setHealth(max.getValue());
            }
        }
        le.setRemoveWhenFarAway(false);
        in.mobs.put(le.getUniqueId(), n.id);
        in.mobTypes.put(le.getUniqueId(), mobId);
        in.stageMobs.add(le.getUniqueId());
        in.stageSpawned = true;
        Instance.NodeState st = in.state(n.id);
        st.spawned.add(le.getUniqueId());
        st.everSpawned = true;
        return le;
    }

    private void door(Instance in, String region, boolean open, String material) {
        Box rel = in.template.regions.get(region);
        if (rel == null) {
            warnOnce(in, "Không có vùng cửa " + region);
            return;
        }
        Box b = in.absolute(rel);
        if (b.volume() > MAX_DOOR_BLOCKS) {
            warnOnce(in, "Vùng cửa " + region + " quá lớn (" + b.volume() + " block)");
            return;
        }
        if (open) {
            List<BlockData> saved = new ArrayList<>();
            boolean save = !in.savedDoors.containsKey(region);
            for (int x = b.min().x(); x <= b.max().x(); x++)
                for (int y = b.min().y(); y <= b.max().y(); y++)
                    for (int z = b.min().z(); z <= b.max().z(); z++) {
                        Block bl = in.world.getBlockAt(x, y, z);
                        if (save) saved.add(bl.getBlockData());
                        bl.setType(Material.AIR, false);
                    }
            if (save) in.savedDoors.put(region, saved);
            return;
        }
        Material fill = material == null || material.isEmpty() ? null : Material.matchMaterial(material);
        List<BlockData> saved = in.savedDoors.get(region);
        int i = 0;
        for (int x = b.min().x(); x <= b.max().x(); x++)
            for (int y = b.min().y(); y <= b.max().y(); y++)
                for (int z = b.min().z(); z <= b.max().z(); z++) {
                    Block bl = in.world.getBlockAt(x, y, z);
                    if (fill != null && fill.isBlock()) bl.setType(fill, false);
                    else if (saved != null && i < saved.size()) bl.setBlockData(saved.get(i), false);
                    i++;
                }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
