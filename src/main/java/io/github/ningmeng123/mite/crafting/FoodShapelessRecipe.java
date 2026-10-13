package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.FoodRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** Expanded per-slot R196 ingredients retain vanilla shapeless matching and JEI support. @author NingMeng123 */
public final class FoodShapelessRecipe extends ShapelessRecipe implements FoodManufacturingRecipe {
    private final ShapelessRecipe original;
    private final Metadata manufacturing;

    public FoodShapelessRecipe(ShapelessRecipe original, Metadata manufacturing) {
        super(original.getGroup(), original.category(), original.getResultItem(null), original.getIngredients());
        this.original = original;
        this.manufacturing = manufacturing;
    }
    @Override public Metadata manufacturing() { return manufacturing; }
    private ShapelessRecipe original() { return original; }
    @Override public boolean matches(CraftingInput input, Level level) {
        if (manufacturing.anyMilkBuckets() && input.ingredientCount() == getIngredients().size()
                && input.items().stream().allMatch(stack -> stack.isEmpty() || FoodManufacturingRecipe.isSourceMilkBucket(stack)))
            return true;
        return super.matches(input, level);
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return FoodManufacturingRecipe.remainders(input, manufacturing);
    }
    @Override public RecipeSerializer<?> getSerializer() { return FoodRecipeSerializers.SHAPELESS.get(); }

    public static final class Serializer implements RecipeSerializer<FoodShapelessRecipe> {
        private static final MapCodec<FoodShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RecipeSerializer.SHAPELESS_RECIPE.codec().forGetter(FoodShapelessRecipe::original),
                Metadata.CODEC.fieldOf("manufacturing").forGetter(FoodShapelessRecipe::manufacturing)
        ).apply(instance, FoodShapelessRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FoodShapelessRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override public FoodShapelessRecipe decode(RegistryFriendlyByteBuf buffer) {
                return new FoodShapelessRecipe(RecipeSerializer.SHAPELESS_RECIPE.streamCodec().decode(buffer), Metadata.STREAM_CODEC.decode(buffer));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, FoodShapelessRecipe recipe) {
                RecipeSerializer.SHAPELESS_RECIPE.streamCodec().encode(buffer, recipe.original);
                Metadata.STREAM_CODEC.encode(buffer, recipe.manufacturing);
            }
        };
        @Override public MapCodec<FoodShapelessRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, FoodShapelessRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
