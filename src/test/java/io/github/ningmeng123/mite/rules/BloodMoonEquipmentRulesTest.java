package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent agreed probability tables, including the blue-date exclusion and geometric tail. @author NingMeng123 */
class BloodMoonEquipmentRulesTest {
    @Test void progressionCountsOnlyActualBloodDatesAndNeverTreatsBlueAsTheFourthBloodMoon() {
        long[] days = {0, 31, 32, 63, 64, 96, 127, 128, 159, 160, 256, 736};
        long[] counts = {0, 0, 1, 1, 2, 3, 3, 3, 3, 4, 6, 18};
        for (int index = 0; index < days.length; index++)
            assertEquals(counts[index], BloodMoonEquipmentRules.bloodMoons(days[index]));
        assertEquals(0L, BloodMoonEquipmentRules.bloodMoons(-100));
    }

    @Test void theSecondBloodMoonStartsLeatherAndEveryTwoFurtherBloodMoonsAdvanceOneTier() {
        assertEquals(-1, BloodMoonEquipmentRules.stage(0)); assertEquals(-1, BloodMoonEquipmentRules.stage(1));
        for (int stage = 0; stage < 9; stage++) {
            assertEquals(stage, BloodMoonEquipmentRules.stage(2L + stage * 2));
            assertEquals(stage, BloodMoonEquipmentRules.stage(3L + stage * 2));
        }
        assertEquals(8, BloodMoonEquipmentRules.stage(Long.MAX_VALUE));
    }

    @Test void allEighteenConditionalDistributionsMatchTheIndependentDecimalSpecification() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
        assertTrue(spec.get("user_requested_modern_extension").getAsBoolean());
        assertFalse(spec.get("r196_source_probabilities").getAsBoolean());
        for (var element : spec.getAsJsonArray("stages")) {
            var row = element.getAsJsonObject(); int stage = row.get("stage").getAsInt();
            assertEquals(row.get("prefix").getAsString(), BloodMoonEquipmentRules.PREFIXES.get(stage));
            for (boolean blood : new boolean[]{false, true}) {
                String prefix = blood ? "blood" : "ordinary";
                assertEquals(row.get(prefix + "_armor_chance").getAsDouble(), BloodMoonEquipmentRules.armorChance(stage, blood));
                var expected = row.getAsJsonArray(prefix + "_conditional_material_chances");
                double sum = 0D;
                for (int tier = 0; tier < 9; tier++) {
                    double probability = expected.get(tier).getAsDouble();
                    assertEquals(probability, BloodMoonEquipmentRules.tierChance(stage, tier, blood), 1E-14);
                    if (probability > 0D) assertEquals(tier, BloodMoonEquipmentRules.selectTier(stage, blood, sum + probability / 2D));
                    sum += probability;
                }
                assertEquals(1D, sum, 1E-14);
                assertEquals(stage, BloodMoonEquipmentRules.selectTier(stage, blood, 0D));
                assertEquals(8, BloodMoonEquipmentRules.selectTier(stage, blood, Math.nextDown(1D)));
            }
        }
    }

    @Test void secondBloodMoonAlreadyAllowsRareAdamantiumAndBloodRaisesBothRelevantProbabilities() {
        assertEquals(.0000152587890625D, BloodMoonEquipmentRules.tierChance(0, 8, false));
        assertEquals(.00065536D, BloodMoonEquipmentRules.tierChance(0, 8, true), 1E-15);
        for (int stage = 0; stage < 9; stage++) {
            assertTrue(BloodMoonEquipmentRules.armorChance(stage, true) > BloodMoonEquipmentRules.armorChance(stage, false));
            if (stage < 8) assertTrue(BloodMoonEquipmentRules.tierChance(stage, 8, true) > BloodMoonEquipmentRules.tierChance(stage, 8, false));
        }
        assertEquals(.84D, BloodMoonEquipmentRules.armorChance(8, false));
        assertEquals(.94D, BloodMoonEquipmentRules.armorChance(8, true));
        assertEquals(1D, BloodMoonEquipmentRules.tierChance(8, 8, false));
    }

    @Test void materialDrawRejectsInvalidInputsInsteadOfSilentlyChangingTheDistribution() {
        for (double value : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectTier(0, false, value));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.armorChance(-1, false));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectTier(9, false, 0D));
    }
}
