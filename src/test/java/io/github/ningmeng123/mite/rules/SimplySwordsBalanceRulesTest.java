package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Independent balance targets and relative combat tradeoffs. @author NingMeng123 */
class SimplySwordsBalanceRulesTest {
    @Test void chakramsUseOneLongRangeDamageBudgetAcrossMaterialsAndConfiguration() {
        assertEquals(4.95D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 0D), 1E-5);
        assertEquals(3.95D, SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 0D), 1E-5);
        assertEquals(4.4D, 1D + SimplySwordsBalanceRules.damageBonus("copper", "chakram", 8D, 0D), 1E-5);
        assertEquals(4.4D, 1D + SimplySwordsBalanceRules.damageBonus("silver", "chakram", 8D, 0D), 1E-5);
        assertEquals(3.85D, 1D + SimplySwordsBalanceRules.damageBonus("gold", "chakram", 7D, 0D), 1E-5);
        assertEquals(5.5D, 1D + SimplySwordsBalanceRules.damageBonus("mithril", "chakram", 10D, 0D), 1E-5);
        assertEquals(6.05D, 1D + SimplySwordsBalanceRules.damageBonus("adamantium", "chakram", 11D, 0D), 1E-5);
        assertEquals(7.7D, 1D + SimplySwordsBalanceRules.damageBonus("vibranium", "chakram", 14D, 0D), 1E-5);
        assertEquals(6.6D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 12D, 0D, true), 1E-5);
        assertEquals(9.35D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "chakram", 9D, 8D), 1E-5);
        assertEquals(.9D, SimplySwordsBalanceRules.attackRate("chakram", 1D), 1E-9);
        assertEquals(9D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 9D, 0D), 1E-5);
    }
    @Test void ninePointMaterialHasExplicitRoleBasedDamageTargets() {
        assertEquals(9D, damage("longsword"), 1E-5);
        assertEquals(5.85D, damage("spear"), 1E-5);
        assertEquals(5.85D, damage("glaive"), 1E-5);
        assertEquals(4.95D, damage("halberd"), 1E-5);
        assertEquals(11.25D, damage("greathammer"), 1E-5);
    }
    @Test void copperSilverAndNativeIronShareOneMonotonicMaterialLadder() {
        for (var profile : SimplySwordsBalanceRules.profiles()) {
            double copper = 1D + SimplySwordsBalanceRules.damageBonus("copper", profile.family(), 8D, 0D);
            double silver = 1D + SimplySwordsBalanceRules.damageBonus("silver", profile.family(), 8D, 0D);
            double iron = 1D + SimplySwordsBalanceRules.damageBonus("iron", profile.family(), 6D, 0D);
            assertEquals(silver, copper, 1E-5);
            assertTrue(silver < iron);
        }
        assertEquals(8D, 1D + SimplySwordsBalanceRules.damageBonus("copper", "longsword", 8D, 0D), 1E-5);
        assertEquals(8D, 1D + SimplySwordsBalanceRules.damageBonus("silver", "longsword", 8D, 0D), 1E-5);
        assertEquals(9D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 6D, 0D), 1E-5);
        assertEquals(7D, 1D + SimplySwordsBalanceRules.damageBonus("gold", "longsword", 4D, 0D), 1E-5);
        assertEquals(9D, 1D + SimplySwordsBalanceRules.damageBonus("diamond", "longsword", 7D, 0D), 1E-5);
        assertEquals(10D, 1D + SimplySwordsBalanceRules.damageBonus("runic", "longsword", 8D, 0D), 1E-5);
    }
    @Test void uniqueWeaponsKeepTheirIndependentBasisEvenWhenTheirTierIsIron() {
        assertEquals(12D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 12D, 0D, true), 1E-5);
        assertEquals(9D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "longsword", 12D, 0D, false), 1E-5);
    }
    @Test void heavySingleHitsTradeAttackFrequencyAndMovementForDamage() {
        assertTrue(damage("greathammer") > damage("longsword"));
        assertTrue(damage("greathammer") * .56D < damage("longsword") * 1.3D);
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
    @Test void spearSpeedAndDamageKeepTheReviewedLongRangeBudgetAndConfigurationBasis() {
        var spear = SimplySwordsBalanceRules.profile("spear");
        assertEquals(1.3D, spear.upstreamAttacksPerSecond());
        assertEquals(.9D, SimplySwordsBalanceRules.attackRate("spear", 1.3D), 1E-9);
        assertEquals(.75D, SimplySwordsBalanceRules.attackRate("spear", .001D), 1E-9);
        assertEquals(1.05D, SimplySwordsBalanceRules.attackRate("spear", 100D), 1E-9);
        assertEquals(.72D, (4D + SimplySwordsBalanceRules.baseAttackSpeedBonus("spear", 1.3D, true)) * .8D, 1E-9);
        assertEquals(5.85D, 1D + SimplySwordsBalanceRules.damageBonus("iron", "spear", 9D, 0D), 1E-5);
    }
    @Test void externalConfigurationsHaveFiniteExplicitBounds() {
        for (var profile : SimplySwordsBalanceRules.profiles()) for (double configured : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -100D, 0D, .00001D, 1000000D}) {
            double speed = SimplySwordsBalanceRules.attackRate(profile.family(), configured);
            assertTrue(speed >= profile.minimumAttacksPerSecond() && speed <= profile.maximumAttacksPerSecond());
            double damage = SimplySwordsBalanceRules.damageBonus("iron", profile.family(), configured, configured);
            assertTrue(Double.isFinite(damage) && damage > -1D && damage <= 89D);
        }
        assertEquals(-4D, SimplySwordsBalanceRules.damageAdjustment(-10000D));
        assertEquals(8D, SimplySwordsBalanceRules.damageAdjustment(10000D));
        assertEquals(0D, SimplySwordsBalanceRules.damageAdjustment(Double.NaN));
        assertEquals(89D, SimplySwordsBalanceRules.damageBonus("unique", "greathammer", 1_000D, 1_000D, true), 1E-9);
    }

    @Test void everyFamilyHasItsLiteralDamageSpeedReachAndComboBudget() {
        String[] families = {"longsword", "twinblade", "rapier", "katana", "sai", "spear", "glaive",
                "warglaive", "cutlass", "claymore", "greataxe", "greathammer", "chakram", "scythe", "halberd"};
        double[] factors = {1D, .7D, .75D, .85D, .6D, .65D, .65D, .8D, .8D, 1.15D, 1.2D, 1.25D, .55D, .7D, .55D};
        double[] rates = {1.3D, 1.05D, 1.85D, 1.7D, 2D, .9D, .8D, 1.65D, 1.75D, .76D, .64D, .56D, .9D, .8D, .65D};
        double[] reaches = {2.5D, 3.5D, 2.1D, 2.15D, 2D, 3.75D, 3.5D, 2.1D, 2.1D, 3.25D, 3.25D, 3.25D, 2.8D, 3.5D, 4D};
        double[] standaloneReaches = {2.5D, 2.75D, 2D, 2.1D, 2D, 3D, 2.75D, 2D, 2D, 3.25D, 3.25D, 3.25D, 2.8D, 2.75D, 3.25D};
        double[] maximumCombo = {1.4D, 1.2D, 1.1D, 1.2D, 1D, 1D, 1.4D, 1.2D, 1.3D, 1.25D, 1.4D, 1.5D, 1D, 1.4D, 1.6D};
        for (int index = 0; index < families.length; index++) {
            var profile = SimplySwordsBalanceRules.profile(families[index]);
            double handling = profile.defaultTwoHanded() ? .8D : 1D;
            double actualRate = SimplySwordsBalanceRules.attackRate(families[index], profile.upstreamAttacksPerSecond()) * handling;
            assertEquals(factors[index], profile.damageFactor(), 1E-9, families[index]);
            assertEquals(rates[index], actualRate, 1E-9, families[index]);
            assertEquals(reaches[index], SimplySwordsBalanceRules.meleeReach(families[index]), 1E-9, families[index]);
            assertEquals(reaches[index], SimplySwordsBalanceRules.meleeReach(families[index], true), 1E-9, families[index]);
            assertEquals(standaloneReaches[index], profile.standaloneMeleeReach(), 1E-9, families[index]);
            assertEquals(standaloneReaches[index], SimplySwordsBalanceRules.meleeReach(families[index], false), 1E-9, families[index]);
            if (index == 0) continue;
            int advantages = (profile.damageFactor() > 1D ? 1 : 0)
                    + (actualRate > 1.3D ? 1 : 0) + (profile.meleeReach() > 2.5D ? 1 : 0);
            assertEquals(profile.damageFactor() > 1D ? 2 : 1, advantages, families[index]);
            if (profile.damageFactor() > 1D) {
                assertTrue(profile.damageFactor() * maximumCombo[index] > 1.4D, families[index]);
                assertEquals(3.25D, profile.meleeReach(), 1E-9, families[index]);
                assertTrue(actualRate < 1.5D, families[index]);
            } else {
                assertTrue(profile.damageFactor() * maximumCombo[index] <= 1.4D, families[index]);
            }
            if (actualRate > 1.5D) assertTrue(profile.minimumAttacksPerSecond() * handling > 1.5D, families[index]);
            else assertTrue(profile.maximumAttacksPerSecond() < 1.5D, families[index]);
        }
        assertTrue(SimplySwordsBalanceRules.profile("longsword").maximumAttacksPerSecond() <= 1.65D);
    }

    @Test void bothCombatModesUseTheReviewedFamilyTradeoffsAtEveryMaterialTier() {
        String[] materials = {"rusted_iron", "gold", "copper", "silver", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"};
        double[] swordDamage = {7D, 7D, 8D, 8D, 9D, 9D, 10D, 11D, 14D};
        for (boolean betterCombat : new boolean[]{false, true}) {
            double swordReach = 2.5D;
            for (int material = 0; material < materials.length; material++) {
                for (var profile : SimplySwordsBalanceRules.profiles()) {
                    double damage = 1D + SimplySwordsBalanceRules.damageBonus(materials[material], profile.family(), swordDamage[material], 0D);
                    double handling = profile.defaultTwoHanded() ? .8D : 1D;
                    double rate = SimplySwordsBalanceRules.attackRate(profile.family(), profile.upstreamAttacksPerSecond()) * handling;
                    double reach = SimplySwordsBalanceRules.meleeReach(profile.family(), betterCombat);
                    int advantages = (damage > swordDamage[material] + 1E-9 ? 1 : 0)
                            + (rate > 1.3D + 1E-9 ? 1 : 0) + (reach > swordReach + 1E-9 ? 1 : 0);
                    int expectedAdvantages = profile.family().equals("longsword") ? 0 : 1;
                    if (profile.damageFactor() > 1D) expectedAdvantages = 2;
                    assertEquals(expectedAdvantages, advantages,
                            materials[material] + " " + profile.family() + " Better Combat=" + betterCombat);
                }
            }
        }
    }
    private static double damage(String family) { return 1D + SimplySwordsBalanceRules.damageBonus("iron", family, 9D, 0D); }
}
