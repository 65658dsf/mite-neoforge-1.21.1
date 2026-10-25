package io.github.ningmeng123.mite.registry;

import io.github.ningmeng123.mite.Mite;
import io.github.ningmeng123.mite.worldgen.NetherBoundaryFeature;
import io.github.ningmeng123.mite.worldgen.R196OreFeature;
import io.github.ningmeng123.mite.worldgen.SurfaceCaveConnectionFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World preparation features, independent of the ore phase. @author NingMeng123 */
public final class MiteWorldFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Mite.MOD_ID);
    public static final DeferredHolder<Feature<?>, NetherBoundaryFeature> NETHER_BOUNDARY = FEATURES.register("nether_boundary",
            () -> new NetherBoundaryFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, R196OreFeature> R196_ORES = FEATURES.register("r196_ores", R196OreFeature::new);
    public static final DeferredHolder<Feature<?>, SurfaceCaveConnectionFeature> SURFACE_CAVE_CONNECTIONS = FEATURES.register(
            "surface_cave_connections", SurfaceCaveConnectionFeature::new);
    private MiteWorldFeatures() {}
    public static void register(IEventBus bus) { FEATURES.register(bus); }
}
