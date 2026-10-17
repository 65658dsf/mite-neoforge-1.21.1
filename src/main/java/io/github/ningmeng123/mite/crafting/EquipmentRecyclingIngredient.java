package io.github.ningmeng123.mite.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.furnace.EquipmentRecycling;
import io.github.ningmeng123.mite.registry.MaterialRecipeSerializers;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/** Matches live equipment materials without storing an item whitelist. @author NingMeng123 */
public record EquipmentRecyclingIngredient(String material) implements ICustomIngredient {
    public static final MapCodec<EquipmentRecyclingIngredient> CODEC = Codec.STRING
            .validate(value -> EquipmentRecycling.metals().contains(value) ? DataResult.success(value)
                    : DataResult.error(() -> "Unknown equipment recycling metal: " + value))
            .fieldOf("material").xmap(EquipmentRecyclingIngredient::new, EquipmentRecyclingIngredient::material);

    @Override public boolean test(ItemStack stack) {
        return material.equals(EquipmentRecycling.material(stack));
    }

    @Override public Stream<ItemStack> getItems() {
        // Ingredient owns the display cache and rebuilds it when recipes/tags reload.
        return BuiltInRegistries.ITEM.stream().map(item -> item.getDefaultInstance()).filter(this::test);
    }

    @Override public boolean isSimple() {
        // Keep the predicate on network sync instead of flattening it into an item list.
        return false;
    }

    @Override public IngredientType<?> getType() {
        return MaterialRecipeSerializers.EQUIPMENT_RECYCLING.get();
    }
}
