package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independent release constants, including the actual JVM shift behavior at unsafe amplifier levels. @author NingMeng123 */
class SourceStatusEffectRulesTest {
    @Test void poisonUsesSourceIntervalsAndDurationBoundaries() {
        int[] intervals = {100, 50, 25, 12, 6, 3, 1, 1};
        for (int amplifier = 0; amplifier < intervals.length; amplifier++)
            assertEquals(intervals[amplifier], SourceStatusEffectRules.poisonInterval(amplifier));
        assertFalse(SourceStatusEffectRules.poisonPulse(101, 0));
        assertTrue(SourceStatusEffectRules.poisonPulse(100, 0));
        assertFalse(SourceStatusEffectRules.poisonPulse(99, 0));
        assertEquals(100, SourceStatusEffectRules.poisonInterval(32));
        assertEquals(50, SourceStatusEffectRules.poisonInterval(33));
        assertEquals(1, SourceStatusEffectRules.poisonInterval(255));
    }

    @Test void attackPercentagesUseAmplifierPlusOneAndTheSameBaseForBothEffects() {
        assertEquals(.4D, SourceStatusEffectRules.attackModifier(0, false));
        assertEquals(-.8D, SourceStatusEffectRules.attackModifier(1, true));
        // ox operation0 has already combined the body's5 and the weapon's6 before operation1.
        assertEquals(15.4D, 11D * (1D + SourceStatusEffectRules.attackModifier(1, false)
                + SourceStatusEffectRules.attackModifier(0, true)), .000001D);
    }

    @Test void npcSlownessKeepsItsSeparateAttributeMultiplierAndParalysisResistance() {
        assertEquals(-.2D, SourceStatusEffectRules.nonPlayerSlowness(0, 0), .000001D);
        assertEquals(-.04D, SourceStatusEffectRules.nonPlayerSlowness(0, 4), .000001D);
        assertEquals(-1.2D, SourceStatusEffectRules.nonPlayerSlowness(5, 0), .000001D);
    }
}
