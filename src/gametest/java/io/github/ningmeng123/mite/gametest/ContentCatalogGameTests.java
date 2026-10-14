package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonParser;
import io.github.ningmeng123.mite.crafting.CraftingQuality;
import io.github.ningmeng123.mite.crafting.TimedCraftingController;
import io.github.ningmeng123.mite.crafting.WorkbenchRules;
import io.github.ningmeng123.mite.mining.MiningProgress;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Requested leaf identities and independently extracted storage-block recipe quantities. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class ContentCatalogGameTests {
    @GameTest(template = "empty")
    public static void everyRequestedCatalogLeafHasItsActualRuntimeIdentity(GameTestHelper helper) throws Exception {
        try (var reader = new InputStreamReader(ContentCatalogGameTests.class.getResourceAsStream("/mite/content-catalog-027.json"), StandardCharsets.UTF_8)) {
            var catalog = JsonParser.parseReader(reader).getAsJsonObject();
            helper.assertValueEqual(catalog.getAsJsonArray("entries").size(), 111, "All four requested categories are accounted for");
            for (var element : catalog.getAsJsonArray("entries")) {
                var entry = element.getAsJsonObject();
                var id = ResourceLocation.parse(entry.get("modern_id").getAsString());
                if (entry.get("source_kind").getAsString().equals("block")) {
                    helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Requested block exists: " + id);
                }
                if (entry.get("block_only").getAsBoolean()) {
                    helper.assertTrue(!BuiltInRegistries.ITEM.containsKey(id), "Source mantle is not a carried item");
                } else {
                    helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id) && !new ItemStack(BuiltInRegistries.ITEM.get(id)).isEmpty(), "Requested item exists: " + id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sourceMetalStoragePreservesNineIngotsAndItsDeclaredPhysicalProperties(GameTestHelper helper) {
        String[] metals = {"silver", "ancient_metal", "mithril", "adamantium"};
        float[] hardness = {4.8F, 19.2F, 76.8F, 307.2F};
        float[] difficulty = {3600F, 14400F, 57600F, 230400F};
        int[] harvest = {3, 4, 5, 6};
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int i = 0; i < metals.length; i++) {
            String metal = metals[i];
            var block = MiteMetalBlocks.STORAGE.get(metal).get();
            var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mite:" + metal + "_ingot"));
            helper.assertValueEqual(new ItemStack(block).getMaxStackSize(), 4, "Source storage stacks to four");
            helper.assertValueEqual(block.defaultBlockState().getDestroySpeed(helper.getLevel(), pos), hardness[i], "Source material-dependent hardness");
            helper.assertValueEqual(MiteMetalBlocks.requiredHarvest(metal), harvest[i], "Source storage needs one more harvest tier than its material");
            var pack = CraftingInput.of(3, 3, java.util.Collections.nCopies(9, new ItemStack(ingot)));
            var packing = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, pack, helper.getLevel()).orElseThrow();
            var packed = packing.value().assemble(pack, helper.getLevel().registryAccess());
            helper.assertTrue(packed.is(block.asItem()) && packed.getCount() == 1, "Nine ingots pack into exactly one source block");
            helper.assertValueEqual(CraftingQuality.rule(packing, packed, pack).difficulty(), difficulty[i], "Packing difficulty is independent source evidence");
            helper.assertValueEqual(CraftingQuality.rule(packing, packed, pack).requiredCapacity(), WorkbenchRules.materialCapacity(metal), "Packing uses the corresponding material workbench");
            var unpack = CraftingInput.of(1, 1, List.of(packed));
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, unpack, helper.getLevel()).orElseThrow();
            var result = recipe.value().assemble(unpack, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ingot) && result.getCount() == 9 && result.getMaxStackSize() == 8, "Unpacking retains the full nine-ingot batch");
            helper.assertValueEqual(CraftingQuality.rule(recipe, result, unpack).requiredCapacity(), 0F, "Unpacking does not require metal equipment");
        }
        helper.succeed();
    }
}
