package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Independent source constants and complete oracle disposition coverage. @author NingMeng123 */
final class StackingRulesTest {
    @Test void commonSourceBlocksAndItemsKeepTheirLiteralLimits() {
        String[] items = {"stone", "cobblestone", "dirt", "oak_log", "oak_planks", "torch", "brewing_stand", "bucket", "iron_ingot", "iron_nugget", "diamond", "flint"};
        int[] limits = {4, 4, 4, 4, 8, 16, 16, 8, 8, 64, 32, 16};
        for (int i = 0; i < items.length; i++) assertEquals(limits[i], StackingRules.limit("minecraft:" + items[i]), items[i]);
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
