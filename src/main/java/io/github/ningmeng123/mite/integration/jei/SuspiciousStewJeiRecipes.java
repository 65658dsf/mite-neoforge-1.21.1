package io.github.ningmeng123.mite.integration.jei;

import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import io.github.ningmeng123.mite.crafting.WaterBowlSuspiciousStewRecipe;
import io.github.ningmeng123.mite.registry.MiteFoods;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** Flower variants assembled by the real dynamic recipe, without linking common code to JEI. @author NingMeng123 */
public final class SuspiciousStewJeiRecipes {
    private static final String DISPLAY_PREFIX = "jei_suspicious_stew/";

    private SuspiciousStewJeiRecipes() {}

    /** Display wrappers are never inserted into the world's recipe manager or used for manufacture. */
    public static List<RecipeHolder<CraftingRecipe>> variants(RecipeHolder<CraftingRecipe> source, Level level) {
        if (!(source.value() instanceof WaterBowlSuspiciousStewRecipe recipe)) return List.of();
        List<RecipeHolder<CraftingRecipe>> result = new ArrayList<>();
        for (var flower : BuiltInRegistries.ITEM.stream()
                .filter(item -> new ItemStack(item).is(ItemTags.SMALL_FLOWERS))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList()) {
            CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(Items.BROWN_MUSHROOM),
                    new ItemStack(Items.RED_MUSHROOM), new ItemStack(MiteFoods.WATER_BOWL.get()), new ItemStack(flower)));
            if (!recipe.matches(input, level)) continue;
            // This preserves modded SuspiciousEffectHolder effects and any valid flower with no effects.
            ItemStack output = recipe.assemble(input, level.registryAccess());
            if (output.isEmpty()) continue;
            var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.BROWN_MUSHROOM),
                    Ingredient.of(Items.RED_MUSHROOM), Ingredient.of(MiteFoods.WATER_BOWL.get()), Ingredient.of(flower));
            var display = new FoodShapelessRecipe(new ShapelessRecipe("", recipe.category(), output, ingredients),
                    recipe.manufacturing());
            ResourceLocation flowerId = BuiltInRegistries.ITEM.getKey(flower);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mite", DISPLAY_PREFIX
                    + source.id().getNamespace() + "/" + source.id().getPath() + "/"
                    + flowerId.getNamespace() + "/" + flowerId.getPath());
            result.add(new RecipeHolder<>(id, display));
        }
        return List.copyOf(result);
    }

    public static boolean isDisplayVariant(ResourceLocation id) {
        return id.getNamespace().equals("mite") && id.getPath().startsWith(DISPLAY_PREFIX);
    }

    /** Only JEI's old synthetic vanilla pages are removed; unrelated recipes retain their own views. */
    public static boolean isObsoleteVanillaPage(RecipeHolder<CraftingRecipe> holder, Level level) {
        if (!holder.id().getNamespace().equals("minecraft")
                || !holder.id().getPath().startsWith("jei.suspicious.stew.")) return false;
        CraftingRecipe recipe = holder.value();
        return recipe.getResultItem(level.registryAccess()).is(Items.SUSPICIOUS_STEW)
                && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.BOWL)));
    }
}
