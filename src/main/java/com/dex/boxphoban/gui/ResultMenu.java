package com.dex.boxphoban.gui;

import com.dex.boxphoban.BoxPhoBan;
import com.dex.boxphoban.engine.InstanceManager;
import com.dex.boxphoban.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shown after a run. */
public final class ResultMenu extends Menu {

    private final InstanceManager.Result result;

    public ResultMenu(BoxPhoBan plugin, Player viewer, InstanceManager.Result result) {
        super(plugin, viewer);
        this.result = result;
    }

    @Override
    protected String title() {
        return gui().raw("result.title");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void draw() {
        filler();
        boolean win = result.outcome() == InstanceManager.Outcome.WIN;
        String rewards = result.rewards().isEmpty() ? gui().raw("result.no-rewards") : String.join("\n",
                result.rewards().stream().map(l -> Text.replace(gui().raw("result.reward-line"), "reward", l)).toList());
        set(13, item("result.summary", win ? Material.EMERALD : Material.REDSTONE, true,
                "dungeon", result.dungeon(), "difficulty", result.difficulty(),
                "outcome", gui().raw("result.outcome." + result.outcome().name()),
                "time", Text.clock(result.seconds()), "deaths", String.valueOf(result.deaths()),
                "rewards", rewards, "first", result.firstClear() ? gui().raw("result.first-clear") : ""), null);
        set(22, item("close", Material.BARRIER), (p, c) -> close());
    }
}
