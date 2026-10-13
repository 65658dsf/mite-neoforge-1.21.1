package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import io.github.ningmeng123.mite.registry.MiteTools;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Primitive workbenches return both their knife and the original wooden body. @author NingMeng123 */
public final class WorkbenchDismantleRecipe extends ShapelessRecipe {
    private final Ingredient ingredient;
    private final ItemStack result;

    public WorkbenchDismantleRecipe(Ingredient ingredient, ItemStack result) {
        super("", CraftingBookCategory.MISC, result, NonNullList.of(Ingredient.EMPTY, ingredient));
        if (!validIngredient(ingredient) || !validResult(result)
                || ingredient.getItems()[0].is(MiteTools.get("flint_crafting_table")) != result.is(MiteTools.get("flint_knife")))
            throw new IllegalArgumentException("Invalid primitive-workbench dismantling recipe");
        this.ingredient = ingredient;
        this.result = result;
    }

    private static boolean validIngredient(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 1 && (items[0].is(MiteTools.get("flint_crafting_table"))
                || items[0].is(MiteTools.get("obsidian_crafting_table")));
    }

    private static boolean validResult(ItemStack result) {
        return result.getCount() == 1 && (result.is(MiteTools.get("flint_knife")) || result.is(MiteTools.get("obsidian_knife")));
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            if (ingredient.test(input.getItem(slot))) {
                // Modern workbenches deliberately unify the old four wood variants to oak.
                remaining.set(slot, new ItemStack(Items.OAK_LOG));
                break;
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return MaterialRecipeSerializers.WORKBENCH_DISMANTLE.get(); }

    public static final class Serializer implements RecipeSerializer<WorkbenchDismantleRecipe> {
        private static final Codec<Ingredient> INPUT = Ingredient.CODEC_NONEMPTY.validate(value -> validIngredient(value)
                ? DataResult.success(value) : DataResult.error(() -> "Only one flint or obsidian workbench is a dismantling ingredient"));
        private static final Codec<ItemStack> OUTPUT = ItemStack.CODEC.validate(value -> validResult(value)
                ? DataResult.success(value) : DataResult.error(() -> "Dismantling returns exactly one primitive knife"));
        private static final MapCodec<WorkbenchDismantleRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                INPUT.fieldOf("ingredient").forGetter(recipe -> recipe.ingredient),
                OUTPUT.fieldOf("result").forGetter(recipe -> recipe.result)
        ).apply(instance, WorkbenchDismantleRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, WorkbenchDismantleRecipe> STREAM_CODEC = StreamCodec.of(
                (buffer, recipe) -> {
                    Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.ingredient);
                    ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
                }, buffer -> new WorkbenchDismantleRecipe(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer), ItemStack.STREAM_CODEC.decode(buffer)));
        @Override public MapCodec<WorkbenchDismantleRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, WorkbenchDismantleRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
