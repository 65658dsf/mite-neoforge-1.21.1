package io.github.ningmeng123.mite.rules;

/** Literal aaw/of/abh arithmetic, independent of the registry and entity implementation. @author NingMeng123 */
public final class EnchantmentCombatRules {
    private EnchantmentCombatRules() {}
    public static boolean meleeProc(int level, double roll) { return level > 0 && level > roll * 10D; }
    public static int stunTicks(int level) { return Math.max(0, level) * 50; }
    public static int stunAmplifier(int level) { return Math.max(0, level) * 5; }
    public static int vampiricTransfer(int level, float damage, boolean biological, double procRoll, double healRoll) {
        if (level <= 0 || damage <= 0F || !biological || procRoll * 10D > level) return 0;
        return Math.max(1, (int)((double)(damage * .5F) * healRoll));
    }
    public static float speedMultiplier(int level) { return 1F + Math.max(0, level) * .05F; }
    public static float freeActionResistance(int level) { return Math.clamp(level, 0, 4) * .2F; }
    /** of:1741-1758; absent potion amplifier is -1. Web also keeps its physical .25/.05/.25 multiplier. */
    public static float movementFactor(int slowAmplifier, int hasteAmplifier, boolean inWeb, int freeAction) {
        return movementFactorWithResistance(slowAmplifier, hasteAmplifier, inWeb, freeActionResistance(freeAction));
    }
    /** Independent sources add resistance before the final physical [0,1] bound. */
    public static float movementFactorWithResistance(int slowAmplifier, int hasteAmplifier, boolean inWeb, float resistance) {
        float slow = slowAmplifier < 0 ? 0F : -(slowAmplifier + 1F) * .2F;
        if (inWeb) slow -= .75F;
        float haste = hasteAmplifier < 0 ? 0F : (hasteAmplifier + 1F) * .2F;
        float amount = slow + haste;
        if (amount < 0F) amount *= 1F - Math.clamp(resistance, 0F, 1F);
        return Math.clamp(1F + amount, 0F, 4F);
    }
    public static double playerSlownessAmount(int slowAmplifier, int hasteAmplifier, int freeAction) {
        return playerSlownessAmountWithResistance(slowAmplifier, hasteAmplifier, freeActionResistance(freeAction));
    }
    public static double playerSlownessAmountWithResistance(int slowAmplifier, int hasteAmplifier, float resistance) {
        double nativeHaste = hasteAmplifier < 0 ? 1D : 1D + .2D * (hasteAmplifier + 1D);
        return movementFactorWithResistance(slowAmplifier, hasteAmplifier, false, resistance) / nativeHaste - 1D;
    }
    public static double webMovementRatio(int slowAmplifier, int hasteAmplifier, int freeAction) {
        return webMovementRatioWithResistance(slowAmplifier, hasteAmplifier, freeActionResistance(freeAction));
    }
    public static double webMovementRatioWithResistance(int slowAmplifier, int hasteAmplifier, float resistance) {
        float dry = movementFactorWithResistance(slowAmplifier, hasteAmplifier, false, resistance);
        return dry > 0F ? movementFactorWithResistance(slowAmplifier, hasteAmplifier, true, resistance) / dry : 0D;
    }
    public static boolean thornsProc(int level, float roll) { return level > 0 && roll < .15F * level; }
    public static int thornsDamage(int level, int rollBoundFour) { return level > 10 ? level - 10 : 1 + rollBoundFour; }
}
