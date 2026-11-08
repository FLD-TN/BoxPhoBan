package com.dex.boxphoban.template;

import org.bukkit.Material;

/** One difficulty of a dungeon (dungeon.yml difficulties.&lt;id&gt;). */
public record Difficulty(String id, String display, Material icon, int mobLevel, double healthMultiplier,
                         double damageMultiplier, int timeLimit, String rewards) {
}
