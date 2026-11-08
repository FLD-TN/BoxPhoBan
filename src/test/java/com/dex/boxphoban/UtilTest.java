package com.dex.boxphoban;

import com.dex.boxphoban.template.ParamSpec;
import com.dex.boxphoban.util.BlockPos;
import com.dex.boxphoban.util.Box;
import com.dex.boxphoban.util.Point;
import com.dex.boxphoban.util.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilTest {

    @Test
    void boxNormalisesAndContains() {
        Box b = Box.of(new BlockPos(5, 10, -3), new BlockPos(-2, 0, 4));
        assertEquals(new BlockPos(-2, 0, -3), b.min());
        assertEquals(new BlockPos(5, 10, 4), b.max());
        assertTrue(b.contains(5.9, 10.9, 4.9));
        assertFalse(b.contains(6.0, 5, 0));
        assertTrue(b.contains(new BlockPos(0, 0, 0)));
        assertEquals(8 * 11 * 8, b.volume());
        assertEquals(b, Box.parse(b.toString()));
        Box s = b.shift(new BlockPos(1000, 64, 2000));
        assertEquals(new BlockPos(998, 64, 1997), s.min());
        assertEquals(new BlockPos(-3, -1, -4), b.grow(1).min());
    }

    @Test
    void pointRoundTrip() {
        Point p = new Point(1.5, 65, -3.25, 90f, -10f);
        assertEquals(p, Point.parse(p.toString()));
        assertNull(Point.parse("x,y"));
        assertEquals(new BlockPos(1, 2, 3), BlockPos.parse("1, 2, 3"));
        assertNull(BlockPos.parse("1,2"));
    }

    @Test
    void numbersAndTimes() {
        assertEquals("1.250", Text.number(1250));
        assertEquals("1.000.000", Text.number(1_000_000L));
        assertEquals("01:15", Text.clock(75));
        assertEquals("1:02:05", Text.clock(3725));
        assertEquals("1 giờ 2 phút", Text.duration(3725, "ngày", "giờ", "phút", "giây"));
        assertEquals("45 giây", Text.duration(45, "ngày", "giờ", "phút", "giây"));
        assertEquals("2 ngày 3 giờ", Text.duration(2 * 86400 + 3 * 3600 + 59, "ngày", "giờ", "phút", "giây"));
    }

    @Test
    void placeholdersAndLines() {
        assertEquals("a 3 b", Text.replace("a %n% b", "n", "3"));
        List<String> lines = Text.replaceLines(List.of("x", "%list%", "y"), "list", "1\n2");
        assertEquals(List.of("x", "1", "2", "y"), lines);
        assertTrue(Text.color("&#5EEAD4a").startsWith("§x§5§E§E§A§D§4"));
    }

    @Test
    void paramNormalise() {
        ParamSpec i = ParamSpec.p("a", "A", ParamSpec.Kind.INT, "1", "");
        assertEquals("5", i.normalise(" 5 "));
        assertNull(i.normalise("x"));
        ParamSpec b = ParamSpec.p("b", "B", ParamSpec.Kind.BOOL, "true", "");
        assertEquals("true", b.normalise("có"));
        assertEquals("false", b.normalise("tắt"));
        ParamSpec c = ParamSpec.p("c", "C", ParamSpec.Kind.CHOICE, "chat", "chat|title|actionbar");
        assertEquals("title", c.normalise("TITLE"));
        assertNull(c.normalise("x"));
        ParamSpec d = ParamSpec.p("d", "D", ParamSpec.Kind.DOUBLE, "1", "");
        assertEquals("1.5", d.normalise("1,5"));
    }
}
