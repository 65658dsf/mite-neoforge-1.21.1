package io.github.ningmeng123.mite.rules;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static io.github.ningmeng123.mite.rules.StructureUnlockRules.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent progression fixtures, including strict block-distance and vanilla variant boundaries. @author NingMeng123 */
class StructureUnlockRulesTest {
    private static Map<Metric, Long> completed() {
        Map<Metric, Long> result = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) result.put(metric, metric.cap());
        return result;
    }

    @Test void allTwentyOneDefinitionsKeepTheirIndependentDaysFlagsAndActions() {
        Map<String, List<Requirement>> expected = new HashMap<>();
        expected.put("ruined_portal", List.of()); expected.put("pillager_outpost", List.of()); expected.put("village", List.of());
        expected.put("stronghold", List.of(new Requirement(Metric.EYE_CRAFTED, 1)));
        expected.put("shipwreck", List.of(new Requirement(Metric.OCEAN_SAIL_CM, 50_001)));
        expected.put("ocean_ruin", List.of(new Requirement(Metric.OCEAN_DIVE_CM, 10_001)));
        expected.put("buried_treasure", List.of(new Requirement(Metric.OCEAN_SAIL_CM, 50_001), new Requirement(Metric.FISH_CAUGHT, 5)));
        expected.put("desert_pyramid", List.of(new Requirement(Metric.DESERT_WALK_CM, 50_001)));
        expected.put("jungle_pyramid", List.of(new Requirement(Metric.JUNGLE_WALK_CM, 50_001)));
        expected.put("swamp_hut", List.of(new Requirement(Metric.SWAMP_TRAVEL_CM, 30_001)));
        expected.put("igloo", List.of(new Requirement(Metric.SNOW_WALK_CM, 50_001)));
        expected.put("trail_ruins", List.of(new Requirement(Metric.BRUSH_CRAFTED, 1)));
        expected.put("mineshaft", List.of(new Requirement(Metric.UNDERGROUND_WALK_CM, 50_001)));
        expected.put("mansion", List.of(new Requirement(Metric.DARK_FOREST_WALK_CM, 100_001)));
        expected.put("monument", List.of(new Requirement(Metric.OCEAN_SAIL_CM, 200_001), new Requirement(Metric.OCEAN_DIVE_CM, 30_001)));
        expected.put("ancient_city", List.of(new Requirement(Metric.DEEP_SNEAK_CM, 30_001)));
        expected.put("fortress", List.of(new Requirement(Metric.NETHER_WALK_CM, 50_001)));
        expected.put("bastion_remnant", List.of(new Requirement(Metric.NETHER_WALK_CM, 100_001), new Requirement(Metric.GOLD_ARMOR_CRAFTED, 1)));
        expected.put("nether_fossil", List.of(new Requirement(Metric.SOUL_VALLEY_WALK_CM, 30_001)));
        expected.put("overworld_fossil", List.of(new Requirement(Metric.NATURAL_STONE_MINED, 256)));
        expected.put("end_city", List.of(new Requirement(Metric.DRAGON_KILLED, 1), new Requirement(Metric.END_ISLAND_WALK_CM, 50_001)));
        assertEquals(21, definitions().size());
        assertEquals(expected.keySet(), definitions().stream().map(Definition::path).collect(java.util.stream.Collectors.toSet()));
        for (Definition definition : definitions()) {
            assertEquals(expected.get(definition.path()), definition.requirements(), definition.path());
            assertEquals(definition.path().equals("village") ? 60L : definition.path().equals("pillager_outpost") ? 45L : 32L,
                    definition.minimumDay(), definition.path());
            int flags = switch (definition.path()) {
                case "village" -> 16;
                case "ruined_portal" -> 32;
                case "pillager_outpost" -> 64 | 128;
                case "mansion", "ancient_city" -> 128;
                default -> 0;
            };
            assertEquals(flags, definition.flags(), definition.path());
        }
    }

    @Test void allThirtyThreeGatedVanillaStructureIdsResolveToTheirOwnFamily() {
        Map<String, String> variants = new HashMap<>();
        for (String suffix : List.of("plains", "desert", "savanna", "snowy", "taiga")) variants.put("village_" + suffix, "village");
        variants.put("ruined_portal", "ruined_portal");
        for (String suffix : List.of("desert", "jungle", "swamp", "mountain", "ocean", "nether"))
            variants.put("ruined_portal_" + suffix, "ruined_portal");
        variants.put("mineshaft", "mineshaft"); variants.put("mineshaft_mesa", "mineshaft");
        variants.put("shipwreck", "shipwreck"); variants.put("shipwreck_beached", "shipwreck");
        variants.put("ocean_ruin_cold", "ocean_ruin"); variants.put("ocean_ruin_warm", "ocean_ruin");
        for (String path : List.of("ancient_city", "bastion_remnant", "buried_treasure", "desert_pyramid", "end_city", "fortress",
                "igloo", "jungle_pyramid", "mansion", "monument", "nether_fossil", "pillager_outpost", "stronghold", "swamp_hut", "trail_ruins"))
            variants.put(path, path);
        assertEquals(33, variants.size());
        variants.forEach((id, family) -> assertSame(find(family), forStructure("minecraft", id), id));
        assertNull(forStructure("minecraft", "trial_chambers"));
        assertNull(forStructure("future_mod", "village_plains"), "A mod cannot inherit a vanilla-only gate just by reusing its path");
        assertNull(forStructure("minecraft", "future_structure"));
    }

    @Test void everyRequirementAndEveryRequiredFlagIsAnAndCondition() {
        for (Definition definition : definitions()) {
            Map<Metric, Long> values = completed();
            assertTrue(requirementsMet(definition, 255, values), definition.path());
            for (Requirement requirement : definition.requirements()) {
                values.put(requirement.metric(), requirement.target() - 1L);
                assertFalse(requirementsMet(definition, 255, values), definition.path() + "/" + requirement.metric());
                values.put(requirement.metric(), requirement.target());
                assertTrue(requirementsMet(definition, 255, values), definition.path() + "/exact target");
                values.remove(requirement.metric());
                assertFalse(requirementsMet(definition, 255, values), definition.path() + "/missing metric");
                values.put(requirement.metric(), requirement.metric().cap());
            }
            for (int bit = 1; bit <= 128; bit <<= 1) {
                assertEquals((definition.flags() & bit) == 0, requirementsMet(definition, 255 ^ bit, values),
                        definition.path() + "/missing bit " + bit);
            }
        }
    }

    @Test void fiveHundredBlocksRequiresOneMoreCentimeterAndOtherDistancesAreAlsoStrict() {
        int fiveHundredRequirements = 0;
        for (Definition definition : definitions()) for (Requirement requirement : definition.requirements()) {
            if (!requirement.metric().distance()) continue;
            long exactBlocks = requirement.target() - 1L;
            assertEquals(0L, exactBlocks % 100L, "Each distance threshold is strictly greater than an exact number of blocks");
            assertFalse(requirement.met(Map.of(requirement.metric(), exactBlocks)), definition.path());
            assertTrue(requirement.met(Map.of(requirement.metric(), exactBlocks + 1L)), definition.path());
            if (requirement.target() == 50_001L) {
                fiveHundredRequirements++;
                assertFalse(requirement.met(Map.of(requirement.metric(), 50_000L)), definition.path() + "/500 blocks");
                assertTrue(requirement.met(Map.of(requirement.metric(), 50_001L)), definition.path() + "/500.01 blocks");
            }
        }
        assertEquals(8, fiveHundredRequirements);
    }

    @Test void thresholdsAreReachableWithoutRequiringAnotherStructureUnlock() {
        assertEquals(18, Metric.values().length);
        for (Definition definition : definitions()) {
            var criteria = new HashSet<String>();
            for (Requirement requirement : definition.requirements()) {
                assertTrue(criteria.add(requirement.criterion()), "Each action is a distinct AND criterion: " + definition.path());
                assertTrue(requirement.target() > 0 && requirement.target() <= requirement.metric().cap(), definition.path());
                assertNull(find(requirement.criterion()), "Prerequisites are observed actions rather than recursive structure achievements");
            }
        }
        for (Metric metric : Metric.values()) {
            assertEquals(0L, metric.clamp(-1L));
            assertEquals(0L, metric.clamp(Long.MIN_VALUE));
            assertEquals(metric.cap(), metric.clamp(Long.MAX_VALUE));
            assertEquals(metric.cap() - 1L, metric.clamp(metric.cap() - 1L));
        }
    }

    @Test void dayFallbackAndTrialExemptionsCoverFutureModsWithoutWeakeningKnownDays() {
        for (String[] id : List.of(new String[]{"future_mod", "new_ruin"}, new String[]{"minecraft", "future_structure"},
                new String[]{"future_mod", "trial_chambers"})) {
            assertFalse(dayUnlocked(id[0], id[1], 31L));
            assertTrue(dayUnlocked(id[0], id[1], 32L));
        }
        for (String[] id : List.of(new String[]{"minecraft", "trial_chambers"}, new String[]{"mite", "underworld_trial_chambers"})) {
            assertTrue(exempt(id[0], id[1]));
            assertTrue(dayUnlocked(id[0], id[1], 1L));
        }
        assertFalse(exempt("future_mod", "trial_chambers"));
        for (Definition definition : definitions()) {
            assertFalse(dayUnlocked("minecraft", definition.path(), definition.minimumDay() - 1L), definition.path());
            assertTrue(dayUnlocked("minecraft", definition.path(), definition.minimumDay()), definition.path());
        }
    }
}
