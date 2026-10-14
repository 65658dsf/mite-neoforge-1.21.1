package io.github.ningmeng123.mite.rules;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Independent material-family oracle, using the extracted release registry rather than implementation loops. @author NingMeng123 */
final class MaterialContentEvidenceTest {
    @Test void sourceMaterialProfilesMatchLiteralReleaseFamilies() throws Exception {
        var data = JsonParser.parseString(Files.readString(Path.of("spec/materials-028.json"))).getAsJsonObject();
        var items = data.getAsJsonArray("items");
        assertEquals(18, items.size());
        for (var value : items) {
            var item = value.getAsJsonObject();
            String id = item.get("id").getAsString();
            boolean sinew = id.endsWith(":sinew"), ingot = id.endsWith("_ingot"), adamantium = id.contains(":adamantium_");
            assertEquals(sinew ? 16 : ingot ? 8 : 64, item.get("maximum_stack").getAsInt());
            assertEquals(sinew, item.get("harmed_by_fire").getAsBoolean());
            assertEquals(!adamantium, item.get("harmed_by_lava").getAsBoolean());
            assertEquals(sinew, item.get("harmed_by_pepsin").getAsBoolean());
            assertTrue(item.get("component_difficulty").getAsFloat() > 0F);
        }
    }
    @Test void remainingScopeKeepsSixtyLeavesSeparateFromAlreadyDeliveredContent() throws Exception {
        var scope = JsonParser.parseString(Files.readString(Path.of("spec/remaining-catalog-028.json"))).getAsJsonObject();
        assertEquals(60, scope.getAsJsonArray("entries").size());
        var categories = scope.getAsJsonObject("category_counts");
        assertEquals(18, categories.get("材料").getAsInt());
        assertEquals(12, categories.get("杂项").getAsInt());
        assertEquals(6, categories.get("硬币").getAsInt());
        assertEquals(6, categories.get("武器").getAsInt());
        assertEquals(6, categories.get("红石").getAsInt());
        assertEquals(12, categories.get("装饰品").getAsInt());
    }
}
