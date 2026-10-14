package io.github.ningmeng123.mite.registry;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.loot.RemoveWoodenToolsLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Serializers for data-driven loot filters. @author NingMeng123 */
public final class MiteLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mite.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<RemoveWoodenToolsLootModifier>> REMOVE_WOODEN_TOOLS =
            SERIALIZERS.register("remove_wooden_tools", () -> RemoveWoodenToolsLootModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<io.github.ningmeng123.mite.loot.SkeletonArrowLootModifier>> SKELETON_ARROWS =
            SERIALIZERS.register("skeleton_arrows", () -> io.github.ningmeng123.mite.loot.SkeletonArrowLootModifier.CODEC);

    private MiteLootModifiers() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
