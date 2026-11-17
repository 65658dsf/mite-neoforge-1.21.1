package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteItems;
import io.github.ningmeng123.mite.registry.MiteTools;
import io.github.ningmeng123.mite.registry.MiteWorkbenches;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.FalseCondition;

/** RecipesMITE 113-131; different log/plank species share a modern table identity. @author NingMeng123 */
public final class WorkbenchRecipes extends RecipeProvider {
    private WorkbenchRecipes() { super(null, null); }
    public static void addRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, Items.CRAFTING_TABLE)
                .pattern("PP").pattern("PP").define('P', ItemTags.PLANKS)
                .unlockedBy("has_planks", has(ItemTags.PLANKS))
                .save(output.withConditions(FalseCondition.INSTANCE), ResourceLocation.withDefaultNamespace("crafting_table"));
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorkbenches.TABLES.get("obsidian").get())
                .pattern("OB").pattern("SL").define('O', Items.OBSIDIAN)
                .define('B', Ingredient.of(Items.STRING, MiteItems.SINEW.get())).define('S', Items.STICK)
                .define('L', ItemTags.LOGS).unlockedBy("has_obsidian", has(Items.OBSIDIAN))
                .save(output, ResourceLocation.fromNamespaceAndPath("mite", "obsidian_crafting_table"));
        for (String material : new String[]{"flint", "obsidian"}) {
            var knife = MiteTools.get(material + "_knife");
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorkbenches.TABLES.get(material).get())
                    .pattern("K").pattern("L").define('K', knife).define('L', ItemTags.LOGS)
                    .unlockedBy("has_knife", has(knife))
                    .save(output, ResourceLocation.fromNamespaceAndPath("mite", material + "_crafting_table_from_knife"));
        }
        for (String material : new String[]{"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium", "vibranium"}) {
            String namespace = material.equals("copper") || material.equals("iron") || material.equals("gold") ? "minecraft" : "mite";
            var ingot = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(namespace, material + "_ingot"));
            ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, MiteWorkbenches.TABLES.get(material).get())
                    .pattern("IL").pattern("SP").define('I', ingot).define('L', Items.LEATHER)
                    .define('S', Items.STICK).define('P', ItemTags.PLANKS)
                    .unlockedBy("has_ingot", has(ingot))
                    .save(output, ResourceLocation.fromNamespaceAndPath("mite", material + "_crafting_table"));
        }
        // The retired automatic crafter has no obtainable recipe.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, Items.CRAFTER)
                .pattern("III").pattern("ITI").pattern("RDR").define('I', Items.IRON_INGOT)
                .define('T', MiteWorkbenches.TABLES.get("flint").get()).define('R', Items.REDSTONE)
                .define('D', Items.DROPPER).unlockedBy("has_iron", has(Items.IRON_INGOT))
                .save(output.withConditions(FalseCondition.INSTANCE), ResourceLocation.withDefaultNamespace("crafter"));
    }
}
