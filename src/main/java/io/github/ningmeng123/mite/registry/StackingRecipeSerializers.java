package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.StackBatchShapedRecipe;
import io.github.ningmeng123.mite.crafting.StackBatchShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Keeps native recipe batches intact after R196 stack limits are applied. @author NingMeng123 */
public final class StackingRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, StackBatchShapedRecipe.Serializer> SHAPED =
            SERIALIZERS.register("stacking_shaped", StackBatchShapedRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, StackBatchShapelessRecipe.Serializer> SHAPELESS =
            SERIALIZERS.register("stacking_shapeless", StackBatchShapelessRecipe.Serializer::new);

    private StackingRecipeSerializers() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
