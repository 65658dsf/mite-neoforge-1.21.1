package io.github.ningmeng123.mite.rules;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/** R196 aoo's complete player-harvest random tree, preserving each random draw. @author NingMeng123 */
public final class FlintGravelRules {
    public enum Drop { GRAVEL, FLINT_CHIP, FLINT, COPPER_NUGGET, SILVER_NUGGET, GOLD_NUGGET,
        OBSIDIAN_SHARD, EMERALD_SHARD, DIAMOND_SHARD, MITHRIL_NUGGET, ADAMANTIUM_NUGGET, QUARTZ_SHARD }

    private static final Drop[] MINERALS = {Drop.COPPER_NUGGET, Drop.SILVER_NUGGET, Drop.GOLD_NUGGET,
            Drop.OBSIDIAN_SHARD, Drop.EMERALD_SHARD, Drop.DIAMOND_SHARD, Drop.MITHRIL_NUGGET};
    private static final double BASE_SUCCESS = 3D / 12D;

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
        return selected(netherGravel, nextInt);
    }

    /** Sum both sources' extra success probability over the same unenchanted gravel baseline. */
    public static double successChance(int normal, int affix) {
        double normalChance = 3D / (12 - 2 * Math.clamp(normal, 0, 3));
        if (affix <= 0) return normalChance;
        double affixChance = Math.min(1D, 3D / Math.max(1D, 12D - 2D * affix));
        return Math.clamp(normalChance + affixChance - BASE_SUCCESS, 0D, 1D);
    }

    public static Drop roll(int normal, int affix, boolean netherGravel, IntUnaryOperator nextInt) {
        if (affix <= 0) return roll(normal, netherGravel, nextInt);
        if (nextInt.applyAsInt(Integer.MAX_VALUE) >= successChance(normal, affix) * Integer.MAX_VALUE)
            return Drop.GRAVEL;
        return selected(netherGravel, nextInt);
    }

    private static Drop selected(boolean netherGravel, IntUnaryOperator nextInt) {
        if (nextInt.applyAsInt(3) == 0) {
            Drop result = Drop.ADAMANTIUM_NUGGET;
            for (Drop mineral : MINERALS) {
                if (nextInt.applyAsInt(3) > 0) { result = mineral; break; }
            }
            return mineralResult(result, netherGravel);
        }
        return nextInt.applyAsInt(16) == 0 ? Drop.FLINT : Drop.FLINT_CHIP;
    }

    private static Drop mineralResult(Drop mineral, boolean netherGravel) {
        if (!netherGravel) return mineral;
        return switch (mineral) {
            case OBSIDIAN_SHARD, EMERALD_SHARD, DIAMOND_SHARD -> Drop.QUARTZ_SHARD;
            default -> Drop.GOLD_NUGGET;
        };
    }

    /** Percentages of a single harvest without Fortune, including the result that returns the block. */
    public static Map<Drop, Double> defaultRates(boolean netherGravel) {
        var rates = new EnumMap<Drop, Double>(Drop.class);
        double success = 100D * BASE_SUCCESS;
        rates.put(Drop.GRAVEL, 100D - success);
        rates.put(Drop.FLINT, success / 24D);
        rates.put(Drop.FLINT_CHIP, success * 15D / 24D);
        double reach = success / 3D;
        for (Drop mineral : MINERALS) {
            rates.merge(mineralResult(mineral, netherGravel), reach * 2D / 3D, Double::sum);
            reach /= 3D;
        }
        rates.merge(mineralResult(Drop.ADAMANTIUM_NUGGET, netherGravel), reach, Double::sum);
        return rates;
    }

    /** Fortune boosts the total chance of finding a product while preserving the ratio between products. */
    public static Drop rollConfigured(Map<Drop, Double> rates, int normal, int affix, DoubleSupplier random) {
        double total = 0D;
        for (var entry : rates.entrySet()) {
            if (entry.getKey() != Drop.GRAVEL) total += entry.getValue();
        }
        double baseChance = 100D * (total / (total + rates.get(Drop.GRAVEL)));
        double chance = Math.min(100D, baseChance * (successChance(normal, affix) / BASE_SUCCESS));
        double roll = random.getAsDouble() * 100D;
        if (roll >= chance) return Drop.GRAVEL;
        double productRoll = roll * total / chance;
        Drop last = Drop.GRAVEL;
        for (var entry : rates.entrySet()) {
            if (entry.getKey() == Drop.GRAVEL || entry.getValue() == 0D) continue;
            last = entry.getKey();
            productRoll -= entry.getValue();
            if (productRoll < 0D) return last;
        }
        return last;
    }
}
