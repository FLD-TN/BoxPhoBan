package com.dex.boxphoban.listener;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.util.Tags;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;

/** Commands, teleports, blocks, drops, trades and PvP inside dungeons; node interactions. */
public final class ProtectionListener implements Listener {

    private final BoxPhoBan plugin;

    public ProtectionListener(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    private Instance in(Player p) {
        return plugin.instances().of(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (in(p) == null || p.hasPermission("boxphoban.bypass.commands")) return;
        String msg = e.getMessage();
        String label = msg.length() > 1 ? msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT) : "";
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (plugin.settings().commandWhitelist.contains(label)) return;
        e.setCancelled(true);
        plugin.messages().send(p, "dungeon.command-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (plugin.sessions().isInternalTeleport(p.getUniqueId())) return;
        Location to = e.getTo();
        if (to == null) return;
        boolean toDungeon = plugin.dungeonWorld().is(to.getWorld());
        Instance in = in(p);
        if (in == null) {
            // nobody walks into the dungeon world by other means (admins and editors may)
            if (toDungeon && !plugin.dungeonWorld().is(e.getFrom().getWorld())
                    && !p.hasPermission("boxphoban.admin") && !plugin.editor().editing(p.getUniqueId())) {
                e.setCancelled(true);
            }
            return;
        }
        if (!toDungeon) {
            // spectator menu teleports to players in other worlds stay blocked; anything else that takes the
            // player out (an admin /tp, another plugin) counts as leaving and is handled by the world listener
            if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) e.setCancelled(true);
            return;
        }
        if (!in.box.grow(plugin.settings().spectatorMargin).contains(to)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (in(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (in(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (in(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (in(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (in(p) == null) return;
        if (plugin.settings().blockDrop || Tags.get(e.getItemDrop().getItemStack(), Tags.KEY) != null) {
            e.setCancelled(true);
            plugin.messages().actionbar(p, "dungeon.drop-blocked");
        }
    }

    /** Shift + right click on a player (trade plugins) and any other player interaction. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player)) return;
        if (in(e.getPlayer()) != null && plugin.settings().blockPlayerInteract) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Instance in = in(p);
        if (in == null || e.getHand() != EquipmentSlot.HAND) return;
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        boolean handled = plugin.engine().onInteract(in, p, b, e.getItem());
        boolean container = b.getState() instanceof Container;
        if (container && (handled || plugin.settings().blockContainers)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        Entity d = e.getDamager();
        if (d instanceof Player p) attacker = p;
        else if (d instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        Instance a = in(attacker), v = in(victim);
        if (a == null && v == null) return;
        if (!plugin.settings().pvp || a != v) e.setCancelled(true);
    }
}
