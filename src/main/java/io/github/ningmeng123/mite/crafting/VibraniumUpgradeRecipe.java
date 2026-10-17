package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.item.EquipmentDurability;
import io.github.ningmeng123.mite.item.EquipmentQuality;
import io.github.ningmeng123.mite.item.R196ReinforcedBowItem;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Native shaped upgrades preserve personal equipment data while retaining the new material defaults. @author NingMeng123 */
public final class VibraniumUpgradeRecipe extends ShapedRecipe {
    private final ShapedRecipe original;

    public VibraniumUpgradeRecipe(ShapedRecipe original) {
        super(original.getGroup(), original.category(), original.pattern, original.getResultItem(null), original.showNotification());
        this.original = original;
    }

    private ShapedRecipe original() { return original; }

    /** Exactly one equipment ingredient supplies the inherited data. */
    public static ItemStack source(CraftingInput input) {
        ItemStack source = ItemStack.EMPTY;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack candidate = input.getItem(slot);
            if (candidate.isEmpty() || !(EquipmentDurability.toolProfile(candidate) != null
                    || EquipmentDurability.armorProfile(candidate) != null || candidate.is(Items.BOW)
                    || candidate.getItem() instanceof R196ReinforcedBowItem)) continue;
            if (!source.isEmpty()) return ItemStack.EMPTY;
            source = candidate;
        }
        return source;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && !source(input).isEmpty();
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = source(input);
        if (source.isEmpty() || !pattern.matches(input)) return ItemStack.EMPTY;
        ItemStack result = super.assemble(input, registries);
        if (!EquipmentDurability.isManaged(result)) return ItemStack.EMPTY;
        int targetBaseMaximum = result.getOrDefault(DataComponents.MAX_DAMAGE, 0);
        int previousMaximum = source.getMaxDamage();
        int previousDamage = source.getDamageValue();
        if (previousMaximum < 1 || targetBaseMaximum < 1) return ItemStack.EMPTY;
        result.applyComponents(source.getComponentsPatch().forget(type -> type == DataComponents.MAX_DAMAGE
                || type == DataComponents.DAMAGE || type == DataComponents.MAX_STACK_SIZE
                || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.TOOL
                || type == DataComponents.FIRE_RESISTANT));
        EquipmentQuality.preserve(source, result);
        result.set(DataComponents.MAX_DAMAGE, targetBaseMaximum);
        int newMaximum = result.getMaxDamage();
        // A source item at its last usable point remains repairable after upgrading.
        int damage = (int)Math.clamp(Math.round((double)previousDamage * newMaximum / previousMaximum),
                0L, Math.max(newMaximum - 1L, 0L));
        result.setDamageValue(damage);
        return result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return MaterialRecipeSerializers.VIBRANIUM_UPGRADE.get(); }

    public static final class Serializer implements RecipeSerializer<VibraniumUpgradeRecipe> {
        private static final MapCodec<VibraniumUpgradeRecipe> CODEC = RecipeSerializer.SHAPED_RECIPE.codec()
                .xmap(VibraniumUpgradeRecipe::new, VibraniumUpgradeRecipe::original);
        private static final StreamCodec<RegistryFriendlyByteBuf, VibraniumUpgradeRecipe> STREAM_CODEC =
                RecipeSerializer.SHAPED_RECIPE.streamCodec().map(VibraniumUpgradeRecipe::new, VibraniumUpgradeRecipe::original);
        @Override public MapCodec<VibraniumUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, VibraniumUpgradeRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
