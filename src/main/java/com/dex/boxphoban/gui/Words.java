package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.util.Text;
import org.bukkit.entity.Player;

/** Shared texts of the GUIs (gui.yml words.*). */
public final class Words {

    private final BoxPhoBan plugin;

    public Words(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    private String w(String key, String... kv) {
        return Text.replace(plugin.gui().raw("words." + key), kv);
    }

    public String status(boolean open) {
        return w(open ? "public" : "private");
    }

    public String entry(DungeonTemplate t) {
        StringBuilder sb = new StringBuilder();
        if (t.ticketEnabled) {
            sb.append(w(t.consume == DungeonTemplate.Consume.EVERY_MEMBER ? "ticket-each" : "ticket-leader",
                    "amount", String.valueOf(t.ticketAmount), "ticket", plugin.tickets().name(t.ticketId)));
        }
        if (t.costMoney > 0) {
            if (!sb.isEmpty()) sb.append(w("and"));
            sb.append(w("fee", "amount", Text.number(t.costMoney)));
        }
        return sb.isEmpty() ? w("free") : sb.toString();
    }

    public String lives(DungeonTemplate t) {
        return switch (t.livesMode) {
            case SHARED -> w("lives-shared", "amount", String.valueOf(t.lives));
            case PERSONAL -> w("lives-personal", "amount", String.valueOf(t.lives));
            case NONE -> w("lives-none");
        };
    }

    public String time(Difficulty d) {
        return d.timeLimit() <= 0 ? w("no-limit") : plugin.messages().duration(d.timeLimit());
    }

    public String cooldown(Player p, DungeonTemplate t) {
        long left = plugin.entry().cooldownLeft(p.getUniqueId(), t);
        if (left > 0) return w("cooldown", "time", plugin.messages().duration(left));
        if (t.dailyLimit > 0) {
            int used = plugin.entry().dailyUsed(p.getUniqueId(), t);
            if (used >= t.dailyLimit) return w("daily-used-up", "limit", String.valueOf(t.dailyLimit));
            return w("daily-left", "left", String.valueOf(t.dailyLimit - used), "limit", String.valueOf(t.dailyLimit));
        }
        return w("ready");
    }

    public String clears(Player p, DungeonTemplate t) {
        int c = plugin.db().stats(p.getUniqueId(), t.id).clears();
        return c > 0 ? w("cleared", "count", String.valueOf(c)) : w("not-cleared");
    }

    public String best(Player p, DungeonTemplate t) {
        long b = plugin.db().stats(p.getUniqueId(), t.id).bestTime();
        return b > 0 ? Text.clock(b) : w("none");
    }

    public String difficultyList(DungeonTemplate t) {
        StringBuilder sb = new StringBuilder();
        for (Difficulty d : t.difficulties.values()) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(w("difficulty-line", "name", d.display(), "time", time(d)));
        }
        return sb.toString();
    }

    public String word(String key, String... kv) {
        return w(key, kv);
    }
}
