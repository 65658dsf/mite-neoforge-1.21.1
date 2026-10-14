package io.github.ningmeng123.mite.registry;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.loot.ArmorStructureLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Separate optional data-driven source-gear pool serializer. @author NingMeng123 */
public final class ArmorLootIntegration {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Mite.MOD_ID);
    static { SERIALIZERS.register("armor_structure_loot", () -> ArmorStructureLootModifier.CODEC); }
    private ArmorLootIntegration() {}
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
