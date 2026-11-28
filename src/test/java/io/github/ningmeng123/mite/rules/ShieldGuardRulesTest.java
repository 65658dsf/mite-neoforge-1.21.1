package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guard exhaustion uses accepted damage rather than durability wear or incoming damage. @author NingMeng123 */
class ShieldGuardRulesTest {
    private static final ShieldGuardRules.Profile BASE = ShieldGuardRules.profile("copper");

    @Test void materialsGainFiveBlocksAndMultiplyDamageByOnePointEightPerTier() {
        String[][] bands = {{"copper", "silver", "rusted_iron", "gold", "unknown"},
                {"iron", "ancient_metal"}, {"mithril"}, {"adamantium"}, {"vibranium"}};
        int[] blockLimits = {5, 10, 15, 20, 25};
        double[] damageLimits = {50D, 90D, 162D, 291.6D, 524.88D};
        for (int tier = 0; tier < bands.length; tier++) for (String material : bands[tier]) {
            var profile = ShieldGuardRules.profile(material);
            assertEquals(blockLimits[tier], profile.blockLimit(), material);
            assertEquals(damageLimits[tier], profile.damageLimit(), material);
        }
        assertEquals(50D, ShieldGuardRules.BASE_DAMAGE_LIMIT);
        assertEquals(5, ShieldGuardRules.BASE_BLOCK_LIMIT);
        assertEquals(524.88D, ShieldGuardRules.MAX_DAMAGE_LIMIT);
        assertEquals(25, ShieldGuardRules.MAX_BLOCK_LIMIT);
    }

    @Test void cooldownDecreasesAlongTheShieldUpgradePath() {
        for (String material : new String[]{"rusted_iron", "copper", "silver", "gold", "unknown"}) {
            assertEquals(110, ShieldGuardRules.profile(material).cooldownTicks(), material);
        }
        assertEquals(110, ShieldGuardRules.profile(null).cooldownTicks());
        assertEquals(100, ShieldGuardRules.profile("iron").cooldownTicks());
        assertEquals(90, ShieldGuardRules.profile("ancient_metal").cooldownTicks());
        assertEquals(80, ShieldGuardRules.profile("mithril").cooldownTicks());
        assertEquals(60, ShieldGuardRules.profile("adamantium").cooldownTicks());
        assertEquals(30, ShieldGuardRules.profile("vibranium").cooldownTicks());
    }

    @Test void damageLimitTriggersAtFiftyAndResetsBothCounters() {
        var first = ShieldGuardRules.advance(BASE, 0D, 0, 49F);
        assertEquals(49D, first.blockedDamage());
        assertEquals(1, first.blocks());
        assertFalse(first.broken());
        var last = ShieldGuardRules.advance(BASE, first.blockedDamage(), first.blocks(), 1F);
        assertTrue(last.broken());
        assertEquals(0D, last.blockedDamage());
        assertEquals(0, last.blocks());
    }

    @Test void fifthSmallBlockTriggersIndependentlyOfDamageThreshold() {
        var fourth = ShieldGuardRules.advance(BASE, .75D, 3, .25F);
        assertEquals(1D, fourth.blockedDamage());
        assertEquals(4, fourth.blocks());
        assertFalse(fourth.broken());
        var fifth = ShieldGuardRules.advance(BASE, fourth.blockedDamage(), fourth.blocks(), .25F);
        assertTrue(fifth.broken());
        assertEquals(0D, fifth.blockedDamage());
        assertEquals(0, fifth.blocks());
    }

    @Test void zeroNegativeAndNonFiniteBlocksDoNotAdvanceOrClearHistory() {
        for (float rejected : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var result = ShieldGuardRules.advance(BASE, 12D, 2, rejected);
            assertEquals(12D, result.blockedDamage());
            assertEquals(2, result.blocks());
            assertFalse(result.broken());
        }
    }

    @Test void aSingleLargeBlockTriggersDamageThresholdImmediately() {
        var result = ShieldGuardRules.advance(BASE, 0D, 0, Float.MAX_VALUE);
        assertTrue(result.broken());
        assertEquals(0D, result.blockedDamage());
        assertEquals(0, result.blocks());
    }

    @Test void damageAndBlockCountThresholdsAreAlternatives() {
        assertTrue(ShieldGuardRules.advance(BASE, 49D, 1, 1F).broken());
        assertTrue(ShieldGuardRules.advance(BASE, 1D, 4, .5F).broken());
        assertFalse(ShieldGuardRules.advance(BASE, 10D, 1, .5F).broken());
    }

    @Test void everyBandBreaksOnItsOwnFinalSmallBlockAndNotBefore() {
        for (String material : new String[]{"copper", "iron", "mithril", "adamantium", "vibranium"}) {
            var profile = ShieldGuardRules.profile(material);
            double damage = 0D;
            int blocks = 0;
            for (int count = 1; count <= profile.blockLimit(); count++) {
                var next = ShieldGuardRules.advance(profile, damage, blocks, .25F);
                assertEquals(count == profile.blockLimit(), next.broken(), material + " block " + count);
                assertEquals(next.broken() ? 0D : count * .25D, next.blockedDamage());
                assertEquals(next.broken() ? 0 : count, next.blocks());
                damage = next.blockedDamage();
                blocks = next.blocks();
            }
        }
    }

    @Test void fractionalDamageLimitsAreNotRoundedToWholeHealthPoints() {
        for (String material : new String[]{"copper", "iron", "mithril", "adamantium", "vibranium"}) {
            var profile = ShieldGuardRules.profile(material);
            float below = Math.nextDown((float)profile.damageLimit());
            var first = ShieldGuardRules.advance(profile, 0D, 0, below);
            assertFalse(first.broken(), material + " stays raised just below its exact damage limit");
            assertEquals((double)below, first.blockedDamage());
            float remainder = Math.nextUp((float)(profile.damageLimit() - below));
            assertTrue(ShieldGuardRules.advance(profile, first.blockedDamage(), first.blocks(), remainder).broken(),
                    material + " breaks immediately after crossing the exact damage limit");
            assertTrue(ShieldGuardRules.advance(profile, profile.damageLimit() - 1D, 0, 1F).broken(),
                    material + " also breaks at exactly the double-precision limit");
        }
        var adamantium = ShieldGuardRules.profile("adamantium");
        assertFalse(ShieldGuardRules.advance(adamantium, 290D, 0, 1.5F).broken(), "291.5 remains below 291.6");
        assertTrue(ShieldGuardRules.advance(adamantium, 291.5D, 1, .25F).broken(), "291.75 exceeds 291.6");
    }

    @Test void higherTierHistorySurvivesTheOldLimitsUntilItsOwnThreshold() {
        var vibranium = ShieldGuardRules.profile("vibranium");
        var next = ShieldGuardRules.advance(vibranium, 96D, 12, 1F);
        assertFalse(next.broken());
        assertEquals(97D, next.blockedDamage());
        assertEquals(13, next.blocks());
        for (float rejected : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var unchanged = ShieldGuardRules.advance(vibranium, 96D, 12, rejected);
            assertEquals(96D, unchanged.blockedDamage());
            assertEquals(12, unchanged.blocks());
            assertFalse(unchanged.broken());
        }
    }
}
