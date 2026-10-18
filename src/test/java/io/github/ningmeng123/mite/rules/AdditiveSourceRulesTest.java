package io.github.ningmeng123.mite.rules;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Nonlinear source bonuses must add after evaluation and keep ordinary RNG contracts. @author NingMeng123 */
class AdditiveSourceRulesTest {
    @Test void gravelCombinesProbabilityGainsInsteadOfFeedingSummedRanksToItsDenominator() {
        assertEquals(.75D, FlintGravelRules.successChance(3, 3), 1E-12D);
        assertEquals(1D, FlintGravelRules.successChance(3, 4), 1E-12D);
        assertEquals(1D, FlintGravelRules.successChance(3, 25));
        assertEquals(.5D, FlintGravelRules.successChance(3, 0));
        List<Integer> ordinary = new ArrayList<>(), absentAffix = new ArrayList<>();
        var first = FlintGravelRules.roll(3, false, bound -> { ordinary.add(bound); return bound - 1; });
        var second = FlintGravelRules.roll(3, 0, false, bound -> { absentAffix.add(bound); return bound - 1; });
        assertEquals(first, second);
        assertEquals(ordinary, absentAffix);
    }

    @Test void fishingAddsEachSourcesProbabilityBenefitOverOneWeatherAndBaitBaseline() {
        long time = 23500L;
        double baseline = 1D / R196RangedRules.biteChanceDenominator(time, false, true, 0, true);
        double normal = 1D / R196RangedRules.biteChanceDenominator(time, false, true, 3, true);
        double affix = 1D / R196RangedRules.biteChanceDenominator(time, false, true, 2, true);
        assertEquals(normal + affix - baseline,
                R196RangedRules.biteChance(time, false, true, 3, 2, true), 1E-12D);
        assertNotEquals(1D / R196RangedRules.biteChanceDenominator(time, false, true, 5, true),
                R196RangedRules.biteChance(time, false, true, 3, 2, true), 1E-8D);
        assertEquals(normal, R196RangedRules.biteChance(time, false, true, 3, 0, true));
        assertEquals(1D, R196RangedRules.biteChance(time, false, true, 1000, 1000, true));
    }

    @Test void addedFreeActionCanReachFullResistanceWithoutChangingOrdinaryLegacyCap() {
        assertEquals(.8F, EnchantmentCombatRules.freeActionResistance(100));
        assertEquals(1F, EnchantmentCombatRules.movementFactorWithResistance(25, -1, false, 1F));
        assertEquals(1D, EnchantmentCombatRules.webMovementRatioWithResistance(25, -1, 1F));
        assertEquals(1.2F, EnchantmentCombatRules.movementFactorWithResistance(0, 1, false, 1F), .000001F);
        for (int normal = 0; normal <= 4; normal++)
            assertEquals(EnchantmentCombatRules.movementFactor(2, -1, true, normal),
                    EnchantmentCombatRules.movementFactorWithResistance(2, -1, true,
                            EnchantmentCombatRules.freeActionResistance(normal)));
    }
}
