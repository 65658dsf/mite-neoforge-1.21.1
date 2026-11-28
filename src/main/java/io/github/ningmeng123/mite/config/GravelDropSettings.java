package io.github.ningmeng123.mite.config;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.rules.FlintGravelRules;
import io.github.ningmeng123.mite.rules.FlintGravelRules.Drop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;
import net.neoforged.fml.ModList;

/** Common configuration for the single drop rolled when a player mines gravel. @author NingMeng123 */
public final class GravelDropSettings {
    private static final String FLINT = "minecraft:flint";
    private static final String CHIP = "mite:flint_chip";
    private static final String ORDINARY_PREFIX = "ordinary/";
    private static final String NETHER_PREFIX = "nether/";
    private static final double TOTAL_TOLERANCE = 0.000001D;
    private static final Rates DEFAULTS = new Rates(defaultGroup(false), defaultGroup(true));
    private static final Rates WHOLE_FLINT_DEFAULTS = combineFlint(DEFAULTS);
    private static volatile Pools local = new Pools(DEFAULTS);

    public record Rates(Map<String, Double> ordinary, Map<String, Double> nether) {
        public Rates {
            ordinary = Collections.unmodifiableMap(new LinkedHashMap<>(ordinary));
            nether = Collections.unmodifiableMap(new LinkedHashMap<>(nether));
        }

        /** Chance for a player to find this product with the given tool, in percent. */
        public double harvestPercent(boolean isNether, String id, int normal, int affix, boolean silkTouch) {
            var weights = isNether ? nether : ordinary;
            String gravel = itemId(Drop.GRAVEL, isNether);
            if (silkTouch) return id.equals(gravel) ? 100D : 0D;
            double products = 0D;
            for (var entry : weights.entrySet()) {
                if (!entry.getKey().equals(gravel)) products += entry.getValue();
            }
            if (products == 0D) return id.equals(gravel) ? 100D : 0D;
            double chance = FlintGravelRules.productChancePercent(products, weights.get(gravel), normal, affix);
            return id.equals(gravel) ? 100D - chance : (weights.getOrDefault(id, 0D) / products) * chance;
        }
    }

    public record Snapshot(boolean wholeFlint, Rates rates) {}

    private record Pools(Rates rates, Rates wholeFlintRates, Map<Drop, Double> ordinary, Map<Drop, Double> nether) {
        private Pools(Rates rates) {
            this(rates, combineFlint(rates), customPool(rates.ordinary(), false), customPool(rates.nether(), true));
        }

        private Rates rates(boolean wholeFlint) { return wholeFlint ? wholeFlintRates : rates; }
    }

    private GravelDropSettings() {}

    public static boolean wholeFlint() {
        return ModList.get().isLoaded("modern_ite");
    }

    public static Rates defaults(boolean wholeFlint) {
        return wholeFlint ? WHOLE_FLINT_DEFAULTS : DEFAULTS;
    }

    public static Rates localRates() {
        return local.rates(wholeFlint());
    }

    public static Snapshot localSnapshot() {
        boolean wholeFlint = wholeFlint();
        return new Snapshot(wholeFlint, local.rates(wholeFlint));
    }

    public static Drop roll(boolean nether, int normal, int affix, IntUnaryOperator nextInt, DoubleSupplier nextDouble) {
        Pools pools = local;
        var custom = nether ? pools.nether() : pools.ordinary();
        return custom.isEmpty() ? FlintGravelRules.roll(normal, affix, nether, nextInt)
                : FlintGravelRules.rollConfigured(custom, normal, affix, nextDouble);
    }

    public static synchronized boolean save(Rates rates) {
        if (!MiteConfig.SPEC.isLoaded() || rates == null) return false;
        boolean wholeFlint = wholeFlint();
        Rates defaults = defaults(wholeFlint);
        if (!validGroup(rates.ordinary(), defaults.ordinary()) || !validGroup(rates.nether(), defaults.nether())) return false;
        Rates previous = local.rates();
        Rates stored = wholeFlint ? new Rates(splitFlint(rates.ordinary(), previous.ordinary(), DEFAULTS.ordinary()),
                splitFlint(rates.nether(), previous.nether(), DEFAULTS.nether())) : rates;
        var previousEntries = MiteConfig.GRAVEL_RATES.get();
        var entries = encode(stored);
        try {
            MiteConfig.GRAVEL_RATES.set(entries);
            MiteConfig.SPEC.save();
        } catch (RuntimeException exception) {
            if (MiteConfig.GRAVEL_RATES.get().equals(entries)) MiteConfig.GRAVEL_RATES.set(previousEntries);
            throw exception;
        }
        return true;
    }

    static void reload() {
        var ordinary = new LinkedHashMap<String, Double>();
        var nether = new LinkedHashMap<String, Double>();
        var entries = MiteConfig.GRAVEL_RATES.get();
        for (String entry : entries) {
            boolean isNether = entry.startsWith(NETHER_PREFIX);
            int separator = entry.indexOf('=');
            int start = isNether ? NETHER_PREFIX.length() : ORDINARY_PREFIX.length();
            var group = isNether ? nether : ordinary;
            group.put(entry.substring(start, separator), Double.parseDouble(entry.substring(separator + 1)));
        }
        if (ordinary.size() + nether.size() != entries.size()
                || !ordinary.isEmpty() && !validGroup(ordinary, DEFAULTS.ordinary())
                || !nether.isEmpty() && !validGroup(nether, DEFAULTS.nether())) {
            Mite.LOGGER.warn("Invalid gravel drop rates, retaining the last valid rates");
            return;
        }
        local = new Pools(new Rates(ordinary.isEmpty() ? DEFAULTS.ordinary() : ordinary,
                nether.isEmpty() ? DEFAULTS.nether() : nether));
    }

    static boolean validEntry(Object input) {
        if (!(input instanceof String text)) return false;
        String prefix;
        Map<String, Double> defaults;
        if (text.startsWith(ORDINARY_PREFIX)) {
            prefix = ORDINARY_PREFIX;
            defaults = DEFAULTS.ordinary();
        } else if (text.startsWith(NETHER_PREFIX)) {
            prefix = NETHER_PREFIX;
            defaults = DEFAULTS.nether();
        } else return false;
        int separator = text.indexOf('=');
        if (separator < 0 || !defaults.containsKey(text.substring(prefix.length(), separator))) return false;
        try {
            return validPercent(Double.parseDouble(text.substring(separator + 1)));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    static List<String> exportEntries() {
        return encode(local.rates());
    }

    private static List<String> encode(Rates rates) {
        var entries = new ArrayList<String>();
        append(entries, ORDINARY_PREFIX, rates.ordinary(), DEFAULTS.ordinary());
        append(entries, NETHER_PREFIX, rates.nether(), DEFAULTS.nether());
        return List.copyOf(entries);
    }

    private static void append(List<String> entries, String prefix, Map<String, Double> rates, Map<String, Double> defaults) {
        if (!rates.equals(defaults)) defaults.keySet().forEach(id -> entries.add(prefix + id + "=" + rates.get(id)));
    }

    private static boolean validGroup(Map<String, Double> rates, Map<String, Double> defaults) {
        return rates.keySet().equals(defaults.keySet()) && rates.values().stream().allMatch(GravelDropSettings::validPercent)
                && Math.abs(rates.values().stream().mapToDouble(Double::doubleValue).sum() - 100D) <= TOTAL_TOLERANCE;
    }

    private static boolean validPercent(Double percent) {
        return percent != null && Double.isFinite(percent) && percent >= 0D && percent <= 100D;
    }

    private static Rates combineFlint(Rates rates) {
        return new Rates(combineFlint(rates.ordinary()), combineFlint(rates.nether()));
    }

    private static Map<String, Double> combineFlint(Map<String, Double> rates) {
        var combined = new LinkedHashMap<>(rates);
        combined.merge(FLINT, combined.remove(CHIP), Double::sum);
        return combined;
    }

    private static Map<String, Double> splitFlint(Map<String, Double> rates, Map<String, Double> previous,
                                                  Map<String, Double> defaults) {
        var split = new LinkedHashMap<>(rates);
        double combined = rates.get(FLINT);
        double previousTotal = previous.get(FLINT) + previous.get(CHIP);
        if (combined == previousTotal) {
            split.put(FLINT, previous.get(FLINT));
            split.put(CHIP, previous.get(CHIP));
        } else if (combined == defaults.get(FLINT) + defaults.get(CHIP)) {
            split.put(FLINT, defaults.get(FLINT));
            split.put(CHIP, defaults.get(CHIP));
        } else {
            double wholeShare = previousTotal == 0D ? 1D / 16D : previous.get(FLINT) / previousTotal;
            double whole = combined * wholeShare;
            split.put(FLINT, whole);
            split.put(CHIP, combined - whole);
        }
        return split;
    }

    private static Map<String, Double> defaultGroup(boolean nether) {
        var rates = new LinkedHashMap<String, Double>();
        FlintGravelRules.defaultRates(nether).forEach((drop, percent) -> rates.put(itemId(drop, nether), percent));
        return rates;
    }

    private static Map<Drop, Double> customPool(Map<String, Double> rates, boolean nether) {
        if (rates.equals(nether ? DEFAULTS.nether() : DEFAULTS.ordinary())) return Map.of();
        var pool = new EnumMap<Drop, Double>(Drop.class);
        for (Drop drop : Drop.values()) {
            Double rate = rates.get(itemId(drop, nether));
            if (rate != null) pool.put(drop, rate);
        }
        return pool;
    }

    private static String itemId(Drop drop, boolean nether) {
        return switch (drop) {
            case GRAVEL -> nether ? "mite:nether_gravel" : "minecraft:gravel";
            case FLINT -> FLINT;
            case GOLD_NUGGET -> "minecraft:gold_nugget";
            default -> "mite:" + drop.name().toLowerCase(Locale.ROOT);
        };
    }
}
