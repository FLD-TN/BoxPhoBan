package com.dex.boxphoban.template;

/** A step of the dungeon shown on the scoreboard. */
public final class Stage {

    public final String id;
    public String display;
    public String objective;

    public Stage(String id, String display, String objective) {
        this.id = id;
        this.display = display;
        this.objective = objective;
    }
}
