package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent boundary checks for the requested date-based equipment enchantment growth. @author NingMeng123 */
class MobEnchantmentRulesTest {
    @Test void dateChanceGrowsFromDayTwoAndCapsAtSeventyFivePercent() {
        for (long day : new long[]{Long.MIN_VALUE, -1L, 0L, 1L}) assertEquals(0F, MobEnchantmentRules.dateChance(day));
        assertEquals(.0025F, MobEnchantmentRules.dateChance(2L));
        assertEquals(.25F, MobEnchantmentRules.dateChance(101L));
        assertEquals(.5F, MobEnchantmentRules.dateChance(201L));
        for (long day : new long[]{301L, 401L, 1280L, Long.MAX_VALUE}) assertEquals(.75F, MobEnchantmentRules.dateChance(day));
    }

    @Test void sourceTensionIsAFloorAndDoesNotStackWithTheDateRoll() {
        assertEquals(.2F, MobEnchantmentRules.chance(1L, 2F, true));
        assertEquals(.2F, MobEnchantmentRules.chance(41L, 2F, true));
        assertEquals(.5F, MobEnchantmentRules.chance(201L, 2F, true));
        assertEquals(.75F, MobEnchantmentRules.chance(Long.MAX_VALUE, 2F, true));
        assertEquals(0F, MobEnchantmentRules.chance(1L, 2F, false));
        assertEquals(.1F, MobEnchantmentRules.chance(41L, 2F, false));
        assertEquals(.75F, MobEnchantmentRules.chance(301L, 2F, false));
    }

    @Test void datePowerStepsEverySixteenDaysAndStopsAtThirty() {
        for (long day : new long[]{Long.MIN_VALUE, 0L, 1L, 16L}) assertEquals(5, MobEnchantmentRules.datePower(day));
        assertEquals(6, MobEnchantmentRules.datePower(17L));
        assertEquals(29, MobEnchantmentRules.datePower(400L));
        for (long day : new long[]{401L, 1280L, Long.MAX_VALUE}) assertEquals(30, MobEnchantmentRules.datePower(day));
    }

    @Test void strongerSourcePowerSurvivesAndLateDatesRaiseItsMinimum() {
        assertEquals(5, MobEnchantmentRules.power(1L, 2F, 0));
        assertEquals(39, MobEnchantmentRules.power(1L, 2F, 17));
        assertEquals(39, MobEnchantmentRules.power(401L, 2F, 17));
        assertEquals(30, MobEnchantmentRules.power(401L, 2F, 0));
        assertEquals(5, MobEnchantmentRules.power(16L, 0F, 17));
        assertEquals(6, MobEnchantmentRules.power(17L, 0F, 0));
    }
}
