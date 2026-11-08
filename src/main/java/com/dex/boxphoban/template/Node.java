package com.dex.boxphoban.template;

import com.dex.boxphoban.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Logic point placed on a block: one trigger, conditions, actions. Position is relative to the slot origin. */
public final class Node {

    public final String id;
    public BlockPos pos;
    public Element trigger;
    public final List<Element> conditions = new ArrayList<>();
    public final List<Element> actions = new ArrayList<>();
    /** Run again every time the trigger fires (otherwise once). */
    public boolean repeat;
    /** "Once" is tracked per player (e.g. a loot chest everyone may open once). */
    public boolean perPlayer;
    /** Ticks between trigger and actions. */
    public int delay;

    public Node(String id, BlockPos pos) {
        this.id = id;
        this.pos = pos;
        this.trigger = Element.withDefaults(TriggerType.INTERACT.name(), TriggerType.INTERACT.params);
    }

    public TriggerType triggerType() {
        return TriggerType.parse(trigger.type);
    }
}
