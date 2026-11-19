package io.github.ningmeng123.mite.rules;

import java.util.Random;
import java.util.regex.Pattern;

public record OreHeightRange(int minimum, int maximum) {
    private static final Pattern FORMAT = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");

    public OreHeightRange {
        if (minimum < -2032 || maximum > 2031 || minimum > maximum)
            throw new IllegalArgumentException("Invalid ore height range");
    }

    public static OreHeightRange parse(String text) {
        var matcher = FORMAT.matcher(text.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Expected [minimum,maximum]");
        return new OreHeightRange(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    public static OreHeightRange defaults(R196OreRules.Kind kind, R196OreRules.Dimension dimension) {
        if (dimension == R196OreRules.Dimension.NETHER) {
            if (kind == R196OreRules.Kind.ANCIENT_DEBRIS) return new OreHeightRange(8, 119);
            if (kind == R196OreRules.Kind.NETHER_ADAMANTIUM) return new OreHeightRange(0, 128);
        }
        int maximum = dimension == R196OreRules.Dimension.UNDERWORLD && kind == R196OreRules.Kind.ADAMANTIUM
                ? 135 : R196OreRules.maxHeight(kind, dimension);
        return new OreHeightRange(R196OreRules.modernY(dimension, R196OreRules.minHeight(kind, dimension)),
                R196OreRules.modernY(dimension, maximum));
    }

    public boolean contains(int height) { return height >= minimum && height <= maximum; }

    public Integer sample(Random random, int minimumBuildHeight, int maximumBuildHeight) {
        int lower = Math.max(minimum, minimumBuildHeight), upper = Math.min(maximum, maximumBuildHeight - 1);
        return lower > upper ? null : lower + random.nextInt(upper - lower + 1);
    }

    @Override public String toString() { return "[" + minimum + "," + maximum + "]"; }
}
