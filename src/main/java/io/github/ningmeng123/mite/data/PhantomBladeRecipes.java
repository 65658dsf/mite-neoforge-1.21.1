package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.phantom.PhantomBladeRecipe;
import io.github.ningmeng123.mite.registry.MiteMetalBlocks;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Eight same-material swords surround a metal block initially, then the preceding formation core. @author NingMeng123 */
public final class PhantomBladeRecipes {
    private PhantomBladeRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        initial(output, "copper", Items.COPPER_BLOCK);
        initial(output, "silver", MiteMetalBlocks.STORAGE.get("silver").get().asItem());
        initial(output, "gold", Items.GOLD_BLOCK);
        initial(output, "rusted_iron", Items.IRON_BLOCK);
        ring(output, "iron", Ingredient.of(MitePhantomBlades.get("copper"), MitePhantomBlades.get("silver"),
                MitePhantomBlades.get("gold"), MitePhantomBlades.get("rusted_iron")), MitePhantomBlades.get("copper"));
        ring(output, "ancient_metal", Ingredient.of(MitePhantomBlades.get("iron")), MitePhantomBlades.get("iron"));
        ring(output, "mithril", Ingredient.of(MitePhantomBlades.get("iron"), MitePhantomBlades.get("ancient_metal")), MitePhantomBlades.get("iron"));
        ring(output, "adamantium", Ingredient.of(MitePhantomBlades.get("mithril")), MitePhantomBlades.get("mithril"));
        ring(output, "vibranium", Ingredient.of(MitePhantomBlades.get("adamantium")), MitePhantomBlades.get("adamantium"));
    }
    private static void initial(RecipeOutput output, String material, Item block) { ring(output, material, Ingredient.of(block), block); }
    private static void ring(RecipeOutput output, String material, Ingredient center, Item unlock) {
        var recipe = new PhantomBladeRecipe(new ShapedRecipe("mite_phantom_blades", CraftingBookCategory.EQUIPMENT,
                ShapedRecipePattern.of(Map.of('S', Ingredient.of(MitePhantomBlades.sword(material)), 'C', center), List.of("SSS", "SCS", "SSS")),
                new ItemStack(MitePhantomBlades.get(material))));
        var id = ResourceLocation.fromNamespaceAndPath("mite", material+"_phantom_blade");
        var advancement = output.advancement().addCriterion("has_previous_core_or_block", InventoryChangeTrigger.TriggerInstance.hasItems(unlock))
                .rewards(AdvancementRewards.Builder.recipe(id));
        output.accept(id, recipe, advancement.build(id.withPrefix("recipes/combat/")));
    }
}
