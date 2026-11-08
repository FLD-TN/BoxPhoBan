package com.dex.boxphoban.room;

import com.dex.boxphoban.config.Messages;
import com.dex.boxphoban.config.Settings;
import com.dex.boxphoban.data.Database;
import com.dex.boxphoban.reward.Economy;
import com.dex.boxphoban.reward.TicketService;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.TemplateManager;
import com.dex.boxphoban.util.Resets;
import com.dex.boxphoban.util.Text;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Entry checks (permission, clears, cooldown, daily limit, ticket, fee) and paying for a run. */
public final class EntryService {

    /** Why a player cannot enter: a messages.yml key plus placeholders. */
    public record Denial(String key, String... kv) {
    }

    private final Settings settings;
    private final Database db;
    private final TicketService tickets;
    private final Economy economy;
    private final TemplateManager templates;
    private final Messages msg;

    public EntryService(Settings settings, Database db, TicketService tickets, Economy economy, TemplateManager templates, Messages msg) {
        this.settings = settings;
        this.db = db;
        this.tickets = tickets;
        this.economy = economy;
        this.templates = templates;
        this.msg = msg;
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** Seconds until the dungeon can be played again (0 = available). */
    public long cooldownLeft(UUID u, DungeonTemplate t) {
        if (t.cooldownType == Resets.Type.NONE) return 0;
        long at = Resets.availableAt(t.cooldownType, db.lastRun(u, t.id), t.cooldownMinutes, t.resetHour, t.resetDay, settings.zone);
        return Math.max(0, at - now());
    }

    public int dailyUsed(UUID u, DungeonTemplate t) {
        return db.dailyCount(u, t.id, Resets.dayKey(now(), t.resetHour, settings.zone));
    }

    /** Does this player pay the ticket / fee of the room? */
    public static boolean pays(DungeonTemplate t, boolean leader) {
        return t.consume == DungeonTemplate.Consume.EVERY_MEMBER || leader;
    }

    public Denial check(Player p, DungeonTemplate t, boolean leader) {
        if (!t.permission.isEmpty() && !p.hasPermission(t.permission)) return new Denial("entry.no-permission");
        for (String req : t.requiredClears) {
            if (db.stats(p.getUniqueId(), req).clears() <= 0) {
                DungeonTemplate rt = templates.get(req);
                return new Denial("entry.required-clear", "dungeon", rt == null ? req : rt.display);
            }
        }
        if (!p.hasPermission("boxphoban.bypass.cooldown")) {
            long left = cooldownLeft(p.getUniqueId(), t);
            if (left > 0) return new Denial("entry.cooldown", "time", msg.duration(left));
            if (t.dailyLimit > 0 && dailyUsed(p.getUniqueId(), t) >= t.dailyLimit) {
                return new Denial("entry.daily-limit", "limit", String.valueOf(t.dailyLimit));
            }
        }
        if (pays(t, leader)) {
            if (t.ticketEnabled && !p.hasPermission("boxphoban.bypass.ticket")) {
                if (tickets.count(p, t.ticketId) < t.ticketAmount) {
                    return new Denial("entry.no-ticket", "amount", String.valueOf(t.ticketAmount), "ticket", tickets.name(t.ticketId));
                }
            }
            if (t.costMoney > 0 && !economy.has(p, t.costMoney)) {
                return new Denial("entry.no-money", "amount", Text.number(t.costMoney));
            }
        }
        return null;
    }

    /** Takes ticket + fee. Returns a refund action, or null when paying failed (nothing taken). */
    public Runnable pay(Player p, DungeonTemplate t, boolean leader) {
        if (!pays(t, leader)) return () -> { };
        boolean ticket = t.ticketEnabled && !p.hasPermission("boxphoban.bypass.ticket");
        if (ticket && !tickets.take(p, t.ticketId, t.ticketAmount)) return null;
        if (t.costMoney > 0 && !economy.withdraw(p, t.costMoney)) {
            if (ticket) giveBack(p, t);
            return null;
        }
        return () -> {
            if (ticket) giveBack(p, t);
            if (t.costMoney > 0) economy.deposit(p, t.costMoney);
        };
    }

    private void giveBack(Player p, DungeonTemplate t) {
        var it = tickets.create(t.ticketId, t.ticketAmount);
        if (it != null) p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
    }

    /** Marks a started run: daily count + cooldown when it applies on start. */
    public void onStart(UUID u, DungeonTemplate t) {
        if (t.dailyLimit > 0) db.addDaily(u, t.id, Resets.dayKey(now(), t.resetHour, settings.zone));
        if (t.applyOn == DungeonTemplate.ApplyOn.START) db.setLastRun(u, t.id, now());
    }

    /** Run ended for this player; {@code won} = the room cleared the dungeon with them inside. */
    public void onEnd(UUID u, DungeonTemplate t, boolean won) {
        if (t.applyOn == DungeonTemplate.ApplyOn.LEAVE || (won && t.applyOn == DungeonTemplate.ApplyOn.FINISH)) {
            db.setLastRun(u, t.id, now());
        }
    }
}
