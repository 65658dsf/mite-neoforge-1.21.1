package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Independent clock boundaries, including signed and overflowing world times. @author NingMeng123 */
final class WorldClockTest {
    @Test void dayRollsOverAtMidnightAndNeverOverflows() {
        long[][] examples = {{Long.MIN_VALUE, 1}, {-1, 1}, {0, 1}, {17999, 1}, {18000, 2},
                {23999, 2}, {24000, 2}, {41999, 2}, {42000, 3}, {Long.MAX_VALUE, 384307168202283L}};
        for (long[] example : examples) assertEquals(example[1], WorldClock.displayDay(example[0]));
    }

    @Test void clockUsesTheSixAmWorldEpoch() {
        long[] times = {0, 6000, 12000, 17999, 18000, 23999, 24000, -1, Long.MAX_VALUE};
        String[] clocks = {"06:00", "12:00", "18:00", "23:59", "00:00", "05:59", "06:00", "05:59", "13:48"};
        for (int i = 0; i < times.length; i++) assertEquals(clocks[i], WorldClock.displayTime(times[i]));
    }
}
