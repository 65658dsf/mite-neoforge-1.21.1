package io.github.ningmeng123.mite.crafting;

import io.github.ningmeng123.mite.registry.FoodRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteFoods;
import java.util.ArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SuspiciousStewRecipe;
import net.minecraft.world.level.Level;

/** Water-bowl manufacture retaining vanilla flower matching and effects. @author NingMeng123 */
public final class WaterBowlSuspiciousStewRecipe extends CustomRecipe implements FoodManufacturingRecipe {
    private static final Metadata MANUFACTURING = new Metadata(25F, "", 0, true, false);
    private final SuspiciousStewRecipe vanilla;

    public WaterBowlSuspiciousStewRecipe(CraftingBookCategory category) {
        super(category);
        vanilla = new SuspiciousStewRecipe(category);
    }

    @Override public Metadata manufacturing() { return MANUFACTURING; }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        var converted = new ArrayList<ItemStack>(input.size());
        boolean hasWaterBowl = false;
        for (ItemStack stack : input.items()) {
            if (stack.is(Items.BOWL)) return false;
            if (stack.is(MiteFoods.WATER_BOWL.get())) {
                converted.add(new ItemStack(Items.BOWL));
                hasWaterBowl = true;
            } else {
                converted.add(stack);
            }
        }
        return hasWaterBowl && vanilla.matches(CraftingInput.of(input.width(), input.height(), converted), level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return vanilla.assemble(input, registries);
    }

    @Override public boolean canCraftInDimensions(int width, int height) {
        return vanilla.canCraftInDimensions(width, height);
    }

    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return FoodManufacturingRecipe.remainders(input, MANUFACTURING);
    }

    @Override public RecipeSerializer<?> getSerializer() { return FoodRecipeSerializers.WATER_BOWL_STEW.get(); }
}
