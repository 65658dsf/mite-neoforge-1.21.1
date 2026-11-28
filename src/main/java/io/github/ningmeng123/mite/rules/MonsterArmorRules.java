package io.github.ningmeng123.mite.rules;

import java.util.Comparator;
import java.util.List;

/** Editable spawn armor windows and independent per-slot chances. @author NingMeng123 */
public final class MonsterArmorRules {
    public static final long OPEN_END = Long.MAX_VALUE;
    public static final List<String> MATERIALS = List.of("leather", "chainmail", "mixed_metals", "iron",
            "ancient_metal", "mithril", "adamantium", "vibranium");
    private static final List<Integer> STAGES = List.of(0, 1, 3, 5, 6, 7, 8, 9);
    public static final Settings DEFAULTS = new Settings(List.of(
            new Row(16, 31, 40, 80), new Row(32, 47, 41, 82), new Row(48, 63, 43, 86), new Row(64, 95, 45, 90),
            new Row(96, 111, 46, 92), new Row(112, 127, 47, 94), new Row(128, 143, 48, 96), new Row(144, OPEN_END, 49, 98)));

    private MonsterArmorRules() {}

    public record Row(long startDay, long endDay, double ordinaryPercent, double bloodPercent) {
        public Row {
            if (startDay < 1 || endDay < startDay || !validPercent(ordinaryPercent) || !validPercent(bloodPercent))
                throw new IllegalArgumentException("Invalid monster armor dates or probabilities");
        }

        public double chance(boolean bloodDate) {
            return (bloodDate ? bloodPercent : ordinaryPercent) / 100D;
        }

        private static boolean validPercent(double percent) {
            return percent >= 0D && percent <= 100D;
        }
    }

    /** Rows follow material order. Dates may leave gaps for the source equipment rules. */
    public record Settings(List<Row> rows) {
        public Settings {
            rows = List.copyOf(rows);
            if (rows.size() != MATERIALS.size()) throw new IllegalArgumentException("Expected eight monster armor rows");
            var byDate = rows.stream().sorted(Comparator.comparingLong(Row::startDay)).toList();
            for (int index = 1; index < byDate.size(); index++) {
                if (byDate.get(index).startDay() <= byDate.get(index - 1).endDay())
                    throw new IllegalArgumentException("Monster armor date ranges overlap");
            }
        }

        public int rowForDay(long day) {
            for (int index = 0; index < rows.size(); index++) {
                var row = rows.get(index);
                if (day >= row.startDay() && day <= row.endDay()) return index;
            }
            return -1;
        }

        public int stageForDay(long day) {
            int row = rowForDay(day);
            return row < 0 ? -1 : stageForRow(row);
        }
    }

    public static int stageForRow(int row) {
        return STAGES.get(row);
    }
}
