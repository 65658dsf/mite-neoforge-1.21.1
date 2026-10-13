package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.LeatherMendingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Serializers for quality-preserving R196 equipment operations. @author NingMeng123 */
public final class QualityRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<LeatherMendingRecipe>> LEATHER_MENDING =
            SERIALIZERS.register("leather_mending", () -> new SimpleCraftingRecipeSerializer<>(LeatherMendingRecipe::new));

    private QualityRecipeSerializers() {}

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
