package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent rational tables, same-date half rates and saved-stage compatibility. @author NingMeng123 */
class BloodMoonEquipmentRulesTest {
    private static JsonObject specification() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("spec/blood-moon-equipment.json"))).getAsJsonObject();
    }

    @Test void bloodMoonCountSkipsBlueDates() {
        long[] days = {0, 31, 32, 63, 64, 96, 127, 128, 159, 160, 256, 288, 319, 320, 351, 352, 736};
        long[] counts = {0, 0, 1, 1, 2, 3, 3, 3, 3, 4, 6, 7, 7, 8, 8, 9, 18};
        for (int index = 0; index < days.length; index++)
            assertEquals(counts[index], BloodMoonEquipmentRules.bloodMoons(days[index]));
        assertEquals(0L, BloodMoonEquipmentRules.bloodMoons(Long.MIN_VALUE));
    }

    @Test void numberedBloodMoonsUseTheirDefaultCalendarStages() {
        assertEquals(-1, BloodMoonEquipmentRules.stage(0));
        int[] expected = {1, 5, 6, 9, 9, 9, 9, 9, 9};
        for (int index = 0; index < expected.length; index++)
            assertEquals(expected[index], BloodMoonEquipmentRules.stage(1L + index));
        assertEquals(9, BloodMoonEquipmentRules.stage(Long.MAX_VALUE));
        assertEquals("minecraft:iron_", BloodMoonEquipmentRules.PREFIXES.get(5));
        assertEquals("mite:adamantium_", BloodMoonEquipmentRules.PREFIXES.get(8));
        assertEquals("mite:vibranium_", BloodMoonEquipmentRules.PREFIXES.get(9));
    }

    @Test void day64FavorsIronAndTheThirdBloodMoonFavorsAncientMetal() {
        double ironStage = BloodMoonEquipmentRules.ordinaryProgress(64);
        double ironSlotChance = BloodMoonEquipmentRules.ordinaryArmorChance(ironStage)
                * BloodMoonEquipmentRules.ordinaryTierChance(ironStage, 5);
        assertEquals(72000D / 348797D, ironSlotChance, 1E-15);

        int stage = BloodMoonEquipmentRules.stageForDay(96);
        double ancientMetal = BloodMoonEquipmentRules.tierChance(stage, 6, true);
        for (int tier = 0; tier <= BloodMoonEquipmentRules.HIGHEST_TIER; tier++) {
            if (tier != 6) assertTrue(ancientMetal > BloodMoonEquipmentRules.tierChance(stage, tier, true));
        }
        double ancientMetalPerSlot = BloodMoonEquipmentRules.armorChance(stage, true) * ancientMetal;
        assertEquals(2944000D / 6910827D, ancientMetalPerSlot, 1E-15);
        assertEquals(.8914442188091787D, 1D - Math.pow(1D - ancientMetalPerSlot, 4), 1E-15);
    }

    @Test void configuredArmorWindowsAndRatesAreIndependentAndRangesCannotOverlap() {
        var rows = new ArrayList<>(MonsterArmorRules.DEFAULTS.rows());
        rows.set(0, new MonsterArmorRules.Row(17, 20, 71.25, 0));
        rows.set(1, new MonsterArmorRules.Row(22, 47, 0, 100));
        var settings = new MonsterArmorRules.Settings(rows);
        assertEquals(-1, settings.stageForDay(16));
        assertEquals(0, settings.stageForDay(17));
        assertEquals(.7125, settings.rows().get(settings.rowForDay(20)).chance(false));
        assertEquals(0, settings.rows().get(settings.rowForDay(20)).chance(true));
        assertEquals(-1, settings.stageForDay(21));
        assertEquals(1, settings.stageForDay(22));
        assertEquals(1, settings.rows().get(settings.rowForDay(22)).chance(true));
        assertEquals(9, settings.stageForDay(Long.MAX_VALUE));
        rows.set(0, new MonsterArmorRules.Row(17, 22, 70, 40));
        assertThrows(IllegalArgumentException.class, () -> new MonsterArmorRules.Settings(rows));
        assertThrows(IllegalArgumentException.class, () -> new MonsterArmorRules.Row(0, 20, 70, 40));
        assertThrows(IllegalArgumentException.class, () -> new MonsterArmorRules.Row(17, 16, 70, 40));
        assertThrows(IllegalArgumentException.class, () -> new MonsterArmorRules.Row(17, 20, 101, 40));
        assertThrows(IllegalArgumentException.class, () -> new MonsterArmorRules.Row(17, 20, 70, Double.NaN));
    }

    @Test void preDay16UsesLegacyArmorAndFirstBloodUsesTheChainmailStage() throws Exception {
        assertEquals(.3D, BloodMoonEquipmentRules.EARLY_ORDINARY_ARMOR_CHANCE);
        assertEquals(.6D, BloodMoonEquipmentRules.FIRST_BLOOD_ARMOR_CHANCE);
        assertEquals(BloodMoonEquipmentRules.FIRST_BLOOD_ARMOR_CHANCE / 2D, BloodMoonEquipmentRules.EARLY_ORDINARY_ARMOR_CHANCE);
        for (long day : new long[]{Long.MIN_VALUE, 0, 1, 15}) {
            assertFalse(BloodMoonEquipmentRules.ordinaryActive(day));
            assertEquals(0D, BloodMoonEquipmentRules.ordinaryProgress(day));
        }
        assertTrue(BloodMoonEquipmentRules.ordinaryActive(16));
        assertTrue(BloodMoonEquipmentRules.ordinaryActive(Long.MAX_VALUE));
        assertEquals(.82D, BloodMoonEquipmentRules.armorChance(BloodMoonEquipmentRules.stageForDay(32), true));
        var spec = specification();
        assertEquals(.3D, spec.get("early_ordinary_legacy_armor_chance").getAsDouble());
        assertEquals(.6D, spec.get("first_blood_date_legacy_armor_chance").getAsDouble());
        assertTrue(spec.get("first_blood_date_material_and_partial_stop_changed").getAsBoolean());
    }

    @Test void allTwentyDistributionsMatchIndependentRationalSpecification() throws Exception {
        var spec = specification();
        assertEquals(4, spec.get("schema_version").getAsInt());
        assertEquals("NingMeng123", spec.get("author").getAsString());
        assertTrue(spec.get("user_requested_modern_extension").getAsBoolean());
        assertFalse(spec.get("r196_source_probabilities").getAsBoolean());
        assertEquals(10, spec.getAsJsonArray("stages").size());
        for (var element : spec.getAsJsonArray("stages")) {
            var row = element.getAsJsonObject(); int stage = row.get("stage").getAsInt();
            assertEquals(row.get("prefix").getAsString(), BloodMoonEquipmentRules.PREFIXES.get(stage));
            for (boolean blood : new boolean[]{false, true}) {
                String prefix = blood ? "blood" : "ordinary";
                double armor = BloodMoonEquipmentRules.armorChance(stage, blood);
                assertEquals(row.get(prefix + "_armor_chance").getAsDouble(), armor, 1E-15);
                assertEquals(row.get(prefix + "_held_chance").getAsDouble(), BloodMoonEquipmentRules.heldChance(stage, blood), 1E-15);
                double empty = row.get(prefix + "_unarmored_chance").getAsDouble();
                assertEquals(empty, 1D - armor, 1E-14);
                double sum = 0D, overallSum = empty;
                for (int tier = 0; tier < 10; tier++) {
                    double expected = rational(row.getAsJsonArray(prefix + "_conditional_material_fractions").get(tier).getAsString());
                    assertTrue(expected > 0D, "Every material remains possible at every stage");
                    assertEquals(expected, BloodMoonEquipmentRules.tierChance(stage, tier, blood), 1E-14);
                    assertEquals(expected, row.getAsJsonArray(prefix + "_conditional_material_chances").get(tier).getAsDouble(), 1E-14);
                    double overall = row.getAsJsonArray(prefix + "_overall_material_chances").get(tier).getAsDouble();
                    assertEquals(overall, armor * expected, 1E-14);
                    assertEquals(tier, BloodMoonEquipmentRules.selectTier(stage, blood, sum + expected / 2D));
                    sum += expected; overallSum += overall;
                }
                assertEquals(1D, sum, 1E-14);
                assertEquals(1D, overallSum, 1E-14);
            }
        }
    }

    @Test void everyOrdinaryStageAndMaterialMarginalIsHalfBloodWithIdenticalConditionalMaterials() {
        for (int stage = 0; stage <= 9; stage++) {
            double bloodArmor = BloodMoonEquipmentRules.armorChance(stage, true);
            double bloodHeld = BloodMoonEquipmentRules.heldChance(stage, true);
            assertEquals(bloodArmor / 2D, BloodMoonEquipmentRules.armorChance(stage, false));
            assertEquals(bloodHeld / 2D, BloodMoonEquipmentRules.heldChance(stage, false));
            assertEquals(bloodArmor / 2D, BloodMoonEquipmentRules.ordinaryArmorChance(stage), 1E-15);
            assertEquals(bloodHeld / 2D, BloodMoonEquipmentRules.ordinaryHeldChance(stage), 1E-15);
            for (int tier = 0; tier <= 9; tier++) {
                double conditional = BloodMoonEquipmentRules.tierChance(stage, tier, true);
                assertEquals(conditional, BloodMoonEquipmentRules.tierChance(stage, tier, false));
                assertEquals(conditional, BloodMoonEquipmentRules.ordinaryTierChance(stage, tier));
                assertEquals(bloodArmor * conditional / 2D,
                        BloodMoonEquipmentRules.ordinaryArmorChance(stage) * conditional, 1E-15);
                assertEquals(bloodHeld * conditional / 2D,
                        BloodMoonEquipmentRules.ordinaryHeldChance(stage) * conditional, 1E-15);
            }
        }
        assertEquals(.4D, BloodMoonEquipmentRules.ordinaryArmorChance(0));
        assertEquals(.35D, BloodMoonEquipmentRules.ordinaryHeldChance(0));
        assertEquals(.49D, BloodMoonEquipmentRules.ordinaryArmorChance(9));
        assertEquals(.485D, BloodMoonEquipmentRules.ordinaryHeldChance(9));
    }

    @Test void highestDateAnchorsMatchIndependentStagesAndRationalHalfProbabilityTables() throws Exception {
        var calendar = specification().getAsJsonObject("ordinary_calendar_progression");
        assertEquals(16L, calendar.get("introduced_from_day").getAsLong());
        assertEquals(144L, calendar.get("maximum_day").getAsLong());
        assertEquals(36, calendar.getAsJsonArray("anchors").size());
        for (var element : calendar.getAsJsonArray("anchors")) {
            var row = element.getAsJsonObject(); long day = row.get("day").getAsLong();
            double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
            assertEquals(row.get("progress").getAsDouble(), progress);
            assertEquals(rational(row.get("progress_fraction").getAsString()), progress);
            double armor = BloodMoonEquipmentRules.ordinaryArmorChance(progress);
            double held = BloodMoonEquipmentRules.ordinaryHeldChance(progress);
            assertEquals(row.get("armor_chance").getAsDouble(), armor, 1E-14);
            assertEquals(row.get("held_chance").getAsDouble(), held, 1E-14);
            assertEquals(row.get("unarmored_chance").getAsDouble(), 1D - armor, 1E-14);
            assertEquals(row.get("empty_held_chance").getAsDouble(), 1D - held, 1E-14);
            for (int tier = 0; tier <= 9; tier++) {
                double expected = rational(row.getAsJsonArray("conditional_material_fractions").get(tier).getAsString());
                assertEquals(expected, BloodMoonEquipmentRules.ordinaryTierChance(progress, tier), 1E-14);
                assertEquals(row.getAsJsonArray("overall_armor_material_chances").get(tier).getAsDouble(), armor * expected, 1E-14);
                assertEquals(row.getAsJsonArray("overall_held_material_chances").get(tier).getAsDouble(), held * expected, 1E-14);
            }
        }
    }

    @Test void calendarThresholdsAdvanceTheSharedStageIncludingTheDay128BlueMoon() {
        for (long day = 16; day <= 1281; day++) {
            double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
            assertEquals(BloodMoonEquipmentRules.stageForDay(day), progress, "Same highest-date material stage at day " + day);
            double previous = BloodMoonEquipmentRules.ordinaryProgress(day - 1);
            assertTrue(progress >= previous);
            for (int threshold = 0; threshold < 10; threshold++) {
                double currentTail = 0D, previousTail = 0D;
                for (int tier = threshold; tier < 10; tier++) {
                    currentTail += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                    previousTail += BloodMoonEquipmentRules.ordinaryTierChance(previous, tier);
                }
                assertTrue(currentTail + 1E-14 >= previousTail, "High-material tail regressed at day " + day);
            }
        }
        long[] days = {15, 16, 31, 32, 47, 48, 63, 64, 80, 95, 96, 111, 112, 127, 128, 143, 144, 160};
        int[] stages = {-1, 0, 0, 1, 1, 3, 3, 5, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9};
        for (int index = 0; index < days.length; index++)
            assertEquals(stages[index], BloodMoonEquipmentRules.stageForDay(days[index]), "Day " + days[index]);
        assertEquals(9D, BloodMoonEquipmentRules.ordinaryProgress(Long.MAX_VALUE));
    }

    @Test void copperSilverSplitTheSameGradeIncludingCompatibleFractionalInputs() {
        int[] grades = {0, 1, 2, 3, 3, 4, 5, 6, 7, 8};
        for (int tier = 0; tier < 10; tier++) assertEquals(grades[tier], BloodMoonEquipmentRules.materialGrade(tier));
        for (double progress : new double[]{0D, .123456789D, 1D, 3D, 3.5D, 4D, 7.999D, 9D}) {
            assertEquals(BloodMoonEquipmentRules.ordinaryTierChance(progress, 3), BloodMoonEquipmentRules.ordinaryTierChance(progress, 4));
            double total = 0D;
            for (int tier = 0; tier < 10; tier++) total += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
            assertEquals(1D, total, 1E-14);
        }
        assertEquals(100000D / 435043D, BloodMoonEquipmentRules.ordinaryTierChance(4D, 3), 1E-15);
    }

    @Test void day48GivesGoldCopperAndSilverEqualLeadingChances() {
        int stage = BloodMoonEquipmentRules.stageForDay(48);
        double mixedMetal = BloodMoonEquipmentRules.ordinaryTierChance(stage, 2);
        assertEquals(90000D / 435043D, mixedMetal, 1E-15);
        for (int tier = 0; tier <= BloodMoonEquipmentRules.HIGHEST_TIER; tier++) {
            double chance = BloodMoonEquipmentRules.ordinaryTierChance(stage, tier);
            if (tier >= 2 && tier <= 4) assertEquals(mixedMetal, chance);
            else assertTrue(chance < mixedMetal);
        }
    }

    @Test void independentBloodSlotAllEmptyAndAllFourProbabilitiesRemainUnchanged() throws Exception {
        for (var element : specification().getAsJsonArray("stages")) {
            var row = element.getAsJsonObject(); int stage = row.get("stage").getAsInt();
            assertEquals(rational(row.get("blood_all_empty_armor_fraction").getAsString()), BloodMoonEquipmentRules.bloodAllEmptyChance(stage), 1E-14);
            assertEquals(rational(row.get("blood_all_four_armor_fraction").getAsString()), BloodMoonEquipmentRules.bloodAllFourChance(stage), 1E-14);
            assertTrue(BloodMoonEquipmentRules.bloodAllEmptyChance(stage) + BloodMoonEquipmentRules.bloodAllFourChance(stage) < 1D);
        }
        assertEquals(.0016D, BloodMoonEquipmentRules.bloodAllEmptyChance(0), 1E-14);
        assertEquals(.4096D, BloodMoonEquipmentRules.bloodAllFourChance(0), 1E-14);
        assertEquals(.00000256D, BloodMoonEquipmentRules.bloodAllEmptyChance(8), 1E-14);
        assertEquals(.84934656D, BloodMoonEquipmentRules.bloodAllFourChance(8), 1E-14);
    }

    @Test void independentHighTierAnchorsKeepBothTailsAndTheAdamantiumSlotChance() {
        assertEquals(640D / 650871D, BloodMoonEquipmentRules.ordinaryTierChance(0D, 8), 1E-15);
        assertEquals(117649D / 354398189D, BloodMoonEquipmentRules.ordinaryTierChance(8D, 0), 1E-15);
        assertEquals(1280000000D / 2480787323D, BloodMoonEquipmentRules.ordinaryTierChance(8D, 8), 1E-15);
        assertEquals(25600000000D / 39381511261D, BloodMoonEquipmentRules.ordinaryTierChance(9D, 9), 1E-15);
        double single = BloodMoonEquipmentRules.armorChance(8, true) * BloodMoonEquipmentRules.tierChance(8, 8, true);
        assertEquals(1228800000D / 2480787323D, single, 1E-15);
        assertEquals(.9351303447388465D, 1D - Math.pow(1D - single, 4), 1E-15);
    }

    @Test void cumulativeDrawBoundariesRemainExactForBothModesAndFractionalCompatibility() {
        for (double progress : new double[]{0D, .25D, .5D, 1D, 3.5D, 4.125D, 8D, 8.5D, 9D}) {
            double boundary = 0D;
            assertEquals(0, BloodMoonEquipmentRules.selectOrdinaryTier(progress, 0D));
            assertEquals(9, BloodMoonEquipmentRules.selectOrdinaryTier(progress, Math.nextDown(1D)));
            for (int tier = 0; tier < 9; tier++) {
                boundary += BloodMoonEquipmentRules.ordinaryTierChance(progress, tier);
                assertEquals(tier, BloodMoonEquipmentRules.selectOrdinaryTier(progress, Math.nextDown(boundary)));
                assertEquals(tier + 1, BloodMoonEquipmentRules.selectOrdinaryTier(progress, boundary));
            }
        }
        for (int stage = 0; stage < 10; stage++) for (boolean blood : new boolean[]{false, true}) {
            double boundary = 0D;
            for (int tier = 0; tier < 9; tier++) {
                boundary += BloodMoonEquipmentRules.tierChance(stage, tier, blood);
                assertEquals(tier, BloodMoonEquipmentRules.selectTier(stage, blood, Math.nextDown(boundary)));
                assertEquals(tier + 1, BloodMoonEquipmentRules.selectTier(stage, blood, boundary));
            }
        }
    }

    @Test void invalidInputsAreRejectedWithoutSilentlyChangingTheDistribution() {
        for (double invalid : new double[]{-1D, Math.nextUp(9D), Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryArmorChance(invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryHeldChance(invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.ordinaryTierChance(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectOrdinaryTier(invalid, 0D));
        }
        for (double invalid : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectTier(0, false, invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.selectOrdinaryTier(0D, invalid));
        }
        for (int invalid : new int[]{-1, 10, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.armorChance(invalid, false));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.heldChance(invalid, true));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.tierChance(invalid, 0, false));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.bloodAllEmptyChance(invalid));
            assertThrows(IllegalArgumentException.class, () -> BloodMoonEquipmentRules.bloodAllFourChance(invalid));
        }
        assertEquals(0D, BloodMoonEquipmentRules.ordinaryTierChance(0D, -1));
        assertEquals(0D, BloodMoonEquipmentRules.tierChance(9, 10, false));
    }

    private static double rational(String value) {
        String[] parts = value.split("/");
        return Double.parseDouble(parts[0]) / (parts.length == 1 ? 1D : Double.parseDouble(parts[1]));
    }
}
