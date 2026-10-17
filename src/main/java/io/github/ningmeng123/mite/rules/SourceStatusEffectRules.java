package io.github.ningmeng123.mite.rules;

/** Literal ni effect arithmetic; Java's masked shift distance is part of the old formula. @author NingMeng123 */
public final class SourceStatusEffectRules {
    private SourceStatusEffectRules() {}

    public static int poisonInterval(int amplifier) { return Math.max(100 >> amplifier, 1); }
    public static boolean poisonPulse(int duration, int amplifier) { return duration % poisonInterval(amplifier) == 0; }
    public static double attackModifier(int amplifier, boolean weakness) {
        return (weakness ? -.4D : .4D) * (amplifier + 1D);
    }
    public static double nonPlayerSlowness(int amplifier, int freeAction) {
        return -.2F * (amplifier + 1D) * (1F - EnchantmentCombatRules.freeActionResistance(freeAction));
    }
}
