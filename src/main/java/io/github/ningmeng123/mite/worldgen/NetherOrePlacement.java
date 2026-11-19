package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;

/** Apply the Nether controls to the existing vanilla and Modern ITE placed features. @author NingMeng123 */
public final class NetherOrePlacement {
    static final ResourceLocation NETHER_ADAMANTIUM = ResourceLocation.fromNamespaceAndPath("modern_ite", "nether_adamantium_ore");
    private static final Map<ResourceLocation, Kind> NATIVE_ORES = Map.of(
            ResourceLocation.withDefaultNamespace("ore_ancient_debris_large"), Kind.ANCIENT_DEBRIS,
            ResourceLocation.withDefaultNamespace("ore_debris_small"), Kind.ANCIENT_DEBRIS,
            NETHER_ADAMANTIUM, Kind.NETHER_ADAMANTIUM);
    private static final ThreadLocal<Placement> ACTIVE = new ThreadLocal<>();

    private NetherOrePlacement() {}

    public static boolean place(PlacedFeature feature, PlacementContext context, BooleanSupplier original) {
        var level = context.getLevel();
        if (!level.getLevel().dimension().equals(Level.NETHER)) return original.getAsBoolean();
        var id = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(feature);
        Kind kind = id == null ? null : NATIVE_ORES.get(id);
        if (kind == null) return original.getAsBoolean();

        var settings = NetherOreSettings.read(MiteConfig.effectiveSnapshot(), kind);
        double multiplier = settings.multiplier();
        OreHeightRange height = settings.height();
        if (multiplier == 0D) return false;
        if (multiplier == 1D && height == null) return original.getAsBoolean();
        HeightRangePlacement heightPlacement = null;
        if (height != null) {
            int lower = Math.max(height.minimum(), level.getMinBuildHeight());
            int upper = Math.min(height.maximum(), level.getMaxBuildHeight() - 1);
            if (lower > upper) return false;
            heightPlacement = HeightRangePlacement.uniform(VerticalAnchor.absolute(lower), VerticalAnchor.absolute(upper));
        }

        var previous = ACTIVE.get();
        ACTIVE.set(new Placement(feature, multiplier, heightPlacement, height, R196OreFeature.generationBlock(kind)));
        try {
            return original.getAsBoolean();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    public static List<PlacementModifier> modifiers(PlacedFeature feature, List<PlacementModifier> original) {
        var active = ACTIVE.get();
        if (active == null || active.feature() != feature) return original;
        var modified = new ArrayList<PlacementModifier>(original.size() + 1);
        PlacementModifier nativeCount = active.multiplier() == 1D ? null : original.stream()
                .filter(RepeatingPlacement.class::isInstance).findFirst().orElse(null);
        if (active.multiplier() != 1D && nativeCount == null)
            modified.add(new ScaledCount(CountPlacement.of(1), active.multiplier()));
        for (var modifier : original) {
            if (modifier == nativeCount) modified.add(new ScaledCount(modifier, active.multiplier()));
            else if (active.heightPlacement() != null && modifier instanceof HeightRangePlacement) modified.add(active.heightPlacement());
            else modified.add(modifier);
        }
        return modified;
    }

    public static boolean allows(BlockState state, BlockPos pos) {
        var active = ACTIVE.get();
        return active == null || active.height() == null || !state.is(active.target()) || active.height().contains(pos.getY());
    }

    private record Placement(PlacedFeature feature, double multiplier, HeightRangePlacement heightPlacement,
            OreHeightRange height, Block target) {}

    private static final class ScaledCount extends PlacementModifier {
        private final PlacementModifier nativeCount;
        private final double multiplier;

        private ScaledCount(PlacementModifier nativeCount, double multiplier) {
            this.nativeCount = nativeCount;
            this.multiplier = multiplier;
        }

        @Override
        public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
            // The native repeater may take its count from the companion mod's own active configuration.
            int count = (int)nativeCount.getPositions(context, random, origin).count();
            int scaled = OreGenerationRates.scaleCandidates(count, multiplier, random::nextDouble);
            return IntStream.range(0, scaled).mapToObj(index -> origin);
        }

        @Override
        public PlacementModifierType<?> type() {
            return nativeCount.type();
        }
    }
}
