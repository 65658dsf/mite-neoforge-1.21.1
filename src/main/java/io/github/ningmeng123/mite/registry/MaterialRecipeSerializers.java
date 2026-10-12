package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.BulkMaterialRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Serializers needed by the material conversion recipes. @author NingMeng123 */
public final class MaterialRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, BulkMaterialRecipe.Serializer> BULK_MATERIAL =
            SERIALIZERS.register("bulk_material", BulkMaterialRecipe.Serializer::new);

    private MaterialRecipeSerializers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
