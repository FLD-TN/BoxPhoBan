package com.dex.boxphoban.listener;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.Instance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Damage multiplier and deaths of tracked dungeon mobs (kills by a BoxPet pet count for its owner). */
public final class MobListener implements Listener {

    private final BoxPhoBan plugin;

    public MobListener(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) d = shooter;
        Instance in = plugin.instances().mobOwner(d.getUniqueId());
        if (in == null) return;
        double mult = in.difficulty.damageMultiplier();
        if (Math.abs(mult - 1) > 1e-6) e.setDamage(e.getDamage() * mult);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Instance in = plugin.instances().mobOwner(e.getEntity().getUniqueId());
        if (in == null) return;
        Player killer = e.getEntity().getKiller();
        plugin.engine().onMobGone(in, e.getEntity().getUniqueId(), true, killer);
    }
}
