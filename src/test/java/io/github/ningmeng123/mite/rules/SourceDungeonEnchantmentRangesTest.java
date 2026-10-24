package io.github.ningmeng123.mite.rules;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.enchantment.R196EnchantmentCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The archived source evidence independently fixes every chest-book level range. @author NingMeng123 */
class SourceDungeonEnchantmentRangesTest {
    @Test void allThirtyEightRangesMatchTheAuditedR196Catalog() throws Exception {
        var definitions = R196EnchantmentCatalog.sourceDefinitions();
        assertEquals(38, definitions.size());
        try (var reader = Files.newBufferedReader(Path.of("spec/enchantments-r196-catalog.json"))) {
            var source = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("enchantments");
            assertEquals(38, source.size());
            for (var value : source) {
                var entry = value.getAsJsonObject();
                int id = entry.get("id").getAsInt();
                var definition = definitions.stream().filter(candidate -> candidate.legacyId() == id).findFirst().orElseThrow();
                assertEquals(entry.get("max_level").getAsInt(), definition.sourceGeneratedLevel(),
                        "Source book range for " + definition.resourceId());
            }
        }
    }
}
