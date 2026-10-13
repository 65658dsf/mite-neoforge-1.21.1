package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.crafting.FoodShapedRecipe;
import io.github.ningmeng123.mite.crafting.FoodShapelessRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Food manufacturing serializers, distinct from legacy default recipes. @author NingMeng123 */
public final class FoodRecipeSerializers {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mite.MOD_ID);
    public static final DeferredHolder<RecipeSerializer<?>, FoodShapedRecipe.Serializer> SHAPED =
            SERIALIZERS.register("food_shaped", FoodShapedRecipe.Serializer::new);
    public static final DeferredHolder<RecipeSerializer<?>, FoodShapelessRecipe.Serializer> SHAPELESS =
            SERIALIZERS.register("food_shapeless", FoodShapelessRecipe.Serializer::new);
    private FoodRecipeSerializers() {}
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
