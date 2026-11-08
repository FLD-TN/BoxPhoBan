package com.dex.boxphoban;

import com.dex.boxphoban.editor.TypePickMenu;
import com.dex.boxphoban.engine.Instance;
import com.dex.boxphoban.engine.InstanceManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every text key used by the code exists in messages.yml or gui.yml, and the texts follow the style rules. */
class ResourcesTest {

    private static YamlConfiguration yaml(String name) throws Exception {
        try (InputStream in = ResourcesTest.class.getResourceAsStream("/" + name)) {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return y;
        }
    }

    private static String raw(String name) throws Exception {
        try (InputStream in = ResourcesTest.class.getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Dotted literals that are config paths, permissions or files, not text keys. */
    private static boolean notTextKey(String k) {
        String[] prefixes = {"boxphoban.", "commands.", "disconnect.", "end.", "entry.cooldown.", "entry.ticket.", "fallback-location.",
                "lives.", "limits.", "players.", "protection.", "spectator.", "world.", "trigger.", "block.", "entity."};
        for (String p : prefixes) if (k.startsWith(p)) return true;
        return k.endsWith(".yml") || k.endsWith(".schem") || k.equals("entry.cost-money") || k.equals("entry.daily-limit")
                || k.equals("entry.permission") || k.equals("entry.required-clears") || k.equals("room.idle-timeout")
                || k.equals("room.invite-timeout") || k.equals("hold.hologram") || k.equals("hold.actionbar")
                || k.equals("hold.border") || k.equals("hold.bar-length");
    }

    @Test
    void everyKeyExists() throws Exception {
        YamlConfiguration msg = yaml("messages.yml"), gui = yaml("gui.yml");
        Pattern lit = Pattern.compile("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = lit.matcher(Files.readString(f));
                while (m.find()) {
                    String k = m.group(1);
                    if (notTextKey(k)) continue;
                    boolean ok = msg.contains(k) || gui.contains(k) || msg.contains(k + "-title");
                    if (!ok) missing.add(f.getFileName() + ": " + k);
                }
            }
        }
        for (InstanceManager.Outcome o : InstanceManager.Outcome.values()) {
            if (!gui.contains("result.outcome." + o.name())) missing.add("result.outcome." + o.name());
        }
        for (Instance.State s : Instance.State.values()) {
            String k = "placeholder.state." + s.name().toLowerCase(Locale.ROOT);
            if (!msg.contains(k)) missing.add(k);
        }
        for (TypePickMenu.Kind k : TypePickMenu.Kind.values()) {
            String key = "editor.pick." + k.name().toLowerCase(Locale.ROOT) + "-title";
            if (!gui.contains(key)) missing.add(key);
        }
        for (String tool : List.of("node", "region", "point", "view")) {
            if (!gui.contains("editor.tools." + tool + ".name")) missing.add("editor.tools." + tool);
        }
        for (String end : List.of("win", "fail", "timeout", "stopped")) {
            if (!msg.contains("dungeon." + end + "-title") || !msg.contains("dungeon." + end + "-subtitle")) missing.add("dungeon." + end);
        }
        assertTrue(missing.isEmpty(), "Thiếu key: " + missing);
    }

    @Test
    void textStyleRules() throws Exception {
        for (String name : List.of("messages.yml", "gui.yml")) {
            String text = raw(name);
            assertTrue(!text.contains("&l"), name + " không được in đậm");
            for (String icon : List.of("✦", "▸", "➥", "⚠", "★")) assertTrue(!text.contains(icon), name + " có icon trang trí " + icon);
            assertTrue(!text.matches("(?s).*[ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘʀꜱᴛᴜᴠᴡʏᴢ].*"), name + " có chữ small caps");
        }
    }
}
