package io.github.ningmeng123.mite.rules;

import java.util.HashMap;
import java.util.Map;

/** Live natural-spawn population and scheduling settings. @author NingMeng123 */
public final class MonsterSpawningSettings {
    public enum Option {
        BASE_CAP("base_cap", 105, 0, 100000, true),
        BLOOD_CAP("blood_cap", 140, 0, 100000, true),
        DAILY_CAP_INCREASE("daily_cap_increase", 0, 0, 10000, false),
        MAXIMUM_CAP("maximum_cap", 0, 0, 100000, true),
        HOSTILE_INTERVAL_TICKS("hostile_interval_ticks", 1, 1, 72000, true),
        DEEP_CHANCE_PERCENT("deep_chance_percent", 10, 0, 100, false),
        SURFACE_CHANCE_PERCENT("surface_chance_percent", 17, 0, 100, false),
        BLOOD_CHANCE_MULTIPLIER("blood_chance_multiplier", 2, 0, 100, false),
        DIMENSION_CHANCE_MULTIPLIER("dimension_chance_multiplier", .25, 0, 100, false);

        private final String key;
        private final double defaultValue, minimum, maximum;
        private final boolean wholeNumber;
        Option(String key, double defaultValue, double minimum, double maximum, boolean wholeNumber) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.minimum = minimum;
            this.maximum = maximum;
            this.wholeNumber = wholeNumber;
        }
        public String key() { return key; }
        public double defaultValue() { return defaultValue; }
        public double minimum() { return minimum; }
        public double maximum() { return maximum; }
        public boolean wholeNumber() { return wholeNumber; }
        public boolean valid(double value) {
            return Double.isFinite(value) && value >= minimum && value <= maximum
                    && (!wholeNumber || value == Math.rint(value));
        }
    }

    public static final Settings DEFAULTS = Settings.from(Map.of());
    private MonsterSpawningSettings() {}

    public record Settings(Map<String, Double> values) {
        public Settings {
            var checked = new HashMap<String, Double>();
            for (var option : Option.values()) {
                Double value = option.defaultValue();
                if (values != null && values.containsKey(option.key())) value = values.get(option.key());
                if (value == null || !option.valid(value)) throw new IllegalArgumentException("Invalid spawning option: " + option.key());
                checked.put(option.key(), value);
            }
            values = Map.copyOf(checked);
        }
        /** Missing fields inherit defaults so earlier configuration snapshots remain readable. */
        public static Settings from(Map<String, Double> values) { return new Settings(values); }
        public double value(Option option) { return values.get(option.key()); }

        /** One player's 17x17 area; fractions accumulate before the final population is rounded down. */
        public int cap(int eligibleChunks, boolean blood, long day) {
            double cap = value(blood ? Option.BLOOD_CAP : Option.BASE_CAP)
                    + Math.max(0D, (double)day - 1D) * value(Option.DAILY_CAP_INCREASE);
            double maximum = value(Option.MAXIMUM_CAP);
            if (maximum > 0D) cap = Math.min(cap, maximum);
            return (int)Math.min(Integer.MAX_VALUE, Math.floor(cap * Math.max(0, eligibleChunks) / 289D));
        }

        public float probability(boolean deep, float modifier, boolean blood) {
            return (float)Math.clamp(value(deep ? Option.DEEP_CHANCE_PERCENT : Option.SURFACE_CHANCE_PERCENT)
                    / 100D * modifier * (blood ? value(Option.BLOOD_CHANCE_MULTIPLIER) : 1D), 0D, 1D);
        }

        /** Re-read the interval each tick, including while waiting, so shortening it takes effect immediately. */
        public boolean ready(long now, long lastAttempt) {
            long elapsed = now - lastAttempt;
            return lastAttempt == Long.MIN_VALUE || now < lastAttempt || elapsed < 0
                    || elapsed >= (long)value(Option.HOSTILE_INTERVAL_TICKS);
        }
    }
}
