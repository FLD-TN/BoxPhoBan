package com.dex.boxphoban;

import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.ConditionType;
import com.dex.boxphoban.template.Difficulty;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.template.Stage;
import com.dex.boxphoban.template.TriggerType;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Resets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateTest {

    private static YamlConfiguration defaultSettings(String id) throws Exception {
        try (InputStream in = TemplateTest.class.getResourceAsStream("/dungeon-template.yml")) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("%id%", id);
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(text);
            return y;
        }
    }

    @Test
    void defaultSettingsParse() throws Exception {
        DungeonTemplate t = new DungeonTemplate("hang", new File("x"));
        t.loadSettings(defaultSettings("hang"));
        assertFalse(t.enabled);
        assertEquals(1, t.minPlayers);
        assertEquals(4, t.maxPlayers);
        assertFalse(t.ticketEnabled);
        assertEquals(DungeonTemplate.Consume.CREATOR, t.consume);
        assertEquals(Resets.Type.NONE, t.cooldownType);
        assertEquals(DungeonTemplate.ApplyOn.FINISH, t.applyOn);
        assertEquals(DungeonTemplate.LivesMode.SHARED, t.livesMode);
        assertTrue(t.rewardDeadPlayers);
        assertEquals(2, t.difficulties.size());
        Difficulty kho = t.difficulty("kho");
        assertNotNull(kho);
        assertEquals(3, kho.mobLevel());
        assertEquals(1.8, kho.healthMultiplier(), 1e-9);
        assertEquals("hang_kho", kho.rewards());
        assertEquals("thuong", t.firstDifficulty().id());
    }

    @Test
    void editorDataRoundTrip() throws Exception {
        DungeonTemplate t = new DungeonTemplate("hang", new File("x"));
        t.editorSlot = 3;
        t.bounds = Box.of(new BlockPos(0, -10, 0), new BlockPos(40, 30, 60));
        t.start = new Point(2.5, 1, 3.5, 180, 0);
        t.points.put("cp1", new Point(10.5, 1, 20.5, 0, 0));
        t.regions.put("cua1", Box.of(new BlockPos(5, 0, 5), new BlockPos(7, 3, 5)));
        t.stages.add(new Stage("vao", "&fLối vào", "Quái còn: %mobs_left%"));
        t.stages.add(new Stage("boss", "&cBoss", ""));
        Node n = new Node(t.newNodeId(), new BlockPos(4, 0, 4));
        n.trigger = Element.withDefaults(TriggerType.AREA.name(), TriggerType.AREA.params);
        n.trigger.params.put("region", "cua1");
        n.repeat = false;
        n.perPlayer = true;
        n.delay = 20;
        n.conditions.add(Element.withDefaults(ConditionType.CHANCE.name(), ConditionType.CHANCE.params));
        Element spawn = Element.withDefaults(ActionType.SPAWN_MOB.name(), ActionType.SPAWN_MOB.params);
        spawn.params.put("mob", "SkeletonKing");
        n.actions.add(spawn);
        n.actions.add(new Element(ActionType.FINISH.name()));
        t.nodes.put(n.id, n);

        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(t.saveEditorData().saveToString());
        DungeonTemplate back = new DungeonTemplate("hang", new File("x"));
        back.loadEditorData(saved);

        assertEquals(3, back.editorSlot);
        assertEquals(t.bounds, back.bounds);
        assertEquals(t.start, back.start);
        assertEquals(t.points, back.points);
        assertEquals(t.regions, back.regions);
        assertEquals(2, back.stages.size());
        assertEquals("Quái còn: %mobs_left%", back.stages.get(0).objective);
        assertEquals(2, back.nextNodeId);
        Node bn = back.nodes.get("n1");
        assertNotNull(bn);
        assertEquals(new BlockPos(4, 0, 4), bn.pos);
        assertEquals(TriggerType.AREA, bn.triggerType());
        assertEquals("cua1", bn.trigger.s("region", ""));
        assertTrue(bn.perPlayer);
        assertEquals(20, bn.delay);
        assertEquals(1, bn.conditions.size());
        assertEquals(2, bn.actions.size());
        assertEquals("SkeletonKing", bn.actions.get(0).s("mob", ""));
        assertEquals(3, bn.actions.get(0).i("amount", 0));
        assertEquals(ActionType.FINISH.name(), bn.actions.get(1).type);
        assertEquals(bn, back.nodeAt(new BlockPos(4, 0, 4)));
    }
}
