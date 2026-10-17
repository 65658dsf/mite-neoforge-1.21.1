package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Optional weapon resource gates and actual high-tier rarity. @author NingMeng123 */
class SimplySwordsMobRulesTest {
    @Test void newMetalsUnlockAtExistingBloodMoonMilestones() {
        long[] days = {1, 159, 160, 191, 192, 223, 224, 287, 288, 319, 320};
        int[] counts = {1, 1, 2, 2, 4, 4, 5, 5, 6, 6, 7};
        for (int index = 0; index < days.length; index++)
            assertEquals(counts[index], SimplySwordsMobRules.available(days[index]).size(), "day " + days[index]);
        assertEquals(List.of("rusted_iron"), SimplySwordsMobRules.available(1).stream().map(SimplySwordsMobRules.Material::name).toList());
        assertEquals("rusted_iron", SimplySwordsMobRules.selectMaterial(1, Math.nextDown(1D)));
    }

    @Test void lateWorldWeightsKeepHighMetalsRareAndEveryBoundaryReachable() {
        var available = SimplySwordsMobRules.available(320);
        assertEquals(381, available.stream().mapToInt(SimplySwordsMobRules.Material::weight).sum());
        assertTrue(SimplySwordsMobRules.EQUIP_CHANCE * 1D / 381D < .0004D);
        double cumulative = 0D;
        for (var material : available) {
            double midpoint = (cumulative + material.weight() / 2D) / 381D;
            assertEquals(material.name(), SimplySwordsMobRules.selectMaterial(320, midpoint));
            cumulative += material.weight();
        }
        assertEquals("adamantium", SimplySwordsMobRules.selectMaterial(Long.MAX_VALUE, Math.nextDown(1D)));
        for (double invalid : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> SimplySwordsMobRules.selectMaterial(320, invalid));
    }
}
