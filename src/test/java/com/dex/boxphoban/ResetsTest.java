package com.dex.boxphoban;

import com.dex.boxphoban.util.Resets;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetsTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, VN).toEpochSecond();
    }

    @Test
    void noRunNeverBlocks() {
        assertEquals(0, Resets.availableAt(Resets.Type.TIMER, 0, 30, 0, DayOfWeek.MONDAY, VN));
        assertEquals(0, Resets.availableAt(Resets.Type.NONE, at(2026, 11, 8, 10, 0), 30, 0, DayOfWeek.MONDAY, VN));
    }

    @Test
    void timerAddsMinutes() {
        long last = at(2026, 11, 8, 10, 0);
        assertEquals(last + 30 * 60, Resets.availableAt(Resets.Type.TIMER, last, 30, 0, DayOfWeek.MONDAY, VN));
    }

    @Test
    void dailyResetsAtTheNextResetHour() {
        // played at 10:00, reset hour 0 -> next midnight
        assertEquals(at(2026, 11, 9, 0, 0), Resets.nextDaily(at(2026, 11, 8, 10, 0), 0, VN));
        // played at 03:00, reset hour 5 -> same day 05:00
        assertEquals(at(2026, 11, 8, 5, 0), Resets.nextDaily(at(2026, 11, 8, 3, 0), 5, VN));
        // played exactly at the reset -> the next day
        assertEquals(at(2026, 11, 9, 5, 0), Resets.nextDaily(at(2026, 11, 8, 5, 0), 5, VN));
    }

    @Test
    void weeklyResetsOnTheResetDay() {
        // 2026-11-08 is a Sunday
        assertEquals(at(2026, 11, 9, 0, 0), Resets.nextWeekly(at(2026, 11, 8, 12, 0), 0, DayOfWeek.MONDAY, VN));
        // Monday after the reset hour -> next Monday
        assertEquals(at(2026, 11, 16, 0, 0), Resets.nextWeekly(at(2026, 11, 9, 1, 0), 0, DayOfWeek.MONDAY, VN));
    }

    @Test
    void dayKeyStartsAtResetHour() {
        assertEquals("2026-11-07", Resets.dayKey(at(2026, 11, 8, 4, 0), 5, VN));
        assertEquals("2026-11-08", Resets.dayKey(at(2026, 11, 8, 6, 0), 5, VN));
        assertEquals("2026-11-08", Resets.dayKey(at(2026, 11, 8, 0, 0), 0, VN));
    }
}
