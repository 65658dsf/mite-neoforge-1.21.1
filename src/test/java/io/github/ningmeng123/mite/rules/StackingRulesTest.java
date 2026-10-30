package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Independent source constants and complete oracle disposition coverage. @author NingMeng123 */
final class StackingRulesTest {
    @Test void leavesStackToEightWithoutChangingOtherBotanicalMaterials() {
        for(String tree:new String[]{"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","azalea","flowering_azalea"})
            assertEquals(8,StackingRules.limit("minecraft:"+tree+"_leaves"),tree);
        assertEquals(4,StackingRules.limit("minecraft:oak_log"));
        assertEquals(8,StackingRules.limit("minecraft:oak_planks"));
        assertEquals(4,StackingRules.limit("minecraft:raw_iron"));
        assertEquals(8,StackingRules.limit("minecraft:iron_ingot"));
    }
    @Test void commonSourceBlocksAndItemsKeepTheirLiteralLimits() {
        String[] items = {"stone", "cobblestone", "dirt", "oak_log", "oak_planks", "torch", "brewing_stand", "bucket", "iron_ingot", "iron_nugget", "diamond", "flint"};
        int[] limits = {4, 4, 4, 4, 8, 16, 16, 8, 8, 64, 32, 16};
        for (int i = 0; i < items.length; i++) assertEquals(limits[i], StackingRules.limit("minecraft:" + items[i]), items[i]);
    }

    @Test void metalIngotsStackToEightWhileRawMineralsStayAtFour() {
        for (String material : new String[]{"copper", "gold", "iron", "netherite"})
            assertEquals(8, StackingRules.limit("minecraft:" + material + "_ingot"));
        for (String material : new String[]{"copper", "gold", "iron"})
            assertEquals(4, StackingRules.limit("minecraft:raw_" + material));
        for (String material : new String[]{"silver", "ancient_metal", "mithril", "adamantium"})
            assertEquals(8, StackingRules.limit("mite:" + material + "_ingot"));
        assertEquals(64, StackingRules.limit("minecraft:iron_nugget"));
        assertEquals(32, StackingRules.limit("minecraft:diamond"));
        assertEquals(4, StackingRules.limit("minecraft:iron_ore"));
        assertEquals(4, StackingRules.limit("mite:deepslate_silver_ore"));
    }

    @Test void snowSubtypeDepthHasTheIndependentSourceException() {
        assertEquals(32, StackingRules.snowLimit(1));
        for (int layers = 2; layers <= 8; layers++) assertEquals(8, StackingRules.snowLimit(layers));
    }

    @Test void foreignIdsHaveNoInferredSourceFamily() {
        assertEquals(0, StackingRules.limit("another_mod:stone"));
        assertEquals(0, StackingRules.limit("another_mod:iron_ingot"));
        assertThrows(UnsupportedOperationException.class, () -> StackingRules.limits().put("another_mod:stone", 4));
    }

    @Test void allSourceRegistrationsHaveADispositionAndNoConflictingLimits() throws Exception {
        var source = JsonParser.parseString(Files.readString(Path.of("spec/stacking-source-030.json"))).getAsJsonObject();
        var mapping = JsonParser.parseString(Files.readString(Path.of("spec/stacking-map-030.json"))).getAsJsonObject();
        assertEquals(667, source.getAsJsonArray("items").size());
        assertEquals(221, source.getAsJsonArray("blocks").size());
        assertEquals(667, mapping.getAsJsonArray("source_dispositions").size());
        var ids = new HashSet<Integer>();
        mapping.getAsJsonArray("source_dispositions").forEach(row -> assertTrue(ids.add(row.getAsJsonObject().get("legacy_id").getAsInt())));
        source.getAsJsonArray("items").forEach(row -> assertTrue(ids.contains(row.getAsJsonObject().get("legacy_id").getAsInt())));
        assertTrue(mapping.getAsJsonArray("gaps").isEmpty());
        assertTrue(mapping.getAsJsonArray("conflicts").isEmpty());
    }
}
