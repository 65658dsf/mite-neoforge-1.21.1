package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies all default leaf identities, without treating identities as full-system completion. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class CompleteCatalogGameTests {
    @GameTest(template = "empty")
    public static void All347IncludedCatalogLeavesHaveRealRegisteredIdentities(GameTestHelper helper) throws Exception {
        try (var reader = new InputStreamReader(CompleteCatalogGameTests.class.getResourceAsStream("/mite/complete-catalog-028.json"), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            helper.assertValueEqual(data.getAsJsonArray("entries").size(), 347, "Complete default catalog leaf count");
            for (var element : data.getAsJsonArray("entries")) {
                var row = element.getAsJsonObject();
                var id = ResourceLocation.parse(row.get("modern_id").getAsString());
                if (row.get("source_kind").getAsString().equals("block"))
                    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Source block identity exists: " + id);
                if (row.get("block_only").getAsBoolean())
                    helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(id), "Mantle/core retain source non-carried identity");
                else helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) && !new ItemStack(BuiltInRegistries.ITEM.get(id)).isEmpty(),
                        "Source catalog leaf has a real usable item identity: " + row.get("name").getAsString() + ": " + id);
            }
        }
        helper.succeed();
    }
}
