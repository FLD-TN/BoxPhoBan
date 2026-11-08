package com.dex.boxphoban;

import com.dex.boxphoban.command.AdminCommand;
import com.dex.boxphoban.command.PhoBanCommand;
import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.config.Settings;
import com.dex.boxphoban.config.YamlFiles;
import com.dex.boxphoban.data.Database;
import com.dex.boxphoban.editor.EditorService;
import com.dex.boxphoban.engine.InstanceManager;
import com.dex.boxphoban.engine.NodeEngine;
import com.dex.boxphoban.gui.ChatInput;
import com.dex.boxphoban.gui.MenuListener;
import com.dex.boxphoban.gui.MenuService;
import com.dex.boxphoban.gui.Words;
import com.dex.boxphoban.hook.PhoBanExpansion;
import com.dex.boxphoban.listener.MobListener;
import com.dex.boxphoban.listener.PlayerListener;
import com.dex.boxphoban.listener.ProtectionListener;
import com.dex.boxphoban.player.SessionStore;
import com.dex.boxphoban.reward.Economy;
import com.dex.boxphoban.reward.RewardService;
import com.dex.boxphoban.reward.TicketService;
import com.dex.boxphoban.room.EntryService;
import com.dex.boxphoban.room.RoomManager;
import com.dex.boxphoban.template.TemplateManager;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Tags;
import com.dex.boxphoban.world.DungeonWorld;
import com.dex.boxphoban.world.MapService;
import com.dex.boxphoban.world.SlotPool;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;

/** BoxPhoBan: room based dungeons. */
public final class BoxPhoBan extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private Messages gui;
    private Database db;
    private Economy economy;
    private TicketService tickets;
    private RewardService rewards;
    private TemplateManager templates;
    private DungeonWorld dungeonWorld;
    private SlotPool slots;
    private MapService maps;
    private SessionStore sessions;
    private EntryService entry;
    private RoomManager rooms;
    private InstanceManager instances;
    private NodeEngine engine;
    private com.dex.boxphoban.engine.Walls walls;
    private EditorService editor;
    private MenuService menus;
    private Words words;
    private ChatInput input;

    @Override
    public void onEnable() {
        Tags.init(this);
        settings = new Settings(YamlFiles.load(this, "config.yml"));
        messages = new Messages(YamlFiles.load(this, "messages.yml"));
        gui = new Messages(YamlFiles.load(this, "gui.yml"));
        try {
            db = new Database(new File(getDataFolder(), "data/boxphoban.db"), getLogger());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Không mở được SQLite, BoxPhoBan tắt", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        economy = new Economy(getLogger());
        tickets = new TicketService();
        tickets.load(YamlFiles.loadPlain(this, "tickets.yml"));
        rewards = new RewardService(new File(getDataFolder(), "rewards.yml"), new File(getDataFolder(), "loot.yml"),
                getLogger(), economy, db, messages);
        rewards.load(YamlFiles.loadPlain(this, "rewards.yml"), YamlFiles.loadPlain(this, "loot.yml"));
        templates = new TemplateManager(this);
        templates.loadAll();

        dungeonWorld = new DungeonWorld(settings);
        World w = dungeonWorld.load();
        if (w == null) getLogger().severe("Không tạo được world " + settings.worldName);
        maps = new MapService(this);
        slots = new SlotPool(new File(getDataFolder(), "data/slots.yml"), getLogger());
        Map<Integer, Box> leftovers = slots.loadLeftovers();
        if (w != null) {
            for (Map.Entry<Integer, Box> e : leftovers.entrySet()) {
                int slot = e.getKey();
                getLogger().info("Dọn ô " + slot + " còn sót từ lần chạy trước");
                maps.clear(w, e.getValue(), err -> {
                    if (err == null) slots.free(slot);
                    else getLogger().log(Level.WARNING, "Dọn ô " + slot + " lỗi", err);
                });
            }
        }

        sessions = new SessionStore(new File(getDataFolder(), "data/sessions"), settings, getLogger());
        entry = new EntryService(settings, db, tickets, economy, templates, messages);
        engine = new NodeEngine(this);
        walls = new com.dex.boxphoban.engine.Walls(this);
        instances = new InstanceManager(this);
        rooms = new RoomManager(this);
        editor = new EditorService(this);
        menus = new MenuService(this);
        words = new Words(this);
        input = new ChatInput(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(input, this);
        pm.registerEvents(editor, this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new MobListener(this), this);

        PhoBanCommand pc = new PhoBanCommand(this);
        PluginCommand c = getCommand("phoban");
        if (c != null) {
            c.setExecutor(pc);
            c.setTabCompleter(pc);
        }
        AdminCommand ac = new AdminCommand(this);
        PluginCommand a = getCommand("phobanadmin");
        if (a != null) {
            a.setExecutor(ac);
            a.setTabCompleter(ac);
        }
        if (pm.getPlugin("PlaceholderAPI") != null) new PhoBanExpansion(this).register();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            rooms.tick();
            instances.tickSecond();
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            instances.tickFast();
            editor.tickParticles();
        }, 10L, 10L);
        // services of other plugins (Vault economy, BoxCore gems) are registered by the time the first tick runs
        Bukkit.getScheduler().runTask(this, () -> economy.hook());

        // /reload or a crash while players were inside: put everyone online back now
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.has(p.getUniqueId())) {
                sessions.restore(p, true);
                messages.send(p, "dungeon.restored");
            }
        }
    }

    @Override
    public void onDisable() {
        try {
            if (instances != null) instances.shutdown();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "shutdown instances", e);
        }
        try {
            if (editor != null) editor.shutdown();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "shutdown editor", e);
        }
        if (rooms != null) rooms.shutdown();
        if (db != null) db.close();
    }

    /** /pba reload: texts, tickets, rewards, loot, dungeon.yml files and most of config.yml (world.* needs a restart). */
    public void reload() {
        settings = new Settings(YamlFiles.load(this, "config.yml"));
        messages = new Messages(YamlFiles.load(this, "messages.yml"));
        gui = new Messages(YamlFiles.load(this, "gui.yml"));
        tickets.load(YamlFiles.loadPlain(this, "tickets.yml"));
        rewards = new RewardService(new File(getDataFolder(), "rewards.yml"), new File(getDataFolder(), "loot.yml"),
                getLogger(), economy, db, messages);
        rewards.load(YamlFiles.loadPlain(this, "rewards.yml"), YamlFiles.loadPlain(this, "loot.yml"));
        entry = new EntryService(settings, db, tickets, economy, templates, messages);
        templates.reloadSettings();
        economy.hook();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Messages gui() {
        return gui;
    }

    public Database db() {
        return db;
    }

    public TicketService tickets() {
        return tickets;
    }

    public RewardService rewards() {
        return rewards;
    }

    public TemplateManager templates() {
        return templates;
    }

    public DungeonWorld dungeonWorld() {
        return dungeonWorld;
    }

    public SlotPool slots() {
        return slots;
    }

    public MapService maps() {
        return maps;
    }

    public SessionStore sessions() {
        return sessions;
    }

    public EntryService entry() {
        return entry;
    }

    public RoomManager rooms() {
        return rooms;
    }

    public InstanceManager instances() {
        return instances;
    }

    public NodeEngine engine() {
        return engine;
    }

    public com.dex.boxphoban.engine.Walls walls() {
        return walls;
    }

    public EditorService editor() {
        return editor;
    }

    public MenuService menus() {
        return menus;
    }

    public Words words() {
        return words;
    }

    public ChatInput input() {
        return input;
    }
}
