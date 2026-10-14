package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ningmeng123.mite.registry.MiteRangedItems;
import io.github.ningmeng123.mite.rules.R196RangedRules.Material;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** aaj's propagate_tag_compound preserves enchantments/custom data, but not old damage. @author NingMeng123 */
public final class RangedRodConversionRecipe extends ShapelessRecipe {
    private final Material material;
    private final boolean attach;
    public RangedRodConversionRecipe(Material material, boolean attach) {
        super("", CraftingBookCategory.MISC, new ItemStack(attach ? MiteRangedItems.carrotRod(material) : MiteRangedItems.rod(material)), ingredients(material, attach));
        if (!material.hasRod()) throw new IllegalArgumentException("No R196 rusted fishing hook");
        this.material = material; this.attach = attach;
    }
    private static NonNullList<Ingredient> ingredients(Material material, boolean attach) {
        return attach ? NonNullList.of(Ingredient.EMPTY, Ingredient.of(MiteRangedItems.rod(material)), Ingredient.of(Items.CARROT))
                : NonNullList.of(Ingredient.EMPTY, Ingredient.of(MiteRangedItems.carrotRod(material)));
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registry) {
        var from = attach ? MiteRangedItems.rod(material) : MiteRangedItems.carrotRod(material);
        var to = attach ? MiteRangedItems.carrotRod(material) : MiteRangedItems.rod(material);
        for (int slot = 0; slot < input.size(); slot++) if (input.getItem(slot).is(from)) {
            ItemStack result = input.getItem(slot).transmuteCopy(to, 1);
            result.set(DataComponents.MAX_DAMAGE, attach ? 25 : material.rodDurability());
            result.setDamageValue(0);
            return result;
        }
        return ItemStack.EMPTY;
    }
    @Override public RecipeSerializer<?> getSerializer() { return MiteRangedItems.ROD_CONVERSION.get(); }
    public static final class Serializer implements RecipeSerializer<RangedRodConversionRecipe> {
        private static final MapCodec<RangedRodConversionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.fieldOf("material").forGetter(recipe -> recipe.material.id),
                com.mojang.serialization.Codec.BOOL.fieldOf("attach_carrot").forGetter(recipe -> recipe.attach)
        ).apply(instance, (material, attach) -> new RangedRodConversionRecipe(java.util.Objects.requireNonNull(Material.byId(material)), attach)));
        private static final StreamCodec<RegistryFriendlyByteBuf, RangedRodConversionRecipe> STREAM = StreamCodec.of(
                (buffer, recipe) -> { buffer.writeUtf(recipe.material.id); buffer.writeBoolean(recipe.attach); },
                buffer -> new RangedRodConversionRecipe(java.util.Objects.requireNonNull(Material.byId(buffer.readUtf())), buffer.readBoolean()));
        @Override public MapCodec<RangedRodConversionRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RangedRodConversionRecipe> streamCodec() { return STREAM; }
    }
}
