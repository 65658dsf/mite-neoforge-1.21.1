package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.StackingRecipeSerializers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/** Native shaped matching with a complete manufacturing batch as its result. @author NingMeng123 */
public final class StackBatchShapedRecipe extends ShapedRecipe {
    private final ItemStack batchResult;

    public StackBatchShapedRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                                  ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.batchResult = result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StackingRecipeSerializers.SHAPED.get();
    }

    public static final class Serializer implements RecipeSerializer<StackBatchShapedRecipe> {
        // A recipe batch is not an inventory stack. Inventory insertion splits the complete result.
        private static final MapCodec<StackBatchShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(StackBatchShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(StackBatchShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.batchResult),
                Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(StackBatchShapedRecipe::showNotification)
        ).apply(instance, StackBatchShapedRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, StackBatchShapedRecipe> STREAM = StreamCodec.of(
                (buffer, recipe) -> {
                    buffer.writeUtf(recipe.getGroup());
                    buffer.writeEnum(recipe.category());
                    ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.batchResult);
                    buffer.writeBoolean(recipe.showNotification());
                }, buffer -> {
                    String group = buffer.readUtf();
                    CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
                    ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                    return new StackBatchShapedRecipe(group, category, pattern, result, buffer.readBoolean());
                });

        @Override
        public MapCodec<StackBatchShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StackBatchShapedRecipe> streamCodec() {
            return STREAM;
        }
    }
}
