package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import io.github.ningmeng123.mite.crafting.EquipmentRecyclingIngredient;
import io.github.ningmeng123.mite.crafting.WorkbenchDismantleRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Serializers needed by the material conversion recipes. @author NingMeng123 */
public final class MaterialRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    private static final DeferredRegister<IngredientType<?>> INGREDIENTS = DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Mite.MOD_ID);
    public static final DeferredHolder<IngredientType<?>, IngredientType<EquipmentRecyclingIngredient>> EQUIPMENT_RECYCLING =
            INGREDIENTS.register("equipment_recycling", () -> new IngredientType<>(EquipmentRecyclingIngredient.CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, BulkMaterialRecipe.Serializer> BULK_MATERIAL =
            SERIALIZERS.register("bulk_material", BulkMaterialRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, WorkbenchDismantleRecipe.Serializer> WORKBENCH_DISMANTLE =
            SERIALIZERS.register("workbench_dismantle", WorkbenchDismantleRecipe.Serializer::new);

    private MaterialRecipeSerializers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
        INGREDIENTS.register(modBus);
    }
}
