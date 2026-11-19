package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Optional weapon fallbacks reuse the live shared progression without changing early scavenged loot. @author NingMeng123 */
class SimplySwordsMobRulesTest {
    private static final List<String> MATERIALS = List.of("rusted_iron", "rusted_iron", "gold", "copper", "silver",
            "iron", "ancient_metal", "mithril", "adamantium", "vibranium");

    @Test void preProgressionScavengedLootAndConversionChanceRemainUnchanged() {
        assertEquals(.15F, SimplySwordsMobRules.EQUIP_CHANCE);
        for (long day : new long[]{Long.MIN_VALUE, 0, 1, 15}) for (boolean blood : new boolean[]{false, true}) {
            assertEquals(List.of(new SimplySwordsMobRules.Material("rusted_iron", 1D)), SimplySwordsMobRules.available(day, blood));
            for (double draw : new double[]{0D, .5D, Math.nextDown(1D)})
                assertEquals("rusted_iron", SimplySwordsMobRules.selectMaterial(day, blood, draw));
        }
    }

    @Test void ordinaryFallbackUsesTheSameHighestDayBloodStageIncludingNativeIronAndVibranium() {
        for (long day : new long[]{16, 32, 48, 64, 128, 129, 320, 352, 640, 1152, 1153, 1280, Long.MAX_VALUE}) {
            var available = SimplySwordsMobRules.available(day);
            assertEquals(9, available.size());
            assertEquals(1D, available.stream().mapToDouble(SimplySwordsMobRules.Material::weight).sum(), 1E-12);
            double progress = BloodMoonEquipmentRules.ordinaryProgress(day);
            for (int step = 0; step <= 1000; step++) {
                double draw = step == 1000 ? Math.nextDown(1D) : step / 1000D;
                assertEquals(MATERIALS.get(BloodMoonEquipmentRules.selectOrdinaryTier(progress, draw)),
                        SimplySwordsMobRules.selectMaterial(day, draw), "day=" + day + " draw=" + draw);
            }
            double cumulative = 0D;
            for (var material : available) {
                assertEquals(material.name(), SimplySwordsMobRules.selectMaterial(day, cumulative + material.weight() / 2D));
                cumulative += material.weight();
            }
        }
    }

    @Test void bloodDatesReuseTheSharedCalendarStagesThroughVibranium() {
        for (long day : new long[]{16, 32, 48, 64, 96, 112, 128, 144, 160, Long.MAX_VALUE}) {
            int stage = BloodMoonEquipmentRules.stageForDay(day);
            assertEquals(1D, SimplySwordsMobRules.available(day, true).stream().mapToDouble(SimplySwordsMobRules.Material::weight).sum(), 1E-12);
            for (int step = 0; step <= 1000; step++) {
                double draw = step == 1000 ? Math.nextDown(1D) : step / 1000D;
                assertEquals(MATERIALS.get(BloodMoonEquipmentRules.selectTier(stage, true, draw)), SimplySwordsMobRules.selectMaterial(day, true, draw));
            }
        }
    }

    @Test void ordinaryAndBloodFallbacksShareEveryMaterialProbabilityAtCalendarBoundaries() {
        for (long day : new long[]{16, 31, 32, 47, 48, 63, 64, 95, 96, 111, 112, 127, 128, 143, 144, 160, 255, 256, Long.MAX_VALUE}) {
            assertEquals(SimplySwordsMobRules.available(day, true), SimplySwordsMobRules.available(day, false),
                    "Optional weapon materials share the same calendar stage at day " + day);
            for (double draw : new double[]{0D, .001D, .15D, .3D, .5D, .85D, Math.nextDown(1D)})
                assertEquals(SimplySwordsMobRules.selectMaterial(day, true, draw), SimplySwordsMobRules.selectMaterial(day, false, draw));
        }
        assertEquals(SimplySwordsMobRules.available(255), SimplySwordsMobRules.available(256));
    }

    @Test void lateWorldVibraniumRemainsReachableAndGrowsWithTheOrdinaryCurve() {
        double previous = 0D;
        for (long day = 16; day <= 1280; day++) {
            double probability = chance(day, "vibranium");
            assertTrue(probability > 0D);
            assertTrue(probability + 1E-12 >= previous, "Vibranium chance cannot fall as the highest calendar day advances: " + day);
            previous = probability;
        }
        for (long day : new long[]{32, 48, 64, 96, 112, 128, 144}) {
            assertTrue(chance(day, "vibranium") > chance(day - 1, "vibranium"));
        }
        assertTrue(chance(1280, "vibranium") > chance(1280, "adamantium"));
        assertEquals("vibranium", SimplySwordsMobRules.selectMaterial(Long.MAX_VALUE, Math.nextDown(1D)));
        for (double invalid : new double[]{-1D, 1D, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> SimplySwordsMobRules.selectMaterial(320, invalid));
    }

    private static double chance(long day, String material) {
        return SimplySwordsMobRules.available(day).stream().filter(value -> value.name().equals(material)).findFirst().orElseThrow().weight();
    }
}
