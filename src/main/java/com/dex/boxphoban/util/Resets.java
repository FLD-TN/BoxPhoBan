package com.dex.boxphoban.util;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/** Pure cooldown / daily-limit time maths (all times in epoch seconds). */
public final class Resets {

    public enum Type { NONE, TIMER, DAILY, WEEKLY }

    private Resets() {
    }

    /** Epoch second when a run made at {@code last} stops blocking, or 0 when nothing blocks. */
    public static long availableAt(Type type, long last, int minutes, int resetHour, DayOfWeek resetDay, ZoneId zone) {
        if (last <= 0) return 0;
        return switch (type) {
            case NONE -> 0;
            case TIMER -> last + Math.max(0, minutes) * 60L;
            case DAILY -> nextDaily(last, resetHour, zone);
            case WEEKLY -> nextWeekly(last, resetHour, resetDay, zone);
        };
    }

    /** First reset instant strictly after {@code last}. */
    public static long nextDaily(long last, int resetHour, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochSecond(last).atZone(zone);
        ZonedDateTime reset = t.toLocalDate().atTime(clampHour(resetHour), 0).atZone(zone);
        if (!reset.isAfter(t)) reset = reset.plusDays(1);
        return reset.toEpochSecond();
    }

    public static long nextWeekly(long last, int resetHour, DayOfWeek day, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochSecond(last).atZone(zone);
        ZonedDateTime reset = t.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(clampHour(resetHour), 0).atZone(zone);
        if (!reset.isAfter(t)) reset = reset.plusWeeks(1);
        return reset.toEpochSecond();
    }

    /** Day key for the daily run limit; a day starts at {@code resetHour}. */
    public static String dayKey(long now, int resetHour, ZoneId zone) {
        LocalDate d = Instant.ofEpochSecond(now).atZone(zone).minusHours(clampHour(resetHour)).toLocalDate();
        return d.toString();
    }

    private static int clampHour(int h) {
        return Math.max(0, Math.min(23, h));
    }
}
