package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.crafting.SmithingRecipeIngredients;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SmithingTransformRecipe.class)
public interface SmithingRecipeIngredientsMixin extends SmithingRecipeIngredients {
    @Override @Accessor("template") Ingredient mite$templateIngredient();
    @Override @Accessor("base") Ingredient mite$baseIngredient();
    @Override @Accessor("addition") Ingredient mite$additionIngredient();
}
