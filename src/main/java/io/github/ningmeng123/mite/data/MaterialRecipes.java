package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.RecipeUnlockedTrigger;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Nine-nugget ingot conversions; timed crafting is tracked separately in the material specification. @author NingMeng123 */
public final class MaterialRecipes extends RecipeProvider {
    public MaterialRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        addRecipes(output);
    }

    public static void addRecipes(RecipeOutput output) {
        conversion(output, "copper", MiteMaterials.COPPER_NUGGET.get(), Items.COPPER_INGOT, false);
        conversion(output, "silver", MiteMaterials.SILVER_NUGGET.get(), MiteMaterials.SILVER_INGOT.get(), false);
        conversion(output, "gold", Items.GOLD_NUGGET, Items.GOLD_INGOT, true);
        conversion(output, "iron", Items.IRON_NUGGET, Items.IRON_INGOT, true);
        conversion(output, "ancient_metal", MiteMaterials.ANCIENT_METAL_NUGGET.get(), MiteMaterials.ANCIENT_METAL_INGOT.get(), false);
        conversion(output, "mithril", MiteMaterials.MITHRIL_NUGGET.get(), MiteMaterials.MITHRIL_INGOT.get(), false);
        conversion(output, "adamantium", MiteMaterials.ADAMANTIUM_NUGGET.get(), MiteMaterials.ADAMANTIUM_INGOT.get(), false);
        conversion(output, "vibranium", MiteMaterials.VIBRANIUM_NUGGET.get(), MiteMaterials.VIBRANIUM_INGOT.get(), false);
        bulkUnpacking(output, "copper_ingot", Items.COPPER_BLOCK, Items.COPPER_INGOT, "copper_ingot");
        bulkUnpacking(output, "copper_ingot_from_waxed_copper_block", Items.WAXED_COPPER_BLOCK, Items.COPPER_INGOT, "copper_ingot");
        bulkUnpacking(output, "iron_ingot_from_iron_block", Items.IRON_BLOCK, Items.IRON_INGOT, "iron_ingot");
        bulkUnpacking(output, "gold_ingot_from_gold_block", Items.GOLD_BLOCK, Items.GOLD_INGOT, "gold_ingot");
    }

    private static void conversion(RecipeOutput output, String metal, Item nugget, Item ingot, boolean replaceVanilla) {
        ResourceLocation packing = ResourceLocation.fromNamespaceAndPath(replaceVanilla ? "minecraft" : Mite.MOD_ID,
                metal + "_ingot_from_nuggets");
        ResourceLocation unpacking = ResourceLocation.fromNamespaceAndPath(replaceVanilla ? "minecraft" : Mite.MOD_ID,
                replaceVanilla ? metal + "_nugget" : metal + "_nuggets_from_ingot");
        // R196 aae:6 uses a full 3x3 pattern; ItemIngot/ItemNugget have no metal-bench requirement.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ingot)
                .pattern("NNN").pattern("NNN").pattern("NNN").define('N', nugget)
                .group(metal + "_ingot")
                .unlockedBy("has_" + metal + "_nugget", has(nugget))
                .save(output, packing);
        // The original reverse recipe is shapeless and consumes one ingot, including in a 2x2 grid.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, nugget, 9).requires(ingot)
                .unlockedBy("has_" + metal + "_ingot", has(ingot))
                .save(output, unpacking);
    }

    private static void bulkUnpacking(RecipeOutput output, String path, Item block, Item ingot, String group) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(path);
        Advancement.Builder advancement = output.advancement()
                .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(id))
                .addCriterion("has_block", has(block))
                .rewards(AdvancementRewards.Builder.recipe(id))
                .requirements(AdvancementRequirements.Strategy.OR);
        output.accept(id, new BulkMaterialRecipe(group, Ingredient.of(block), new ItemStack(ingot, 9)),
                advancement.build(id.withPrefix("recipes/misc/")));
    }
}
