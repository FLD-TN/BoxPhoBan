package com.dex.boxphoban.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colours (&amp; codes + &amp;#RRGGBB), placeholders, numbers (1.250) and durations. */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    private Text() {
    }

    public static String color(String s) {
        if (s == null || s.isEmpty()) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) r.append('§').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    /** Coloured component without the default item italic. */
    public static Component component(String s) {
        return SECTION.deserialize(color(s)).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> components(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) out.add(component(l));
        return out;
    }

    public static String strip(String s) {
        return ChatColor.stripColor(color(s));
    }

    /** Replaces %key% pairs: replace(text, "player", name, "amount", "3"). */
    public static String replace(String s, String... kv) {
        if (s == null) return "";
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("%" + kv[i] + "%", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }

    /** Lore helper: replaces placeholders, then splits values that contain new lines into separate lines. */
    public static List<String> replaceLines(List<String> lines, String... kv) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String r = replace(line, kv);
            if (r.contains("\n")) {
                String[] parts = r.split("\n", -1);
                for (String p : parts) {
                    if (!p.isEmpty() || parts.length == 1) out.add(p);
                }
            } else {
                out.add(r);
            }
        }
        return out;
    }

    /** 1250 -> "1.250". */
    public static String number(long n) {
        return String.format(Locale.GERMANY, "%,d", n);
    }

    public static String number(double n) {
        if (n == Math.rint(n)) return number((long) n);
        return String.format(Locale.GERMANY, "%,.2f", n);
    }

    /** 75 -> "01:15", 3725 -> "1:02:05". */
    public static String clock(long seconds) {
        if (seconds < 0) seconds = 0;
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    /** 3725 -> "1 giờ 2 phút", 45 -> "45 giây". Units come from messages.yml. */
    public static String duration(long seconds, String day, String hour, String minute, String second) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(' ').append(day).append(' ');
        if (h > 0) sb.append(h).append(' ').append(hour).append(' ');
        if (m > 0 && d == 0) sb.append(m).append(' ').append(minute).append(' ');
        if (s > 0 && d == 0 && h == 0) sb.append(s).append(' ').append(second).append(' ');
        if (sb.isEmpty()) sb.append(0).append(' ').append(second);
        return sb.toString().trim();
    }
}
