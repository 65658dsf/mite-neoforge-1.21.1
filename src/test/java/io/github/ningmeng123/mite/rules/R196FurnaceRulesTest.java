package io.github.ningmeng123.mite.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Source literals read independently from asg/SlotFuel and the original item overrides. @author NingMeng123 */
class R196FurnaceRulesTest {
    @ParameterizedTest
    @CsvSource({"mite:clay_oven,1,false,200", "mite:large_clay_oven,1,true,200", "mite:sandstone_furnace,1,true,200",
            "minecraft:furnace,2,true,200", "mite:obsidian_furnace,3,true,200", "mite:netherrack_furnace,4,true,200",
            "minecraft:blast_furnace,2,true,100", "minecraft:smoker,1,true,100"})
    void sourceSixStageProfilesAndExplicitModernDevices(String id, int maximum, boolean large, int ticks) {
        assertEquals(new R196FurnaceRules.Profile(maximum, large, ticks), R196FurnaceRules.profile(id));
    }

    @ParameterizedTest
    @CsvSource({"minecraft:charcoal,1600,1", "minecraft:coal,1600,2", "minecraft:coal_block,16000,2",
            "minecraft:lava_bucket,3200,3", "minecraft:blaze_rod,2400,4", "minecraft:paper,25,1",
            "minecraft:stick,100,1", "minecraft:arrow,100,1", "minecraft:book,100,1", "minecraft:enchanted_book,100,1",
            "minecraft:writable_book,100,1", "minecraft:written_book,100,1", "minecraft:oak_door,400,1",
            "minecraft:oak_log,1600,1", "minecraft:oak_planks,400,1", "minecraft:oak_button,400,1",
            "minecraft:oak_slab,200,1", "minecraft:oak_sapling,200,1", "minecraft:dead_bush,200,1",
            "minecraft:torch,800,1", "minecraft:redstone_torch,800,1", "minecraft:oak_boat,1200,1",
            "minecraft:bowl,200,1", "mite:manure,100,1", "minecraft:mushroom_stew,0,0", "mite:vegetable_soup,0,0"})
    void sourceFuelLiteralsKeepTemperatureIndependentOfDuration(String id, int ticks, int heat) {
        assertEquals(new R196FurnaceRules.Fuel(ticks, heat), R196FurnaceRules.fuel(id));
    }

    @ParameterizedTest
    @CsvSource({"minecraft:copper_ore,2", "minecraft:iron_ore,2", "minecraft:gold_ore,2", "mite:silver_ore,2",
            "mite:mithril_ore,3", "mite:adamantium_ore,4", "minecraft:nether_quartz_ore,2", "minecraft:emerald_ore,2",
            "minecraft:diamond_ore,2", "minecraft:redstone_ore,2", "minecraft:lapis_ore,2", "minecraft:sandstone,2",
            "minecraft:sand,1", "minecraft:beef,1", "minecraft:clay_ball,1", "minecraft:clay,1"})
    void sourceHeatRequirementsComeFromAsgRatherThanModernRecipeCategories(String id, int heat) {
        assertEquals(heat, R196FurnaceRules.requiredHeat(id));
    }

    @ParameterizedTest
    @CsvSource({"minecraft:raw_copper,2", "minecraft:raw_iron,2", "minecraft:raw_gold,2",
            "mite:raw_silver,2", "mite:raw_mithril,3", "mite:raw_adamantium,4"})
    void modernRawMetalsKeepTheOreHeatTier(String id, int heat) {
        assertEquals(heat, R196FurnaceRules.requiredHeat(id));
    }

    @ParameterizedTest
    @CsvSource({"-1,4,false,true,false", "0,4,false,true,false", "1,1,false,false,true", "1,1,true,false,false",
            "1,1,true,true,true", "2,1,false,true,false", "2,2,false,false,true", "2,2,true,true,true",
            "3,2,false,true,false", "3,3,false,true,true", "4,3,false,true,false", "4,4,true,true,true", "5,4,false,true,false"})
    void sourceFuelSlotRejectsOversizeAndOverheatInsteadOfClamping(int heat, int maximum, boolean large, boolean acceptsLarge, boolean accepted) {
        assertEquals(accepted, R196FurnaceRules.fuelAccepted(heat, maximum, large, acceptsLarge));
    }

    @ParameterizedTest
    @CsvSource({"minecraft:oak_log,true", "minecraft:oak_planks,true", "minecraft:oak_door,true", "minecraft:oak_boat,true",
            "minecraft:coal_block,true", "minecraft:coal,false", "minecraft:charcoal,false", "minecraft:stick,false",
            "minecraft:paper,false", "minecraft:torch,false", "minecraft:oak_sapling,false", "minecraft:bowl,false"})
    void originalWeLargeItemCasesStaySeparateFromFuelHeat(String id, boolean large) {
        assertEquals(large, R196FurnaceRules.large(id));
    }

    @Test void unknownExternalItemsRemainDistinctFromExplicitSourceNonFuels() {
        assertNull(R196FurnaceRules.fuel("external:test_fuel"));
        assertNull(R196FurnaceRules.profile("external:test_furnace"));
        assertNull(R196FurnaceRules.large("external:test_fuel"));
        assertEquals(1, R196FurnaceRules.requiredHeat("external:test_input"));
        assertEquals(new R196FurnaceRules.Fuel(0, 0), R196FurnaceRules.fuel("minecraft:mushroom_stew"));
    }
}
