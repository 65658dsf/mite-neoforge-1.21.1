package io.github.ningmeng123.mite.furnace;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Access to the actual native furnace state, never a parallel inventory. @author NingMeng123 */
public interface FurnaceThermalAccess {
    int mite$getHeat();
    void mite$setHeat(int value);
    int mite$getBurn();
    void mite$setBurn(int value);
    int mite$getDuration();
    void mite$setDuration(int value);
    int mite$getProgress();
    void mite$setProgress(int value);
    void mite$setTotal(int value);
    RecipeType<? extends AbstractCookingRecipe> mite$recipeType();
    RecipeHolder<? extends AbstractCookingRecipe> mite$recipe(Level level, ItemStack input);
}
