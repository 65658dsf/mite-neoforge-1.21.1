package io.github.ningmeng123.mite.phantom;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MitePhantomBlades;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Ring recipes inherit only the central formation core's personal data, never the eight sacrificed swords. @author NingMeng123 */
public final class PhantomBladeRecipe extends ShapedRecipe {
    private final ShapedRecipe original;
    public PhantomBladeRecipe(ShapedRecipe original) {
        super(original.getGroup(), original.category(), original.pattern, original.getResultItem(null), original.showNotification());
        this.original = original;
    }
    private ShapedRecipe original() { return original; }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        if (!pattern.matches(input)) return ItemStack.EMPTY;
        ItemStack result = super.assemble(input, registries);
        ItemStack center = input.width()==3 && input.height()==3 ? input.getItem(4) : ItemStack.EMPTY;
        if (center.getItem() instanceof PhantomBladeItem) {
            result.applyComponents(center.getComponentsPatch().forget(type -> type == DataComponents.ATTRIBUTE_MODIFIERS
                    || type == DataComponents.MAX_STACK_SIZE || type == DataComponents.MAX_DAMAGE || type == DataComponents.DAMAGE
                    || type == DataComponents.FIRE_RESISTANT || type == DataComponents.RARITY || type == DataComponents.ITEM_NAME));
        }
        return result;
    }
    @Override public RecipeSerializer<?> getSerializer() { return MitePhantomBlades.CRAFTING.get(); }
    public static final class Serializer implements RecipeSerializer<PhantomBladeRecipe> {
        private static final MapCodec<PhantomBladeRecipe> CODEC = RecipeSerializer.SHAPED_RECIPE.codec()
                .xmap(PhantomBladeRecipe::new, PhantomBladeRecipe::original);
        private static final StreamCodec<RegistryFriendlyByteBuf, PhantomBladeRecipe> STREAM_CODEC = RecipeSerializer.SHAPED_RECIPE.streamCodec()
                .map(PhantomBladeRecipe::new, PhantomBladeRecipe::original);
        @Override public MapCodec<PhantomBladeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, PhantomBladeRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
