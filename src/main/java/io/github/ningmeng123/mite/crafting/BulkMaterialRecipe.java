package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteMaterials;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** A single material block yields nine ingots while each inventory stack holds eight. @author NingMeng123 */
public final class BulkMaterialRecipe extends ShapelessRecipe {
    private final String materialGroup;
    private final Ingredient ingredient;
    private final ItemStack result;

    public BulkMaterialRecipe(String group, Ingredient ingredient, ItemStack result) {
        super(group, CraftingBookCategory.MISC, result, NonNullList.of(Ingredient.EMPTY, ingredient));
        this.materialGroup = group;
        this.ingredient = ingredient;
        this.result = result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MaterialRecipeSerializers.BULK_MATERIAL.get();
    }

    /** This exception is deliberately limited to a batch of nine of the seven R196 ingots. */
    public static boolean isValidResult(ItemStack stack) {
        return stack.getCount() == 9 && stack.getMaxStackSize() == 8
                && (stack.is(Items.COPPER_INGOT) || stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT)
                || stack.is(MiteMaterials.SILVER_INGOT.get()) || stack.is(MiteMaterials.ANCIENT_METAL_INGOT.get())
                || stack.is(MiteMaterials.MITHRIL_INGOT.get()) || stack.is(MiteMaterials.ADAMANTIUM_INGOT.get()));
    }

    public static final class Serializer implements RecipeSerializer<BulkMaterialRecipe> {
        private static final Codec<ItemStack> RESULT_CODEC = ItemStack.CODEC.validate(stack -> isValidResult(stack)
                ? DataResult.success(stack)
                : DataResult.error(() -> "Bulk material result must be exactly nine R196 ingots with stack limit eight"));
        private static final MapCodec<BulkMaterialRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.materialGroup),
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(recipe -> recipe.ingredient),
                RESULT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, BulkMaterialRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, BulkMaterialRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) -> {
                    buffer.writeUtf(recipe.materialGroup);
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ingredient);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                }, buffer -> {
                    String group = buffer.readUtf();
                    Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
                    if (!isValidResult(result)) throw new IllegalArgumentException("Invalid bulk material result received");
                    return new BulkMaterialRecipe(group, ingredient, result);
                });

        @Override
        public MapCodec<BulkMaterialRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BulkMaterialRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
