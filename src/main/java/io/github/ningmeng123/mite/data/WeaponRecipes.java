package io.github.ningmeng123.mite.data;

import io.github.ningmeng123.mite.registry.MiteWeaponContent;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

/** aam source orders 38 and 39, three sticks, three strings and one reinforcement ingot. @author NingMeng123 */
public final class WeaponRecipes {
    private WeaponRecipes() {}
    public static void addRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, MiteWeaponContent.ANCIENT_METAL_BOW.get())
                .pattern(" #X").pattern("#IX").pattern(" #X").define('#', Items.STICK).define('X', Items.STRING)
                .define('I', MiteMaterials.ANCIENT_METAL_INGOT.get())
                .unlockedBy("has_ancient_metal", InventoryChangeTrigger.TriggerInstance.hasItems(MiteMaterials.ANCIENT_METAL_INGOT.get()))
                .save(output, ResourceLocation.parse("mite:ancient_metal_bow"));
        ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, MiteWeaponContent.MITHRIL_BOW.get())
                .pattern(" #X").pattern("#IX").pattern(" #X").define('#', Items.STICK).define('X', Items.STRING)
                .define('I', MiteMaterials.MITHRIL_INGOT.get())
                .unlockedBy("has_mithril", InventoryChangeTrigger.TriggerInstance.hasItems(MiteMaterials.MITHRIL_INGOT.get()))
                .save(output, ResourceLocation.parse("mite:mithril_bow"));
    }
}
