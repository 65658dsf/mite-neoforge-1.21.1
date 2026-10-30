package io.github.ningmeng123.mite.rules;

import java.util.Locale;

/** Shared day and clock shown inside and outside a world. @author NingMeng123 */
public final class WorldClock {
    private WorldClock() {}

    public static long displayDay(long time) {
        long safe = Math.max(time, 0L);
        return safe / 24_000L + (safe % 24_000L + 6_000L) / 24_000L + 1L;
    }

    public static String displayTime(long time) {
        int ticks = (int)((Math.floorMod(time, 24_000L) + 6_000L) % 24_000L);
        return String.format(Locale.ROOT, "%02d:%02d", ticks / 1_000, ticks % 1_000 * 60 / 1_000);
    }
}
