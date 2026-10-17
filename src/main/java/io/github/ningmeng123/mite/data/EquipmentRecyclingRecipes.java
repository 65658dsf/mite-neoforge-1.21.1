package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.EquipmentRecyclingIngredient;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.compat.simplyswords.SimplySwordsCompat;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.conditions.FalseCondition;

/** Recipes declare metals; their ingredients discover all installed equipment at runtime. @author NingMeng123 */
public final class EquipmentRecyclingRecipes extends RecipeProvider {
    private EquipmentRecyclingRecipes() { super(null, null); }
    @Override protected void buildRecipes(RecipeOutput output) {}

    public static void addRecipes(RecipeOutput output) {
        for (String metal : EquipmentRecycling.metals()) {
            var nugget = EquipmentRecycling.nugget(metal);
            Ingredient equipment = new EquipmentRecyclingIngredient(metal).toVanilla();
            // Replace vanilla iron/gold entries so native armor and new weapons have one recipe each.
            String namespace = metal.equals("iron") || metal.equals("gold") ? "minecraft" : Mite.MOD_ID;
            SimpleCookingRecipeBuilder.smelting(equipment, RecipeCategory.MISC, nugget, 0.1F, 200)
                    .unlockedBy("has_metal", has(nugget))
                    .save(output, ResourceLocation.fromNamespaceAndPath(namespace, metal + "_nugget_from_smelting"));
            SimpleCookingRecipeBuilder.blasting(equipment, RecipeCategory.MISC, nugget, 0.1F, 100)
                    .unlockedBy("has_metal", has(nugget))
                    .save(output, ResourceLocation.fromNamespaceAndPath(namespace, metal + "_nugget_from_blasting"));
            if (namespace.equals("minecraft")) {
                // Simply Swords supplies its own iron/gold smelting; consolidate those entries, not its unique-remnant recipes.
                SimpleCookingRecipeBuilder.smelting(equipment, RecipeCategory.MISC, nugget, 0.1F, 200)
                        .unlockedBy("has_metal", has(nugget))
                        .save(output.withConditions(FalseCondition.INSTANCE),
                                ResourceLocation.fromNamespaceAndPath(SimplySwordsCompat.MOD_ID, metal + "_melting"));
            }
        }
    }
}
