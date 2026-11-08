package com.dex.boxphoban.config;

import com.dex.boxphoban.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/** messages.yml: chat lines get the prefix, '' disables a message. */
public final class Messages {

    private final YamlConfiguration yaml;
    private final String prefix;

    public Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
        this.prefix = yaml.getString("prefix", "");
    }

    public String raw(String key) {
        return yaml.getString(key, key);
    }

    public List<String> list(String key) {
        return yaml.getStringList(key);
    }

    /** Coloured text without prefix. */
    public String text(String key, String... kv) {
        return Text.color(Text.replace(raw(key), kv));
    }

    public void send(CommandSender to, String key, String... kv) {
        String r = raw(key);
        if (r == null || r.isEmpty() || to == null) return;
        to.sendMessage(Text.component(prefix + Text.replace(r, kv)));
    }

    /** Message followed by clickable buttons: each button = {textKey, command}. */
    public void sendWithButtons(Player to, String key, String[][] buttons, String... kv) {
        String r = raw(key);
        if (r == null || r.isEmpty()) return;
        Component c = Text.component(prefix + Text.replace(r, kv));
        for (String[] b : buttons) {
            c = c.append(Component.text(" ")).append(Text.component(Text.replace(raw(b[0]), kv))
                    .clickEvent(ClickEvent.runCommand(b[1]))
                    .hoverEvent(HoverEvent.showText(Text.component(Text.replace(raw("click-hover"), "command", b[1])))));
        }
        to.sendMessage(c);
    }

    public void title(Player p, String titleKey, String subKey, int fadeIn, int stay, int fadeOut, String... kv) {
        String t = titleKey == null ? "" : Text.replace(raw(titleKey), kv);
        String s = subKey == null ? "" : Text.replace(raw(subKey), kv);
        if (t.isEmpty() && s.isEmpty()) return;
        p.showTitle(Title.title(Text.component(t), Text.component(s), Title.Times.times(
                Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    public void actionbar(Player p, String key, String... kv) {
        String r = raw(key);
        if (r == null || r.isEmpty()) return;
        p.sendActionBar(Text.component(Text.replace(r, kv)));
    }

    public String duration(long seconds) {
        return Text.duration(seconds, raw("units.day"), raw("units.hour"), raw("units.minute"), raw("units.second"));
    }
}
