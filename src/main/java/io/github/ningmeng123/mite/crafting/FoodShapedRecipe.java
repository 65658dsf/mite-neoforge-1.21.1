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
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Vanilla pattern matching with source food timing and vessel remainders. @author NingMeng123 */
public final class FoodShapedRecipe extends ShapedRecipe implements FoodManufacturingRecipe {
    private final ShapedRecipe original;
    private final Metadata manufacturing;

    public FoodShapedRecipe(ShapedRecipe original, Metadata manufacturing) {
        super(original.getGroup(), original.category(), original.pattern, original.getResultItem(null), original.showNotification());
        this.original = original;
        this.manufacturing = manufacturing;
    }
    @Override public Metadata manufacturing() { return manufacturing; }
    private ShapedRecipe original() { return original; }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return FoodManufacturingRecipe.remainders(input, manufacturing);
    }
    @Override public RecipeSerializer<?> getSerializer() { return FoodRecipeSerializers.SHAPED.get(); }

    public static final class Serializer implements RecipeSerializer<FoodShapedRecipe> {
        private static final MapCodec<FoodShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RecipeSerializer.SHAPED_RECIPE.codec().forGetter(FoodShapedRecipe::original),
                Metadata.CODEC.fieldOf("manufacturing").forGetter(FoodShapedRecipe::manufacturing)
        ).apply(instance, FoodShapedRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FoodShapedRecipe> STREAM_CODEC = new StreamCodec<>() {
            @Override public FoodShapedRecipe decode(RegistryFriendlyByteBuf buffer) {
                return new FoodShapedRecipe(RecipeSerializer.SHAPED_RECIPE.streamCodec().decode(buffer), Metadata.STREAM_CODEC.decode(buffer));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, FoodShapedRecipe recipe) {
                RecipeSerializer.SHAPED_RECIPE.streamCodec().encode(buffer, recipe.original);
                Metadata.STREAM_CODEC.encode(buffer, recipe.manufacturing);
            }
        };
        @Override public MapCodec<FoodShapedRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, FoodShapedRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
