package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MiteUtilityContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/** Exact R196 utility batches; water subtype and single-container dismantling are preserved. @author NingMeng123 */
public final class UtilityShapelessRecipe extends ShapelessRecipe {
    private final ItemStack output;
    private final NonNullList<Ingredient> inputs;
    private final boolean waterOnly;
    private final boolean propagate;
    public UtilityShapelessRecipe(ItemStack output, NonNullList<Ingredient> inputs, boolean waterOnly, boolean propagate) {
        super("", CraftingBookCategory.MISC, output, inputs);
        this.output = output; this.inputs = inputs; this.waterOnly = waterOnly; this.propagate = propagate;
    }
    public boolean waterPotionOnly() { return waterOnly; }
    @Override public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) return false;
        if (!waterOnly) return true;
        for (ItemStack stack : input.items()) if (stack.is(Items.POTION)
                && !stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) return false;
        return true;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registry) {
        if (propagate) for (ItemStack stack : input.items()) if (!stack.isEmpty()) return stack.transmuteCopy(output.getItem(), output.getCount());
        return output.copy();
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        // wd omits the source bucket remainder because result and remainder are the same vessel class.
        // Propagation is only enabled for stone-bucket -> empty-bucket recipes.
        return propagate ? NonNullList.withSize(input.size(), ItemStack.EMPTY) : super.getRemainingItems(input);
    }
    @Override public RecipeSerializer<?> getSerializer() { return MiteUtilityContent.UTILITY_SHAPELESS.get(); }
    public static final class Serializer implements RecipeSerializer<UtilityShapelessRecipe> {
        private static final MapCodec<UtilityShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStack.CODEC.fieldOf("result").forGetter(recipe -> recipe.output),
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(recipe -> recipe.inputs),
                Codec.BOOL.optionalFieldOf("water_potion_only", false).forGetter(recipe -> recipe.waterOnly),
                Codec.BOOL.optionalFieldOf("propagate_components", false).forGetter(recipe -> recipe.propagate)
        ).apply(instance, (output, inputs, water, propagate) -> {
            if (inputs.isEmpty() || inputs.size() > 9) throw new IllegalArgumentException("Invalid utility ingredient count");
            NonNullList<Ingredient> ingredients = NonNullList.create(); ingredients.addAll(inputs);
            return new UtilityShapelessRecipe(output, ingredients, water, propagate);
        }));
        private static final StreamCodec<RegistryFriendlyByteBuf, UtilityShapelessRecipe> STREAM = StreamCodec.of((buffer, recipe) -> {
            ItemStack.STREAM_CODEC.encode(buffer, recipe.output); buffer.writeVarInt(recipe.inputs.size());
            recipe.inputs.forEach(ingredient -> Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient));
            buffer.writeBoolean(recipe.waterOnly); buffer.writeBoolean(recipe.propagate);
        }, buffer -> {
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer); int size = buffer.readVarInt();
            if (size < 1 || size > 9) throw new IllegalArgumentException("Invalid utility ingredient count");
            NonNullList<Ingredient> inputs = NonNullList.create();
            for (int index = 0; index < size; index++) inputs.add(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
            return new UtilityShapelessRecipe(result, inputs, buffer.readBoolean(), buffer.readBoolean());
        });
        @Override public MapCodec<UtilityShapelessRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, UtilityShapelessRecipe> streamCodec() { return STREAM; }
    }
}
