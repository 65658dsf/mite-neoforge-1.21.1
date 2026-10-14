package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.StackingRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Native shapeless matching with a complete manufacturing batch as its result. @author NingMeng123 */
public final class StackBatchShapelessRecipe extends ShapelessRecipe {
    private final ItemStack batchResult;

    public StackBatchShapelessRecipe(String group, CraftingBookCategory category, ItemStack result,
                                    NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
        this.batchResult = result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return StackingRecipeSerializers.SHAPELESS.get();
    }

    private static int maximumIngredients() {
        return ShapedRecipePattern.getMaxWidth() * ShapedRecipePattern.getMaxHeight();
    }

    public static final class Serializer implements RecipeSerializer<StackBatchShapelessRecipe> {
        // Preserve all normal ingredient validation, while allowing results larger than a single stack.
        private static final MapCodec<StackBatchShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(StackBatchShapelessRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(StackBatchShapelessRecipe::category),
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.batchResult),
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(ingredients -> {
                    if (ingredients.isEmpty()) return DataResult.error(() -> "No ingredients for shapeless recipe");
                    if (ingredients.size() > maximumIngredients())
                        return DataResult.error(() -> "Too many ingredients for shapeless recipe; maximum is " + maximumIngredients());
                    return DataResult.success(NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new)));
                }, DataResult::success).forGetter(StackBatchShapelessRecipe::getIngredients)
        ).apply(instance, StackBatchShapelessRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, StackBatchShapelessRecipe> STREAM = StreamCodec.of(
                (buffer, recipe) -> {
                    buffer.writeUtf(recipe.getGroup());
                    buffer.writeEnum(recipe.category());
                    buffer.writeVarInt(recipe.getIngredients().size());
                    for (Ingredient ingredient : recipe.getIngredients()) Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.batchResult);
                }, buffer -> {
                    String group = buffer.readUtf();
                    CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
                    int size = buffer.readVarInt();
                    if (size < 1 || size > maximumIngredients()) throw new IllegalArgumentException("Invalid shapeless ingredient count " + size);
                    NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
                    ingredients.replaceAll(ignored -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                    return new StackBatchShapelessRecipe(group, category, result, ingredients);
                });

        @Override
        public MapCodec<StackBatchShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StackBatchShapelessRecipe> streamCodec() {
            return STREAM;
        }
    }
}
