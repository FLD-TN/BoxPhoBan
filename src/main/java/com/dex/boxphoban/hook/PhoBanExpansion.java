package com.dex.boxphoban.hook;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.room.Room;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Stage;
import com.dex.boxphoban.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** %boxphoban_...% for TAB scoreboards and holograms. */
public final class PhoBanExpansion extends PlaceholderExpansion {

    private final BoxPhoBan plugin;

    public PhoBanExpansion(BoxPhoBan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "boxphoban";
    }

    @Override
    public String getAuthor() {
        return "dex";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String p = params.toLowerCase();
        if (p.startsWith("cooldown_")) {
            DungeonTemplate t = plugin.templates().get(params.substring(9));
            if (t == null) return "";
            long left = plugin.entry().cooldownLeft(player.getUniqueId(), t);
            return left > 0 ? plugin.messages().duration(left) : plugin.messages().text("placeholder.ready");
        }
        if (p.startsWith("clears_")) {
            DungeonTemplate t = plugin.templates().get(params.substring(7));
            return t == null ? "0" : String.valueOf(plugin.db().stats(player.getUniqueId(), t.id).clears());
        }
        if (p.startsWith("best_")) {
            DungeonTemplate t = plugin.templates().get(params.substring(5));
            if (t == null) return "";
            long b = plugin.db().stats(player.getUniqueId(), t.id).bestTime();
            return b > 0 ? Text.clock(b) : plugin.messages().text("placeholder.none");
        }
        if (p.equals("in_room")) return String.valueOf(plugin.rooms().room(player.getUniqueId()) != null);
        if (p.equals("room_dungeon")) {
            Room r = plugin.rooms().room(player.getUniqueId());
            return r == null ? "" : Text.color(r.template.display);
        }
        Instance in = plugin.instances().of(player.getUniqueId());
        if (p.equals("in")) return String.valueOf(in != null && in.phase != Instance.Phase.PREPARING);
        if (in == null) return "";
        return switch (p) {
            case "name" -> Text.color(in.template.display);
            case "difficulty" -> Text.color(in.difficulty.display());
            case "stage" -> {
                Stage s = in.stage();
                yield s == null ? plugin.messages().text("placeholder.no-stage") : Text.color(s.display);
            }
            case "stage_index" -> String.valueOf(Math.max(0, in.stageIndex + 1));
            case "stage_total" -> String.valueOf(in.template.stages.size());
            case "objective" -> {
                Stage s = in.stage();
                yield s == null ? "" : Text.color(Text.replace(s.objective, "mobs_left", String.valueOf(in.mobsLeft()),
                        "time_left", timeLeft(in), "lives", String.valueOf(in.livesOf(player.getUniqueId()))));
            }
            case "time_left" -> timeLeft(in);
            case "time_elapsed" -> Text.clock(in.elapsedSeconds());
            case "lives" -> in.template.livesMode == DungeonTemplate.LivesMode.NONE
                    ? plugin.messages().text("placeholder.no-lives") : String.valueOf(in.livesOf(player.getUniqueId()));
            case "players_alive" -> String.valueOf(in.alivePlayers().size());
            case "players" -> String.valueOf(in.members.size());
            case "mobs_left" -> String.valueOf(in.mobsLeft());
            case "state" -> plugin.messages().text("placeholder.state." + in.stateOf(player.getUniqueId()).name().toLowerCase());
            default -> null;
        };
    }

    private String timeLeft(Instance in) {
        long left = in.timeLeft();
        return left < 0 ? plugin.messages().text("placeholder.no-limit") : Text.clock(left);
    }
}
