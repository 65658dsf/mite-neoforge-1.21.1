package io.github.ningmeng123.mite.rules;

/** Calendar growth augments the source equipment roll without removing its local tension floor. @author NingMeng123 */
public final class MobEnchantmentRules {
    private MobEnchantmentRules() {}

    public static float dateChance(long highestDay) {
        if (highestDay <= 1L) return 0F;
        if (highestDay >= 301L) return .75F;
        return (highestDay - 1L) / 400F;
    }

    /** Source enchant=false remains disabled on the first day; later dates can enchant any generated equipment. */
    public static float chance(long highestDay, float tension, boolean sourceEnchantment) {
        return Math.max(sourceEnchantment ? .1F * tension : 0F, dateChance(highestDay));
    }

    public static int datePower(long highestDay) {
        if (highestDay <= 1L) return 5;
        if (highestDay >= 401L) return 30;
        return 5 + (int)((highestDay - 1L) / 16L);
    }

    public static int power(long highestDay, float tension, int sourceRoll) {
        return Math.max((int)(5F + tension * sourceRoll), datePower(highestDay));
    }
}
