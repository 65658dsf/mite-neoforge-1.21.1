package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guard exhaustion uses accepted damage rather than durability wear or incoming damage. @author NingMeng123 */
class ShieldGuardRulesTest {
    @Test void damageLimitTriggersAtTwentyFiveAndResetsBothCounters() {
        var first = ShieldGuardRules.advance(0D, 0, 24F);
        assertEquals(24D, first.blockedDamage());
        assertEquals(1, first.blocks());
        assertFalse(first.broken());
        var last = ShieldGuardRules.advance(first.blockedDamage(), first.blocks(), 1F);
        assertTrue(last.broken());
        assertEquals(0D, last.blockedDamage());
        assertEquals(0, last.blocks());
    }

    @Test void thirdSmallBlockTriggersIndependentlyOfDamageThreshold() {
        var first = ShieldGuardRules.advance(0D, 0, .25F);
        var second = ShieldGuardRules.advance(first.blockedDamage(), first.blocks(), .25F);
        assertEquals(.5D, second.blockedDamage());
        assertEquals(2, second.blocks());
        assertFalse(second.broken());
        var third = ShieldGuardRules.advance(second.blockedDamage(), second.blocks(), .25F);
        assertTrue(third.broken());
        assertEquals(0D, third.blockedDamage());
        assertEquals(0, third.blocks());
    }

    @Test void zeroNegativeAndNonFiniteBlocksDoNotAdvanceOrClearHistory() {
        for (float rejected : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var result = ShieldGuardRules.advance(12D, 2, rejected);
            assertEquals(12D, result.blockedDamage());
            assertEquals(2, result.blocks());
            assertFalse(result.broken());
        }
    }

    @Test void aSingleLargeBlockTriggersDamageThresholdImmediately() {
        var result = ShieldGuardRules.advance(0D, 0, Float.MAX_VALUE);
        assertTrue(result.broken());
        assertEquals(0D, result.blockedDamage());
        assertEquals(0, result.blocks());
    }

    @Test void damageAndBlockCountThresholdsAreAlternatives() {
        assertTrue(ShieldGuardRules.advance(24D, 1, 1F).broken());
        assertTrue(ShieldGuardRules.advance(1D, 2, .5F).broken());
        assertFalse(ShieldGuardRules.advance(10D, 1, .5F).broken());
        assertEquals(100, ShieldGuardRules.COOLDOWN_TICKS);
    }
}
