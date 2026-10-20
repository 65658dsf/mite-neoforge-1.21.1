package io.github.ningmeng123.mite.data;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.conditions.FalseCondition;

/** Disable native diamond crafting and netherite upgrades together with their recipe-book entries. @author NingMeng123 */
public final class DiamondEquipmentRecipes extends RecipeProvider {
    private static final List<String> EQUIPMENT = List.of(
            "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");

    private DiamondEquipmentRecipes() { super(null, null); }

    @Override protected void buildRecipes(RecipeOutput output) {}

    public static void addRecipes(RecipeOutput output) {
        RecipeOutput disabled = output.withConditions(FalseCondition.INSTANCE);
        for (String family : EQUIPMENT) {
            RecipeCategory category = family.equals("sword") || List.of("helmet", "chestplate", "leggings", "boots").contains(family)
                    ? RecipeCategory.COMBAT : RecipeCategory.TOOLS;
            ResourceLocation diamondId = ResourceLocation.withDefaultNamespace("diamond_" + family);
            // These conditioned placeholders replace the native recipe IDs and their
            // recipe-book advancements. Diamond horse armor has no native recipe.
            ShapelessRecipeBuilder.shapeless(category, BuiltInRegistries.ITEM.get(diamondId))
                    .requires(Items.DIAMOND).unlockedBy("has_diamond", has(Items.DIAMOND))
                    .save(disabled, diamondId);

            var netherite = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("netherite_" + family));
            ShapelessRecipeBuilder.shapeless(category, netherite)
                    .requires(Items.NETHERITE_INGOT).unlockedBy("has_netherite_ingot", has(Items.NETHERITE_INGOT))
                    .save(disabled, ResourceLocation.withDefaultNamespace("netherite_" + family + "_smithing"));
        }
    }
}
