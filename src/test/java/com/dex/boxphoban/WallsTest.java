package com.dex.boxphoban;

import com.dex.boxphoban.engine.Walls;
import com.dex.boxphoban.template.ActionType;
import com.dex.boxphoban.template.DungeonTemplate;
import com.dex.boxphoban.template.Element;
import com.dex.boxphoban.template.Node;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WallsTest {

    /** Doorway 4 wide (x), 3 high, 1 thick. */
    private static final Box DOOR = Box.of(new BlockPos(10, 64, 5), new BlockPos(13, 66, 5));

    @Test
    void portcullisHidesAboveAndRisingWallBelow() {
        assertEquals(new Vector3f(0, 3, 0), Walls.openOffset(Walls.Style.DOWN, DOOR, new BlockPos(11, 65, 5)));
        assertEquals(new Vector3f(0, -3, 0), Walls.openOffset(Walls.Style.UP, DOOR, new BlockPos(11, 65, 5)));
    }

    @Test
    void slidingHalvesMoveApartAlongTheLongerAxis() {
        assertEquals(new Vector3f(-2, 0, 0), Walls.openOffset(Walls.Style.SIDE, DOOR, new BlockPos(10, 64, 5)));
        assertEquals(new Vector3f(-2, 0, 0), Walls.openOffset(Walls.Style.SIDE, DOOR, new BlockPos(11, 64, 5)));
        assertEquals(new Vector3f(2, 0, 0), Walls.openOffset(Walls.Style.SIDE, DOOR, new BlockPos(12, 64, 5)));
        assertEquals(new Vector3f(2, 0, 0), Walls.openOffset(Walls.Style.SIDE, DOOR, new BlockPos(13, 64, 5)));
        Box alongZ = Box.of(new BlockPos(0, 0, 0), new BlockPos(0, 2, 4));
        assertEquals(new Vector3f(0, 0, -3), Walls.openOffset(Walls.Style.SIDE, alongZ, new BlockPos(0, 0, 1)));
        assertEquals(new Vector3f(0, 0, 3), Walls.openOffset(Walls.Style.SIDE, alongZ, new BlockPos(0, 0, 3)));
    }

    @Test
    void styleAndNames() {
        assertEquals(Walls.Style.UP, Walls.style("up"));
        assertEquals(Walls.Style.SIDE, Walls.style("side"));
        assertEquals(Walls.Style.DOWN, Walls.style("x"));
        assertEquals(List.of("vao1", "vao2"), Walls.names(" vao1 , vao2,"));
    }

    @Test
    void wallRegionsComeFromWallAndLockActions() {
        Node n = new Node("n1", new BlockPos(0, 0, 0));
        Element lock = Element.withDefaults(ActionType.ROOM_LOCK.name(), ActionType.ROOM_LOCK.params);
        lock.params.put("room", "phong2");
        lock.params.put("entrances", "vao2");
        lock.params.put("exits", "ra2,ra2b");
        n.actions.add(lock);
        Element close = Element.withDefaults(ActionType.WALL_CLOSE.name(), ActionType.WALL_CLOSE.params);
        close.params.put("region", "cong");
        n.actions.add(close);
        Set<String> walls = Walls.wallRegions(List.of(n));
        assertEquals(Set.of("vao2", "ra2", "ra2b", "cong"), walls);
        assertTrue(!walls.contains("phong2"));
    }

    @Test
    void closedAtStartIsSaved() throws Exception {
        DungeonTemplate t = new DungeonTemplate("x", new File("x"));
        t.wallsClosedAtStart.add("cong");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(t.saveEditorData().saveToString());
        DungeonTemplate back = new DungeonTemplate("x", new File("x"));
        back.loadEditorData(y);
        assertEquals(Set.of("cong"), back.wallsClosedAtStart);
    }
}
