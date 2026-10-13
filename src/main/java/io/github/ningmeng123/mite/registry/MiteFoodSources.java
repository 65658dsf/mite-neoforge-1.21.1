package io.github.ningmeng123.mite.registry;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.loot.NaturalFoodLootModifier;
import io.github.ningmeng123.mite.worldgen.BlueberryPatchFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Independent serializers/features for verified food acquisition. @author NingMeng123 */
public final class MiteFoodSources {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mite.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Mite.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<NaturalFoodLootModifier>> NATURAL_FOOD =
            LOOT.register("natural_food_sources", () -> NaturalFoodLootModifier.CODEC);
    public static final DeferredHolder<Feature<?>, BlueberryPatchFeature> BLUEBERRY_PATCH =
            FEATURES.register("blueberry_patch", BlueberryPatchFeature::new);
    private MiteFoodSources() {}
    public static void register(IEventBus bus) { LOOT.register(bus); FEATURES.register(bus); }
}
