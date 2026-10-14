package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Expected values are source amv/vc/vm facts, including integer division. @author NingMeng123 */
class FunctionalBlockRulesTest {
    static Stream<Arguments> anvils() {
        return Stream.of(Arguments.of(4F, 198400, 99200, 158720), Arguments.of(8F, 396800, 198400, 317440),
                Arguments.of(16F, 793600, 396800, 634880), Arguments.of(64F, 3174400, 1587200, 2539520),
                Arguments.of(256F, 12697600, 6348800, 10158080));
    }
    @ParameterizedTest @MethodSource("anvils")
    void sourceAnvilWearStages(float material, int total, int half, int worn) {
        assertEquals(total, FunctionalBlockRules.anvilDurability(material));
        assertEquals(half, FunctionalBlockRules.minimumDamage(1, total));
        assertEquals(worn, FunctionalBlockRules.minimumDamage(2, total));
        assertEquals(0, FunctionalBlockRules.damageStage(half - 1, total));
        assertEquals(1, FunctionalBlockRules.damageStage(half, total));
        assertEquals(1, FunctionalBlockRules.damageStage(worn - 1, total));
        assertEquals(2, FunctionalBlockRules.damageStage(worn, total));
        assertEquals(2, FunctionalBlockRules.damageStage(total - 1, total));
        assertEquals(3, FunctionalBlockRules.damageStage(total, total));
    }
    @Test void sourceRepairStopsBeforeUnequalLastBatch() {
        assertEquals(new FunctionalBlockRules.Repair(250, 4, 3200), FunctionalBlockRules.repair(3450, 4800, 6, false, 5));
        assertEquals(new FunctionalBlockRules.Repair(0, 1, 250), FunctionalBlockRules.repair(250, 4800, 6, false, 5));
        assertEquals(new FunctionalBlockRules.Repair(1050, 3, 2400), FunctionalBlockRules.repair(3450, 4800, 6, false, 3));
    }
    @Test void chainConsumesTwiceProportionAndKeepsSmallIntegerRounding() {
        assertEquals(new FunctionalBlockRules.Repair(3, 1, 8), FunctionalBlockRules.repair(11, 32, 8, true, 3));
        assertEquals(new FunctionalBlockRules.Repair(3, 2, 8), FunctionalBlockRules.repair(11, 32, 8, false, 3));
        assertEquals(new FunctionalBlockRules.Repair(0, 1, 3), FunctionalBlockRules.repair(3, 32, 8, true, 3));
    }
    @Test void zeroUnitCannotConsumeMaterials() {
        assertEquals(new FunctionalBlockRules.Repair(1, 0, 0), FunctionalBlockRules.repair(1, 1, 2, false, 64));
        assertEquals(new FunctionalBlockRules.Repair(0, 0, 0), FunctionalBlockRules.repair(0, 4800, 6, false, 64));
    }
    @Test void emeraldSourceEnergyAndEnchantabilityBands() {
        assertEquals(2, FunctionalBlockRules.enchantmentPower(0, true));
        assertEquals(50, FunctionalBlockRules.enchantmentPower(24, true));
        assertEquals(50, FunctionalBlockRules.enchantmentPower(999, true));
        assertEquals(100, FunctionalBlockRules.enchantmentPower(24, false));
        assertEquals(30, FunctionalBlockRules.enchantabilityAdjustedPower(30, 30));
        assertEquals(40, FunctionalBlockRules.enchantabilityAdjustedPower(50, 30));
        assertEquals(45, FunctionalBlockRules.enchantabilityAdjustedPower(60, 30));
        assertEquals(53, FunctionalBlockRules.enchantabilityAdjustedPower(90, 30));
        assertEquals(53, FunctionalBlockRules.enchantabilityAdjustedPower(1000, 30));
        assertEquals(0, FunctionalBlockRules.enchantabilityAdjustedPower(50, 0));
    }
    @Test void topSlotStableLowerSlotsSourceVariation() {
        assertEquals(40, FunctionalBlockRules.enchantmentLevels(24, true, 30, 2, .5F));
        assertEquals(9, FunctionalBlockRules.enchantmentLevels(24, true, 30, 0, 0F));
        assertEquals(17, FunctionalBlockRules.enchantmentLevels(24, true, 30, 0, 1F));
        assertEquals(0, FunctionalBlockRules.enchantmentLevels(24, true, 0, 2, .5F));
    }
}
