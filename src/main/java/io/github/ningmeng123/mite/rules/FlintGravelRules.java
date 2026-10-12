package io.github.ningmeng123.mite.rules;

import java.util.function.IntUnaryOperator;

/** R196 aoo's complete player-harvest random tree, preserving each random draw. @author NingMeng123 */
public final class FlintGravelRules {
    public enum Drop { GRAVEL, FLINT_CHIP, FLINT, COPPER_NUGGET, SILVER_NUGGET, GOLD_NUGGET,
        OBSIDIAN_SHARD, EMERALD_SHARD, DIAMOND_SHARD, MITHRIL_NUGGET, ADAMANTIUM_NUGGET, QUARTZ_SHARD }

    private FlintGravelRules() {
    }

    public static Drop roll(int fortune, IntUnaryOperator nextInt) {
        return roll(fortune, false, nextInt);
    }

    public static Drop roll(int fortune, boolean netherGravel, IntUnaryOperator nextInt) {
        int denominator = 12 - 2 * Math.clamp(fortune, 0, 3);
        if (nextInt.applyAsInt(denominator) > 2) {
            return Drop.GRAVEL;
        }
        if (nextInt.applyAsInt(3) == 0) {
            Drop[] minerals = {Drop.COPPER_NUGGET, Drop.SILVER_NUGGET, Drop.GOLD_NUGGET,
                    Drop.OBSIDIAN_SHARD, Drop.EMERALD_SHARD, Drop.DIAMOND_SHARD, Drop.MITHRIL_NUGGET};
            Drop result = Drop.ADAMANTIUM_NUGGET;
            for (Drop mineral : minerals) {
                if (nextInt.applyAsInt(3) > 0) { result = mineral; break; }
            }
            if (!netherGravel) return result;
            return switch (result) {
                case OBSIDIAN_SHARD, EMERALD_SHARD, DIAMOND_SHARD -> Drop.QUARTZ_SHARD;
                default -> Drop.GOLD_NUGGET;
            };
        }
        return nextInt.applyAsInt(16) == 0 ? Drop.FLINT : Drop.FLINT_CHIP;
    }
}
