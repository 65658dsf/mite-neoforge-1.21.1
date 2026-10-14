package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.Mite;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Independent R196 underworld; the modern overworld generator is never replaced. @author NingMeng123 */
public final class MiteWorldDimensions {
    public static final ResourceLocation UNDERWORLD_ID = ResourceLocation.fromNamespaceAndPath(Mite.MOD_ID, "underworld");
    public static final ResourceKey<Level> UNDERWORLD = ResourceKey.create(Registries.DIMENSION, UNDERWORLD_ID);
    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Mite.MOD_ID);

    static {
        GENERATORS.register("underworld", () -> R196UnderworldGenerator.CODEC);
    }

    private MiteWorldDimensions() {}
    public static void register(IEventBus bus) {
        GENERATORS.register(bus);
    }
}
