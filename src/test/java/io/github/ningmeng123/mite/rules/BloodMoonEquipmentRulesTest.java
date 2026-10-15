package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent rational probability tables, including blue-date exclusion and both material tails. @author NingMeng123 */
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

    @Test void allEighteenDistributionsMatchTheIndependentRationalSpecification() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
        assertEquals(2, spec.get("schema_version").getAsInt());
        assertEquals("NingMeng123", spec.get("author").getAsString());
        assertTrue(spec.get("user_requested_modern_extension").getAsBoolean());
        assertFalse(spec.get("r196_source_probabilities").getAsBoolean());
        assertEquals(.6D, spec.get("first_blood_date_legacy_armor_chance").getAsDouble());
        assertFalse(spec.get("first_blood_date_material_and_partial_stop_changed").getAsBoolean());
        assertEquals(9, spec.getAsJsonArray("stages").size());
        for (var element : spec.getAsJsonArray("stages")) {
            var row = element.getAsJsonObject(); int stage = row.get("stage").getAsInt();
            assertEquals(row.get("prefix").getAsString(), BloodMoonEquipmentRules.PREFIXES.get(stage));
            for (boolean blood : new boolean[]{false, true}) {
                String prefix = blood ? "blood" : "ordinary";
                double armor = BloodMoonEquipmentRules.armorChance(stage, blood);
                assertEquals(row.get(prefix + "_armor_chance").getAsDouble(), armor);
                assertEquals(row.get(prefix + "_held_chance").getAsDouble(), BloodMoonEquipmentRules.heldChance(stage, blood));
                double unarmored = row.get(prefix + "_unarmored_chance").getAsDouble();
                assertEquals(unarmored, 1D - armor, 1E-14);
                var expected = row.getAsJsonArray(prefix + "_conditional_material_chances");
                var overall = row.getAsJsonArray(prefix + "_overall_material_chances");
                assertEquals(9, expected.size()); assertEquals(9, overall.size());
                double sum = 0D, overallSum = unarmored;
                for (int tier = 0; tier < 9; tier++) {
                    double probability = expected.get(tier).getAsDouble();
                    assertTrue(probability > 0D, "Every material must remain available, including older armor");
                    assertEquals(probability, BloodMoonEquipmentRules.tierChance(stage, tier, blood), 1E-14);
                    assertEquals(overall.get(tier).getAsDouble(), armor * BloodMoonEquipmentRules.tierChance(stage, tier, blood), 1E-14);
                    assertEquals(tier, BloodMoonEquipmentRules.selectTier(stage, blood, sum + probability / 2D));
                    sum += probability;
                    overallSum += overall.get(tier).getAsDouble();
                }
                assertEquals(1D, sum, 1E-14);
                assertEquals(1D, overallSum, 1E-14);
                assertEquals(0, BloodMoonEquipmentRules.selectTier(stage, blood, 0D));
                assertEquals(8, BloodMoonEquipmentRules.selectTier(stage, blood, Math.nextDown(1D)));
            }
        }
    }

    @Test void independentlyCalculatedAnchorValuesIncludeLowMaterialsAtTheHighestStage() {
        assertEquals(1D / 87381D, BloodMoonEquipmentRules.tierChance(0, 8, false), 1E-15);
        assertEquals(256D / 650871D, BloodMoonEquipmentRules.tierChance(0, 8, true), 1E-15);
        assertEquals(16D / 581D, BloodMoonEquipmentRules.tierChance(4, 0, false), 1E-15);
        assertEquals(2401D / 348797D, BloodMoonEquipmentRules.tierChance(4, 0, true), 1E-15);
        assertEquals(1D / 511D, BloodMoonEquipmentRules.tierChance(8, 0, false), 1E-15);
        assertEquals(256D / 511D, BloodMoonEquipmentRules.tierChance(8, 8, false), 1E-15);
        assertEquals(5764801D / 39381511261D, BloodMoonEquipmentRules.tierChance(8, 0, true), 1E-15);
        assertEquals(25600000000D / 39381511261D, BloodMoonEquipmentRules.tierChance(8, 8, true), 1E-15);
    }

    @Test void bloodRaisesArmorHeldAndHighMaterialProbabilitiesWhileLaterStagesReduceNakedSpawns() {
        for (int stage = 0; stage < 9; stage++) {
            assertTrue(BloodMoonEquipmentRules.armorChance(stage, true) > BloodMoonEquipmentRules.armorChance(stage, false));
            assertTrue(BloodMoonEquipmentRules.heldChance(stage, true) > BloodMoonEquipmentRules.heldChance(stage, false));
            assertTrue(BloodMoonEquipmentRules.tierChance(stage, 8, true) > BloodMoonEquipmentRules.tierChance(stage, 8, false));
            for (boolean blood : new boolean[]{false, true}) {
                for (int tier = 0; tier < 9; tier++)
                    assertTrue(BloodMoonEquipmentRules.tierChance(stage, stage, blood) >= BloodMoonEquipmentRules.tierChance(stage, tier, blood));
                if (stage > 0) {
                    assertTrue(BloodMoonEquipmentRules.armorChance(stage, blood) > BloodMoonEquipmentRules.armorChance(stage - 1, blood));
                    assertTrue(BloodMoonEquipmentRules.heldChance(stage, blood) > BloodMoonEquipmentRules.heldChance(stage - 1, blood));
                    assertTrue(BloodMoonEquipmentRules.tierChance(stage, 0, blood) < BloodMoonEquipmentRules.tierChance(stage - 1, 0, blood));
                }
            }
        }
        assertEquals(.5D, BloodMoonEquipmentRules.armorChance(0, false));
        assertEquals(.8D, BloodMoonEquipmentRules.armorChance(0, true));
        assertEquals(.9D, BloodMoonEquipmentRules.armorChance(8, false));
        assertEquals(.96D, BloodMoonEquipmentRules.armorChance(8, true));
        assertEquals(.4D, BloodMoonEquipmentRules.heldChance(0, false));
        assertEquals(.7D, BloodMoonEquipmentRules.heldChance(0, true));
        assertEquals(.8D, BloodMoonEquipmentRules.heldChance(8, false));
        assertEquals(.94D, BloodMoonEquipmentRules.heldChance(8, true));
    }

    @Test void materialDrawIncludesBothSidesOfEachCumulativeBoundary() {
        for (int stage = 0; stage < 9; stage++) {
            for (boolean blood : new boolean[]{false, true}) {
                double boundary = 0D;
                for (int tier = 0; tier < 8; tier++) {
                    boundary += BloodMoonEquipmentRules.tierChance(stage, tier, blood);
                    assertEquals(tier, BloodMoonEquipmentRules.selectTier(stage, blood, Math.nextDown(boundary)));
                    assertEquals(tier + 1, BloodMoonEquipmentRules.selectTier(stage, blood, boundary));
                }
            }
        }
    }

    @Test void materialDrawRejectsInvalidInputsInsteadOfSilentlyChangingTheDistribution() {
        for (double value : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectTier(0, false, value));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.armorChance(-1, false));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.heldChance(-1, false));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.heldChance(9, true));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.tierChance(9, 0, false));
        assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectTier(9, false, 0D));
        assertEquals(0D, BloodMoonEquipmentRules.tierChance(0, -1, false));
        assertEquals(0D, BloodMoonEquipmentRules.tierChance(8, 9, true));
    }
}
