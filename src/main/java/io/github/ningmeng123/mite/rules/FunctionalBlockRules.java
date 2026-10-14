package io.github.ningmeng123.mite.rules;

/** Independently extracted amv/va/vc/vm/aaw arithmetic. @author NingMeng123 */
public final class FunctionalBlockRules {
    private FunctionalBlockRules() {}
    public static int anvilDurability(float materialDurability) {
        return (int)(1600F * 31F * materialDurability);
    }
    public static int damageStage(int damage, int durability) {
        float fraction = (float)damage / (float)durability;
        return fraction >= 1F ? 3 : fraction >= .8F ? 2 : fraction >= .5F ? 1 : 0;
    }
    public static int minimumDamage(int stage, int durability) {
        // Use the source float comparison, including rounding at large durability values.
        int estimate = (int)(durability * (stage == 2 ? .8F : stage == 1 ? .5F : 0F));
        while (damageStage(estimate, durability) < stage) estimate++;
        while (estimate > 0 && damageStage(estimate - 1, durability) >= stage) estimate--;
        return estimate;
    }
    public record Repair(int damage, int consumed, int restored) {}
    public static Repair repair(int damage, int maximum, int cost, boolean chainmail, int available) {
        if (damage <= 0 || maximum < 1 || cost < 1 || available < 1) return new Repair(damage, 0, 0);
        int unit = chainmail ? maximum * 2 / cost : maximum / cost;
        int initial = Math.min(damage, unit);
        int remaining = damage, used = 0;
        int amount = initial;
        while (amount > 0 && amount == initial && used < available) {
            remaining -= amount;
            used++;
            amount = Math.min(remaining, unit);
        }
        return new Repair(remaining, used, damage - remaining);
    }
    public static int enchantabilityAdjustedPower(int power, int enchantability) {
        if (enchantability < 1) return 0;
        if (power <= enchantability) return power;
        float adjusted = enchantability;
        for (int value = enchantability + 1; value <= power; value++) {
            if (value <= enchantability * 2) adjusted += .5F;
            else if (value <= enchantability * 3) adjusted += .25F;
            else break;
        }
        return Math.round(adjusted);
    }
    public static int enchantmentPower(int accessibleBookshelves, boolean emerald) {
        return (1 + Math.clamp(accessibleBookshelves, 0, 24)) * (emerald ? 2 : 4);
    }
    public static int enchantmentLevels(int bookshelves, boolean emerald, int enchantability, int slot, float random) {
        int levels = enchantabilityAdjustedPower(enchantmentPower(bookshelves, emerald), enchantability);
        if (levels == 0) return 0;
        float fraction = (1F + slot) / 3F;
        if (slot < 2) fraction += (random - .5F) * .2F;
        return Math.max(Math.round(levels * fraction), 1);
    }
}
