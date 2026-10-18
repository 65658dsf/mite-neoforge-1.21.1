package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.rules.ProgressionRules;

/** R196 float arithmetic, independently traceable to bex:450-503. @author NingMeng123 */
public final class TimedCraftingRules {
    private TimedCraftingRules() {}

    public static int basePeriod(float difficulty) {
        if (!Float.isFinite(difficulty) || difficulty < 0) throw new IllegalArgumentException("Invalid crafting difficulty");
        if (difficulty < 25F) return 25;
        if (difficulty > 100F) return (int)Math.round(Math.pow((double)(difficulty - 100F), (double)0.8F)) + 100;
        return Math.round(difficulty);
    }

    public static int period(float difficulty, int level, float benchModifier, boolean clumsy) {
        int period = basePeriod(difficulty);
        if (clumsy) period *= 2;
        return (int)Math.max((float)period / ((1F + (float)ProgressionRules.attributeLevel(level) * .02F) + benchModifier), 25F);
    }

    public static float benchModifier(float capacity, boolean hasMaterialRequirement) {
        if (capacity <= 0F) return 0F;
        if (!hasMaterialRequirement || capacity <= 2F) return .2F;
        if (capacity <= 4F) return .3F;
        if (capacity <= 8F) return .4F;
        if (capacity <= 16F) return .5F;
        if (capacity <= 64F) return .6F;
        return .7F;
    }

    public static int damageSetback(float damage) {
        return Math.min(Math.max(Math.round(damage * 10F), 0), Short.MAX_VALUE) * 5;
    }
}
