package com.dex.boxphoban.reward;

import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.data.Database;
import com.dex.boxphoban.util.AtomicYaml;
import com.dex.boxphoban.util.Items;
import com.dex.boxphoban.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** rewards.yml (clear rewards) and loot.yml (chest loot), plus handing items out with the pending store. */
public final class RewardService {

    /** One reward bundle. */
    public record Bundle(double money, long gem, List<String> commands, List<ItemStack> items) {
        static Bundle read(ConfigurationSection s) {
            if (s == null) return new Bundle(0, 0, List.of(), List.of());
            List<ItemStack> items = new ArrayList<>();
            for (String enc : s.getStringList("items")) {
                ItemStack it = Items.decode(enc);
                if (it != null) items.add(it);
            }
            return new Bundle(Math.max(0, s.getDouble("money")), Math.max(0, s.getLong("gem")), s.getStringList("commands"), items);
        }

        boolean empty() {
            return money <= 0 && gem <= 0 && commands.isEmpty() && items.isEmpty();
        }
    }

    public record Table(Bundle normal, Bundle firstClear) {
    }

    private record LootEntry(int weight, ItemStack item, int min, int max) {
    }

    private record LootTable(int rolls, List<LootEntry> entries) {
    }

    private final File rewardsFile;
    private final File lootFile;
    private final Logger log;
    private final Economy economy;
    private final Database db;
    private final Messages msg;
    private final Map<String, Table> tables = new HashMap<>();
    private final Map<String, LootTable> loot = new HashMap<>();
    private YamlConfiguration rewardsYaml;
    private YamlConfiguration lootYaml;

    public RewardService(File rewardsFile, File lootFile, Logger log, Economy economy, Database db, Messages msg) {
        this.rewardsFile = rewardsFile;
        this.lootFile = lootFile;
        this.log = log;
        this.economy = economy;
        this.db = db;
        this.msg = msg;
    }

    public void load(YamlConfiguration rewards, YamlConfiguration lootY) {
        rewardsYaml = rewards;
        lootYaml = lootY;
        tables.clear();
        for (String id : rewards.getKeys(false)) {
            ConfigurationSection s = rewards.getConfigurationSection(id);
            if (s == null) continue;
            tables.put(id, new Table(Bundle.read(s), Bundle.read(s.getConfigurationSection("first-clear"))));
        }
        loot.clear();
        for (String id : lootY.getKeys(false)) {
            ConfigurationSection s = lootY.getConfigurationSection(id);
            if (s == null) continue;
            List<LootEntry> entries = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("entries")) {
                ItemStack it = null;
                Object enc = m.get("item");
                if (enc != null) it = Items.decode(enc.toString());
                Object mat = m.get("material");
                if (it == null && mat != null) {
                    Material mt = Items.material(mat.toString(), null);
                    if (mt != null) it = new ItemStack(mt);
                }
                if (it == null) continue;
                int weight = num(m.get("weight"), 1);
                int min = num(m.get("min"), it.getAmount());
                int max = num(m.get("max"), min);
                entries.add(new LootEntry(Math.max(1, weight), it, Math.max(1, min), Math.max(Math.max(1, min), max)));
            }
            loot.put(id, new LootTable(Math.max(1, s.getInt("rolls", 3)), entries));
        }
    }

    private static int num(Object o, int def) {
        if (o == null) return def;
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Table table(String id) {
        return id == null ? null : tables.get(id);
    }

    public boolean hasLoot(String id) {
        return loot.containsKey(id);
    }

    /** Rolls a loot table (weighted, with replacement). */
    public List<ItemStack> roll(String id) {
        LootTable t = loot.get(id);
        List<ItemStack> out = new ArrayList<>();
        if (t == null || t.entries.isEmpty()) return out;
        int total = 0;
        for (LootEntry e : t.entries) total += e.weight;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < t.rolls; i++) {
            int pick = r.nextInt(total);
            for (LootEntry e : t.entries) {
                pick -= e.weight;
                if (pick < 0) {
                    ItemStack it = e.item.clone();
                    it.setAmount(Math.min(it.getMaxStackSize(), e.min + (e.max > e.min ? r.nextInt(e.max - e.min + 1) : 0)));
                    out.add(it);
                    break;
                }
            }
        }
        return out;
    }

    /** Gives a bundle; returns the lines describing it for the result menu. */
    public List<String> give(Player p, Bundle b, String reason) {
        List<String> lines = new ArrayList<>();
        if (b == null || b.empty()) return lines;
        if (b.money > 0) {
            economy.deposit(p, b.money);
            lines.add(msg.text("reward-line.money", "amount", Text.number(b.money)));
        }
        if (b.gem > 0) {
            economy.depositGem(p, b.gem, reason);
            lines.add(msg.text("reward-line.gem", "amount", Text.number(b.gem)));
        }
        for (String c : b.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", p.getName()));
        }
        if (!b.items.isEmpty()) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack it : b.items) {
                copies.add(it.clone());
                lines.add(Text.color(Items.label(it)));
            }
            giveItems(p, copies);
        }
        return lines;
    }

    /** Adds items to the inventory; whatever does not fit goes to the pending store. True if something overflowed. */
    public boolean giveItems(Player p, List<ItemStack> items) {
        boolean overflow = false;
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            for (ItemStack l : left.values()) {
                db.addPending(p.getUniqueId(), l.serializeAsBytes());
                overflow = true;
            }
        }
        if (overflow) msg.send(p, "rewards.overflow");
        return overflow;
    }

    // ------------------------------------------------------------------ admin edits

    public boolean addRewardItem(String table, ItemStack item) {
        List<String> list = new ArrayList<>(rewardsYaml.getStringList(table + ".items"));
        list.add(Items.encode(item));
        rewardsYaml.set(table + ".items", list);
        return saveYaml(rewardsYaml, rewardsFile);
    }

    public boolean addLootItem(String table, ItemStack item, int weight) {
        List<Map<?, ?>> list = new ArrayList<>(lootYaml.getMapList(table + ".entries"));
        Map<String, Object> e = new java.util.LinkedHashMap<>();
        e.put("weight", weight);
        e.put("min", item.getAmount());
        e.put("max", item.getAmount());
        e.put("item", Items.encode(item));
        list.add(e);
        if (!lootYaml.isSet(table + ".rolls")) lootYaml.set(table + ".rolls", 3);
        lootYaml.set(table + ".entries", list);
        return saveYaml(lootYaml, lootFile);
    }

    private boolean saveYaml(YamlConfiguration y, File f) {
        try {
            AtomicYaml.save(y, f);
            load(rewardsYaml, lootYaml);
            return true;
        } catch (IOException e) {
            log.warning("Không ghi được " + f.getName() + ": " + e.getMessage());
            return false;
        }
    }
}
