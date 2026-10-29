package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independent balance targets and relative combat tradeoffs. @author NingMeng123 */
class SimplySwordsBalanceRulesTest {
    @Test void chakramsLoseTwentyPercentOfThePreviousTotalRatherThanOnlyTheirModifier() {
        assertEquals(4.608D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 0D), 1E-5);
        assertEquals(3.608D, SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 0D), 1E-5);
        assertEquals(3.84D, 1D + SimplySwordsBalanceRules.damageBonus("copper", "chakram", 8D, 0D), 1E-5);
        assertEquals(4.096D, 1D + SimplySwordsBalanceRules.damageBonus("silver", "chakram", 8D, 0D), 1E-5);
        assertEquals(3.584D, 1D + SimplySwordsBalanceRules.damageBonus("gold", "chakram", 7D, 0D), 1E-5);
        assertEquals(5.12D, 1D + SimplySwordsBalanceRules.damageBonus("mithril", "chakram", 10D, 0D), 1E-5);
        assertEquals(5.632D, 1D + SimplySwordsBalanceRules.damageBonus("adamantium", "chakram", 11D, 0D), 1E-5);
        assertEquals(7.168D, 1D + SimplySwordsBalanceRules.damageBonus("vibranium", "chakram", 14D, 0D), 1E-5);
        assertEquals(6.144D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 12D, 0D, true), 1E-5);
        assertEquals(8.704D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 8D), 1E-5);
        assertEquals(1.5D, SimplySwordsBalanceRules.attackRate("chakram", 1D), 1E-9);
        assertEquals(7.2D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 9D, 0D), 1E-5);
    }
    @Test void ninePointMaterialHasExplicitLowerDamageTargets() {
        assertEquals(7.2D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 9D, 0D), 1E-5);
        assertEquals(5.04D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "spear", 9D, 0D), 1E-5);
        assertEquals(5.4D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "glaive", 9D, 0D), 1E-5);
        assertEquals(6.12D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "halberd", 9D, 0D), 1E-5);
        assertEquals(7.92D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "greathammer", 9D, 0D), 1E-5);
    }
    @Test void copperSilverAndNativeIronShareOneMonotonicMaterialLadder() {
        for (var profile : SimplySwordsBalanceRules.profiles()) {
            double copper = 1D + SimplySwordsBalanceRules.damageBonus("copper", profile.family(), 8D, 0D);
            double silver = 1D + SimplySwordsBalanceRules.damageBonus("silver", profile.family(), 8D, 0D);
            double iron = 1D + SimplySwordsBalanceRules.damageBonus("iron", profile.family(), 6D, 0D);
            assertEquals(silver * .9375D, copper, 1E-5);
            assertTrue(copper < silver && silver < iron);
        }
        assertEquals(6D, 1D + SimplySwordsBalanceRules.damageBonus("copper", "longsword", 8D, 0D), 1E-5);
        assertEquals(6.4D, 1D + SimplySwordsBalanceRules.damageBonus("silver", "longsword", 8D, 0D), 1E-5);
        assertEquals(7.2D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 6D, 0D), 1E-5);
        assertEquals(5.6D, 1D + SimplySwordsBalanceRules.damageBonus("gold", "longsword", 4D, 0D), 1E-5);
        assertEquals(7.2D, 1D + SimplySwordsBalanceRules.damageBonus("diamond", "longsword", 7D, 0D), 1E-5);
        assertEquals(8D, 1D + SimplySwordsBalanceRules.damageBonus("runic", "longsword", 8D, 0D), 1E-5);
    }
    @Test void uniqueWeaponsKeepTheirIndependentBasisEvenWhenTheirTierIsIron() {
        assertEquals(9.6D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 12D, 0D, true), 1E-5);
        assertEquals(7.2D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 12D, 0D, false), 1E-5);
    }
    @Test void heavySingleHitsTradeAttackFrequencyAndMovementForDamage() {
        assertTrue(damage("greathammer") > damage("longsword"));
        assertTrue(damage("greathammer") * .7D < damage("longsword") * 1.5D);
        assertTrue(damage("spear") < damage("longsword"));
        assertTrue(damage("glaive") < damage("longsword"));
        assertTrue(damage("halberd") < damage("longsword"));
        assertEquals(.7D, SimplySwordsBalanceRules.attackRate("greathammer", .8D), 1E-9);
        assertEquals(.25D, SimplySwordsBalanceRules.movementPenalty("greathammer", true));
        assertEquals(.05D, SimplySwordsBalanceRules.movementPenalty("spear", false));
    }
    @Test void twoHandedHandlingReallyLowersTheFamilyRateExactlyOnce() {
        for (var profile : SimplySwordsBalanceRules.profiles()) {
            double oneHanded = 4D + SimplySwordsBalanceRules.baseAttackSpeedBonus(profile.family(), profile.upstreamAttacksPerSecond(), false);
            double twoHanded = (4D + SimplySwordsBalanceRules.baseAttackSpeedBonus(profile.family(), profile.upstreamAttacksPerSecond(), true)) * .8D;
            assertEquals(profile.attacksPerSecond(), oneHanded, 1E-9);
            assertEquals(oneHanded * .8D, twoHanded, 1E-9);
            assertTrue(twoHanded < oneHanded);
        }
    }
    @Test void spearSpeedHasAnExplicitSlowerBudgetWithoutChangingItsDamageOrConfigurationBasis() {
        var spear = SimplySwordsBalanceRules.profile("spear");
        assertEquals(1.3D, spear.upstreamAttacksPerSecond());
        assertEquals(1D, SimplySwordsBalanceRules.attackRate("spear", 1.3D), 1E-9);
        assertEquals(.85D, SimplySwordsBalanceRules.attackRate("spear", .001D), 1E-9);
        assertEquals(1.1D, SimplySwordsBalanceRules.attackRate("spear", 100D), 1E-9);
        assertEquals(.8D, (4D + SimplySwordsBalanceRules.baseAttackSpeedBonus("spear", 1.3D, true)) * .8D, 1E-9);
        assertEquals(5.04D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "spear", 9D, 0D), 1E-5);
    }
    @Test void externalConfigurationsHaveFiniteExplicitBounds() {
        for (var profile : SimplySwordsBalanceRules.profiles()) for (double configured : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -100D, 0D, .00001D, 1000000D}) {
            double speed = SimplySwordsBalanceRules.attackRate(profile.family(), configured);
            assertTrue(speed >= profile.minimumAttacksPerSecond() && speed <= profile.maximumAttacksPerSecond());
            double damage = SimplySwordsBalanceRules.damageBonus("iron", profile.family(), configured, configured);
            assertTrue(Double.isFinite(damage) && damage > -1D && damage < 70D);
        }
        assertEquals(-4D, SimplySwordsBalanceRules.damageAdjustment(-10000D));
        assertEquals(8D, SimplySwordsBalanceRules.damageAdjustment(10000D));
        assertEquals(0D, SimplySwordsBalanceRules.damageAdjustment(Double.NaN));
    }
    private static double damage(String family) { return 1D + SimplySwordsBalanceRules.damageBonus("iron", family, 9D, 0D); }
}
