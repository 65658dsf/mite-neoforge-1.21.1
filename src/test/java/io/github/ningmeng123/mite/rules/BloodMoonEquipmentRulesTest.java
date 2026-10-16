package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent rational probability tables, including blue-date exclusion and both material tails. @author NingMeng123 */
class BloodMoonEquipmentRulesTest {
    @Test void progressionCountsOnlyActualBloodDatesAndNeverTreatsBlueAsTheFourthBloodMoon() {
        long[] days = {0, 31, 32, 63, 64, 96, 127, 128, 159, 160, 256, 288, 319, 320, 736};
        long[] counts = {0, 0, 1, 1, 2, 3, 3, 3, 3, 4, 6, 7, 7, 8, 18};
        for (int index = 0; index < days.length; index++)
            assertEquals(counts[index], BloodMoonEquipmentRules.bloodMoons(days[index]));
        assertEquals(0L, BloodMoonEquipmentRules.bloodMoons(-100));
    }

    @Test void bloodMoonStagesShareCopperAndSilverAndEighthReachesAdamantium() {
        assertEquals(-1, BloodMoonEquipmentRules.stage(0)); assertEquals(-1, BloodMoonEquipmentRules.stage(1));
        int[] expected = {0, 1, 2, 3, 5, 6};
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], BloodMoonEquipmentRules.stage(2L + index));
        }
        for (long count : new long[]{8L, 9L, 10L, 11L}) assertEquals(8, BloodMoonEquipmentRules.stage(count));
        assertEquals(8, BloodMoonEquipmentRules.stage(Long.MAX_VALUE));
    }

    @Test void eighthBloodDateUsesIndependentEightGradeHighestTierSlotAndAtLeastOneProbabilities() {
        assertEquals(6, BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(319)));
        int stage = BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(320));
        assertEquals(8, stage);
        assertEquals(.96D, BloodMoonEquipmentRules.armorChance(stage, true));
        double expectedSingleSlot = 409_600_000D / 656_262_441D;
        double actualSingleSlot = BloodMoonEquipmentRules.armorChance(stage, true) * BloodMoonEquipmentRules.tierChance(stage, 8, true);
        assertEquals(expectedSingleSlot, actualSingleSlot, 1E-15);
        assertEquals(.9800426946666656D, 1D - Math.pow(1D - actualSingleSlot, 4), 1E-15);
        assertTrue(actualSingleSlot > .62D && 1D - Math.pow(1D - actualSingleSlot, 4) > .98D);
        assertEquals(1.5D, BloodMoonEquipmentRules.ordinaryProgress(320), "Ordinary-day growth does not inherit the eighth-blood jump");
    }

    @Test void allEighteenDistributionsMatchTheIndependentRationalSpecification() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
        assertEquals(3, spec.get("schema_version").getAsInt());
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
        assertEquals(1D / 21845D, BloodMoonEquipmentRules.tierChance(0, 8, false), 1E-15);
        assertEquals(128D / 130123D, BloodMoonEquipmentRules.tierChance(0, 8, true), 1E-15);
        assertEquals(32D / 565D, BloodMoonEquipmentRules.tierChance(4, 0, false), 1E-15);
        assertEquals(1715D / 86599D, BloodMoonEquipmentRules.tierChance(4, 0, true), 1E-15);
        assertEquals(1D / 255D, BloodMoonEquipmentRules.tierChance(8, 0, false), 1E-15);
        assertEquals(128D / 255D, BloodMoonEquipmentRules.tierChance(8, 8, false), 1E-15);
        assertEquals(823543D / 1968787323D, BloodMoonEquipmentRules.tierChance(8, 0, true), 1E-15);
        assertEquals(1280000000D / 1968787323D, BloodMoonEquipmentRules.tierChance(8, 8, true), 1E-15);
    }

    @Test void copperAndSilverShareOneGradeAndSplitItsProbabilityAtEveryBasisAndDailyProgress() {
        int[] expectedGrades = {0, 1, 2, 3, 3, 4, 5, 6, 7};
        for (int tier = 0; tier < expectedGrades.length; tier++)
            assertEquals(expectedGrades[tier], BloodMoonEquipmentRules.materialGrade(tier));
        for (boolean blood : new boolean[]{false, true}) {
            for (int stage = 0; stage < 9; stage++)
                assertEquals(BloodMoonEquipmentRules.tierChance(stage, 3, blood), BloodMoonEquipmentRules.tierChance(stage, 4, blood),
                        "Copper and silver receive equal halves of one grade, rather than duplicating its weight");
            for (int tier = 0; tier < 9; tier++)
                assertEquals(BloodMoonEquipmentRules.tierChance(3, tier, blood), BloodMoonEquipmentRules.tierChance(4, tier, blood),
                        "The two compatible copper/silver basis indices have the same conditional distribution");
        }
        assertEquals(128D / 565D, BloodMoonEquipmentRules.tierChance(3, 3, false), 1E-15);
        assertEquals(20000D / 86599D, BloodMoonEquipmentRules.tierChance(3, 4, true), 1E-15);
        for (int dayStep = 0; dayStep <= 1024; dayStep++) {
            double progress = dayStep / 128D;
            assertEquals(BloodMoonEquipmentRules.ordinaryTierChance(progress, 3), BloodMoonEquipmentRules.ordinaryTierChance(progress, 4),
                    "Copper and silver stay equal through each reachable daily interpolation step");
        }
        for (double progress : new double[]{Math.nextUp(0D), .123456789D, 3.141592654D, 3.999999999D, 5.87654321D, Math.nextDown(8D)})
            assertEquals(BloodMoonEquipmentRules.ordinaryTierChance(progress, 3), BloodMoonEquipmentRules.ordinaryTierChance(progress, 4));
    }

    @Test void independentBloodArmorSlotsMatchRationalAllEmptyAndAllFourProbabilities() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
        for (var element : spec.getAsJsonArray("stages")) {
            var row = element.getAsJsonObject();
            int stage = row.get("stage").getAsInt();
            assertEquals(row.get("blood_empty_slot_chance").getAsDouble(), row.get("blood_unarmored_chance").getAsDouble());
            assertEquals(row.get("blood_empty_slot_chance").getAsDouble(), 1D - BloodMoonEquipmentRules.armorChance(stage, true), 1E-14);
            assertEquals(row.get("blood_all_empty_armor_chance").getAsDouble(), BloodMoonEquipmentRules.bloodAllEmptyChance(stage), 1E-14);
            assertEquals(row.get("blood_all_four_armor_chance").getAsDouble(), BloodMoonEquipmentRules.bloodAllFourChance(stage), 1E-14);
            assertEquals(rational(row.get("blood_all_empty_armor_fraction").getAsString()), BloodMoonEquipmentRules.bloodAllEmptyChance(stage), 1E-14);
            assertEquals(rational(row.get("blood_all_four_armor_fraction").getAsString()), BloodMoonEquipmentRules.bloodAllFourChance(stage), 1E-14);
            double missingSome = 1D - BloodMoonEquipmentRules.bloodAllEmptyChance(stage) - BloodMoonEquipmentRules.bloodAllFourChance(stage);
            assertTrue(missingSome > 0D, "Blood-date mobs can spawn with only part of the armor slots filled");
        }
        assertEquals(.0016D, BloodMoonEquipmentRules.bloodAllEmptyChance(0), 1E-14);
        assertEquals(.4096D, BloodMoonEquipmentRules.bloodAllFourChance(0), 1E-14);
        assertEquals(.00000256D, BloodMoonEquipmentRules.bloodAllEmptyChance(8), 1E-14);
        assertEquals(.84934656D, BloodMoonEquipmentRules.bloodAllFourChance(8), 1E-14);
        for (int stage : new int[]{-1, 9, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.bloodAllEmptyChance(stage));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.bloodAllFourChance(stage));
        }
    }

    @Test void bloodRaisesSlotArmorHeldAndHighMaterialProbabilitiesWhileLaterStagesReduceEmptySlots() {
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
                    if (stage == 4) assertEquals(BloodMoonEquipmentRules.tierChance(stage - 1, 0, blood), BloodMoonEquipmentRules.tierChance(stage, 0, blood));
                    else assertTrue(BloodMoonEquipmentRules.tierChance(stage, 0, blood) < BloodMoonEquipmentRules.tierChance(stage - 1, 0, blood));
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

    @Test void ordinaryCalendarActivationAndLongBoundariesDoNotDependOnBloodCounts() {
        assertFalse(BloodMoonEquipmentRules.ordinaryActive(Long.MIN_VALUE));
        assertFalse(BloodMoonEquipmentRules.ordinaryActive(63));
        assertTrue(BloodMoonEquipmentRules.ordinaryActive(64));
        assertTrue(BloodMoonEquipmentRules.ordinaryActive(Long.MAX_VALUE));
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryProgress(Long.MIN_VALUE));
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryProgress(64));
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryProgress(128));
        assertEquals(1D / 128D, BloodMoonEquipmentRules.ordinaryProgress(129));
        assertEquals(127D / 128D, BloodMoonEquipmentRules.ordinaryProgress(255));
        assertEquals(1D, BloodMoonEquipmentRules.ordinaryProgress(256));
        assertEquals(129D / 128D, BloodMoonEquipmentRules.ordinaryProgress(257));
        assertEquals(1023D / 128D, BloodMoonEquipmentRules.ordinaryProgress(1151));
        assertEquals(8D, BloodMoonEquipmentRules.ordinaryProgress(1152));
        assertEquals(8D, BloodMoonEquipmentRules.ordinaryProgress(Long.MAX_VALUE));
        assertEquals(8, BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(416)));
        assertEquals(2.25D, BloodMoonEquipmentRules.ordinaryProgress(416));
    }

    @Test void ordinaryCalendarMatchesIndependentOfflineRationalAnchors() throws Exception {
        var spec = JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
        var calendar = spec.getAsJsonObject("ordinary_calendar_progression");
        assertEquals(64L, calendar.get("introduced_from_day").getAsLong());
        assertEquals(128L, calendar.get("growth_start_day").getAsLong());
        assertEquals(128L, calendar.get("days_per_stage").getAsLong());
        assertEquals(1152L, calendar.get("maximum_day").getAsLong());
        assertEquals(12, calendar.getAsJsonArray("anchors").size());
        for (var element : calendar.getAsJsonArray("anchors")) {
            var row = element.getAsJsonObject();
            double progress = BloodMoonEquipmentRules.ordinaryProgress(row.get("day").getAsLong());
            assertEquals(row.get("progress").getAsDouble(), progress);
            assertEquals(rational(row.get("progress_fraction").getAsString()), progress, 1E-14);
            double armor = BloodMoonEquipmentRules.ordinaryArmorChance(progress);
            double held = BloodMoonEquipmentRules.ordinaryHeldChance(progress);
            assertEquals(row.get("armor_chance").getAsDouble(), armor, 1E-14);
            assertEquals(row.get("held_chance").getAsDouble(), held, 1E-14);
            assertEquals(row.get("unarmored_chance").getAsDouble(), 1D - armor, 1E-14);
            assertEquals(row.get("empty_held_chance").getAsDouble(), 1D - held, 1E-14);
            double cumulative = 0D;
            for (int tier = 0; tier < 9; tier++) {
                double expected = row.getAsJsonArray("conditional_material_chances").get(tier).getAsDouble();
                double actual = BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                assertEquals(rational(row.getAsJsonArray("conditional_material_fractions").get(tier).getAsString()), expected, 1E-14);
                assertEquals(expected, actual, 1E-14);
                assertEquals(row.getAsJsonArray("overall_armor_material_chances").get(tier).getAsDouble(), armor * actual, 1E-14);
                assertEquals(row.getAsJsonArray("overall_held_material_chances").get(tier).getAsDouble(), held * actual, 1E-14);
                assertEquals(tier, BloodMoonEquipmentRules.selectOrdinaryTier(progress, cumulative + expected / 2D));
                cumulative += actual;
            }
            assertEquals(1D, cumulative, 1E-14);
        }
        // Copper/silver share their two normalized basis rows; the half-stage anchor mixes rows0/1.
        assertEquals(32D / 565D, BloodMoonEquipmentRules.ordinaryTierChance(3.5D, 0), 1E-15);
        assertEquals(64D / 565D, BloodMoonEquipmentRules.ordinaryTierChance(3.5D, 5), 1E-15);
        assertEquals(14677D / 164034105D, BloodMoonEquipmentRules.ordinaryTierChance(.5D, 8), 1E-15);
    }

    @Test void ordinaryProbabilitiesGrowDailyAndNeverOutrunBloodAtAnyMaterialThreshold() {
        for (long day = 64; day <= 1152; day++) {
            double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
            double previous = BloodMoonEquipmentRules.ordinaryProgress(day - 1);
            assertTrue(BloodMoonEquipmentRules.ordinaryArmorChance(progress) >= BloodMoonEquipmentRules.ordinaryArmorChance(previous));
            assertTrue(BloodMoonEquipmentRules.ordinaryHeldChance(progress) >= BloodMoonEquipmentRules.ordinaryHeldChance(previous));
            double probability = 0D;
            for (int tier = 0; tier < 9; tier++) {
                double actual = BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                assertTrue(actual > 0D, "Older materials must remain possible at day " + day);
                probability += actual;
            }
            assertEquals(1D, probability, 1E-14);
            assertOrdinaryDoesNotOutrunBlood(day);
            for (int threshold = 1; threshold < 9; threshold++) {
                double currentTail = 0D, previousTail = 0D;
                for (int tier = threshold; tier < 9; tier++) {
                    currentTail += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                    previousTail += BloodMoonEquipmentRules.ordinaryTierChance(previous, tier);
                }
                assertTrue(currentTail + 1E-14 >= previousTail, "High-material tail regressed at day " + day);
            }
        }
        for (long day : new long[]{1153, 4096, Long.MAX_VALUE}) assertOrdinaryDoesNotOutrunBlood(day);
    }

    @Test void ordinaryMaterialDrawPreservesEveryCumulativeBoundaryAndRejectsInvalidInputs() {
        for (double progress : new double[]{0D, .25D, .5D, 1D, 3.5D, 4.125D, 7.9921875D, 8D}) {
            double boundary = 0D;
            assertEquals(0, BloodMoonEquipmentRules.selectOrdinaryTier(progress, 0D));
            assertEquals(8, BloodMoonEquipmentRules.selectOrdinaryTier(progress, Math.nextDown(1D)));
            for (int tier = 0; tier < 8; tier++) {
                boundary += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                assertEquals(tier, BloodMoonEquipmentRules.selectOrdinaryTier(progress, Math.nextDown(boundary)));
                assertEquals(tier + 1, BloodMoonEquipmentRules.selectOrdinaryTier(progress, boundary));
            }
        }
        for (double invalid : new double[]{-1D, Math.nextUp(8D), Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryArmorChance(invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryHeldChance(invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryTierChance(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectOrdinaryTier(invalid, 0D));
        }
        for (double invalid : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectOrdinaryTier(0D, invalid));
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryTierChance(0D, -1));
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryTierChance(8D, 9));
    }

    private static void assertOrdinaryDoesNotOutrunBlood(long day) {
        double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
        int bloodStage = BloodMoonEquipmentRules.stage(BloodMoonEquipmentRules.bloodMoons(day));
        double ordinaryArmor = BloodMoonEquipmentRules.ordinaryArmorChance(progress);
        double ordinaryHeld = BloodMoonEquipmentRules.ordinaryHeldChance(progress);
        double bloodArmor = BloodMoonEquipmentRules.armorChance(bloodStage, true);
        double bloodHeld = BloodMoonEquipmentRules.heldChance(bloodStage, true);
        assertTrue(ordinaryArmor <= bloodArmor, "Ordinary armor rate outran blood at day " + day);
        assertTrue(ordinaryHeld <= bloodHeld, "Ordinary held rate outran blood at day " + day);
        for (int threshold = 1; threshold < 9; threshold++) {
            double ordinaryTail = 0D, bloodTail = 0D;
            for (int tier = threshold; tier < 9; tier++) {
                ordinaryTail += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                bloodTail += BloodMoonEquipmentRules.tierChance(bloodStage, tier, true);
            }
            String detail = "day " + day + ", material threshold " + threshold;
            assertTrue(ordinaryTail <= bloodTail + 1E-14, "Conditional material tail outran blood at " + detail);
            assertTrue(ordinaryArmor * ordinaryTail <= bloodArmor * bloodTail + 1E-14, "Per-slot armor material tail outran blood at " + detail);
            assertTrue(ordinaryHeld * ordinaryTail <= bloodHeld * bloodTail + 1E-14, "Overall held material tail outran blood at " + detail);
        }
    }

    private static double rational(String value) {
        String[] parts = value.split("/");
        return Double.parseDouble(parts[0]) / (parts.length == 1 ? 1D : Double.parseDouble(parts[1]));
    }
}
