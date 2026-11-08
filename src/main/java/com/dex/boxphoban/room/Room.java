package com.dex.boxphoban.room;

import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** A waiting room (= party). It holds no map: the map is pasted when the leader starts. */
public final class Room {

    public final int id;
    public final DungeonTemplate template;
    public Difficulty difficulty;
    public UUID leader;
    /** Join order (the first one becomes leader when the leader leaves). */
    public final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    public boolean open = true;
    public long lastActivity = System.currentTimeMillis();
    /** invited player -> expiry (ms). */
    public final Map<UUID, Long> invites = new HashMap<>();
    public boolean starting;

    public Room(int id, DungeonTemplate template, Difficulty difficulty, UUID leader) {
        this.id = id;
        this.template = template;
        this.difficulty = difficulty;
        this.leader = leader;
        members.add(leader);
    }

    public boolean full() {
        return members.size() >= template.maxPlayers;
    }

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }
}
