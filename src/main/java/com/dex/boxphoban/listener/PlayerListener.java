package com.dex.boxphoban.listener;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.engine.InstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Deaths, respawns, joins, quits and world changes of players in a dungeon. */
public final class PlayerListener implements Listener {

    private final BoxPhoBan plugin;

    public PlayerListener(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Instance in = plugin.instances().of(p.getUniqueId());
        if (in == null && !plugin.dungeonWorld().is(p.getWorld())) return;
        // Always keep everything inside the dungeon world, whatever the gamerule says.
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        if (in != null && in.members.contains(p.getUniqueId())) plugin.instances().onDeath(in, p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Instance in = plugin.instances().of(p.getUniqueId());
        if (in == null || !in.members.contains(p.getUniqueId())) return;
        e.setRespawnLocation(plugin.instances().respawnLocation(in, p));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) plugin.instances().afterRespawn(in, p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        plugin.rooms().onQuit(p.getUniqueId());
        plugin.instances().onQuit(p);
        if (plugin.editor().editing(p.getUniqueId())) plugin.editor().drop(p.getUniqueId());
        plugin.db().forget(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (plugin.instances().onJoin(p)) return;
            if (plugin.sessions().has(p.getUniqueId())) {
                plugin.sessions().restore(p, true);
                plugin.messages().send(p, "dungeon.restored");
            } else if (plugin.dungeonWorld().is(p.getWorld()) && plugin.instances().of(p.getUniqueId()) == null) {
                plugin.sessions().teleport(p, plugin.settings().fallback());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!plugin.dungeonWorld().is(e.getFrom())) return;
        if (plugin.sessions().isInternalTeleport(p.getUniqueId())) return;
        Instance in = plugin.instances().of(p.getUniqueId());
        if (in != null) {
            plugin.instances().leave(p, InstanceManager.LeaveReason.WORLD_CHANGE);
            return;
        }
        if (plugin.editor().editing(p.getUniqueId())) plugin.editor().exit(p, false);
    }
}
