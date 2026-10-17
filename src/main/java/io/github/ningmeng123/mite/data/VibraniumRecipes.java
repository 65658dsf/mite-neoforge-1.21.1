package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteMaterials;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.registry.MiteFunctionalBlocks;
import io.github.ningmeng123.mite.registry.MiteTools;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** Extreme LH diamond alloy and existing MITE equipment shapes. @author NingMeng123 */
public final class VibraniumRecipes {
    private VibraniumRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        var ingot = MiteMaterials.VIBRANIUM_INGOT.get();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ingot)
                .pattern("NIN").pattern("IDI").pattern("NIN")
                .define('N', MiteMaterials.MITHRIL_NUGGET.get()).define('I', MiteMaterials.ADAMANTIUM_INGOT.get())
                .define('D', Items.DIAMOND)
                .unlockedBy("has_adamantium", InventoryChangeTrigger.TriggerInstance.hasItems(MiteMaterials.ADAMANTIUM_INGOT.get()))
                .save(output, ResourceLocation.parse("mite:vibranium_ingot"));
        // Source-native upgrades are generated separately by generate_vibranium_recipes.py.
        Map<String, String[]> shapes = new LinkedHashMap<>();
        shapes.put("hatchet", new String[]{"SI", "S "});
        shapes.put("hoe", new String[]{"II", " S", " S"});
        shapes.put("mattock", new String[]{"III", " SI", " S "});
        shapes.put("scythe", new String[]{"SI ", "S I", "S  "});
        shapes.put("shears", new String[]{" I", "I "});
        shapes.put("knife", new String[]{"I", "S"});
        shapes.forEach((family, shape) -> {
            var recipe = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, MiteTools.get("vibranium_" + family))
                    .define('I', ingot).unlockedBy("has_vibranium", InventoryChangeTrigger.TriggerInstance.hasItems(ingot));
            if (String.join("", shape).contains("S")) recipe.define('S', Items.STICK);
            for (String row : shape) recipe.pattern(row);
            recipe.save(output, ResourceLocation.parse("mite:vibranium_" + family));
        });
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteFunctionalBlocks.ANVILS.get("vibranium").get())
                .pattern("BBB").pattern(" I ").pattern("III").define('B', MiteMetalBlocks.STORAGE.get("vibranium").get())
                .define('I', ingot).unlockedBy("has_vibranium", InventoryChangeTrigger.TriggerInstance.hasItems(ingot))
                .save(output, ResourceLocation.parse("mite:vibranium_anvil"));
    }
}
