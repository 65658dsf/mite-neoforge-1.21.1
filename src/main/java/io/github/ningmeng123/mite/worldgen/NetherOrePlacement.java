package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

/** Apply ore controls to existing native placed features without replacing their registry identities. @author NingMeng123 */
public final class NetherOrePlacement {
    static final ResourceLocation NETHER_ADAMANTIUM = ResourceLocation.fromNamespaceAndPath("modern_ite", "nether_adamantium_ore");
    static final ResourceLocation RED_DIAMOND = ResourceLocation.fromNamespaceAndPath("modern_ite", "fancy_red_ore");
    private static final Map<ResourceKey<Level>, Map<ResourceLocation, Kind>> NATIVE_ORES = Map.of(
            Level.NETHER, Map.of(ResourceLocation.withDefaultNamespace("ore_ancient_debris_large"), Kind.ANCIENT_DEBRIS,
                    ResourceLocation.withDefaultNamespace("ore_debris_small"), Kind.ANCIENT_DEBRIS,
                    NETHER_ADAMANTIUM, Kind.NETHER_ADAMANTIUM),
            MiteWorldDimensions.UNDERWORLD, Map.of(RED_DIAMOND, Kind.RED_DIAMOND));
    private static final ThreadLocal<Placement> ACTIVE = new ThreadLocal<>();

    private NetherOrePlacement() {}

    public static boolean place(PlacedFeature feature, PlacementContext context, BooleanSupplier original) {
        var level = context.getLevel();
        var ores = NATIVE_ORES.get(level.getLevel().dimension());
        if (ores == null) return original.getAsBoolean();
        var id = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey(feature);
        Kind kind = id == null ? null : ores.get(id);
        if (kind == null) return original.getAsBoolean();

        var settings = settings(MiteConfig.effectiveSnapshot(), kind);
        OreHeightRange height = settings.height();
        if (settings.multiplier() == 0D || settings.chance() == 0D) return false;
        if (!settings.changesCount() && height == null) return original.getAsBoolean();
        HeightRangePlacement heightPlacement = null;
        if (height != null) {
            int lower = Math.max(height.minimum(), level.getMinBuildHeight());
            int upper = Math.min(height.maximum(), level.getMaxBuildHeight() - 1);
            if (lower > upper) return false;
            heightPlacement = HeightRangePlacement.uniform(VerticalAnchor.absolute(lower), VerticalAnchor.absolute(upper));
        }

        var previous = ACTIVE.get();
        ACTIVE.set(new Placement(feature, settings, heightPlacement, R196OreFeature.generationBlock(kind)));
        try {
            return original.getAsBoolean();
        } finally {
            if (previous == null) ACTIVE.remove();
            else ACTIVE.set(previous);
        }
    }

    private static Settings settings(MiteConfig.Snapshot snapshot, Kind kind) {
        if (kind != Kind.RED_DIAMOND) {
            var nether = NetherOreSettings.read(snapshot, kind);
            return new Settings(nether.multiplier(), 1D, nether.height());
        }
        String key = kind.name();
        double candidates = snapshot.oreCandidates().getOrDefault(key,
                (double)R196OreRules.configuredCandidateCount(kind, Dimension.UNDERWORLD));
        double chance = snapshot.oreChances().getOrDefault(key,
                R196OreRules.configuredCandidateChance(kind, Dimension.UNDERWORLD));
        String height = snapshot.oreHeights().get(key);
        // The red diamond placed feature has one native attempt, so the scaled count is its actual candidate budget.
        return new Settings(candidates * snapshot.ores().getOrDefault(key, 1D), chance,
                height == null ? null : OreHeightRange.parse(height));
    }

    public static List<PlacementModifier> modifiers(PlacedFeature feature, List<PlacementModifier> original) {
        var active = ACTIVE.get();
        if (active == null || active.feature() != feature) return original;
        var settings = active.settings();
        var modified = new ArrayList<PlacementModifier>(original.size() + 1);
        PlacementModifier nativeCount = !settings.changesCount() ? null : original.stream()
                .filter(RepeatingPlacement.class::isInstance).findFirst().orElse(null);
        if (settings.changesCount() && nativeCount == null)
            modified.add(new ScaledCount(CountPlacement.of(1), settings));
        for (var modifier : original) {
            if (modifier == nativeCount) modified.add(new ScaledCount(modifier, settings));
            else if (active.heightPlacement() != null && modifier instanceof HeightRangePlacement) modified.add(active.heightPlacement());
            else modified.add(modifier);
        }
        return modified;
    }

    public static boolean allows(BlockState state, BlockPos pos) {
        var active = ACTIVE.get();
        return active == null || active.settings().height() == null || !state.is(active.target())
                || active.settings().height().contains(pos.getY());
    }

    private record Settings(double multiplier, double chance, OreHeightRange height) {
        boolean changesCount() { return multiplier != 1D || chance != 1D; }
    }

    private record Placement(PlacedFeature feature, Settings settings, HeightRangePlacement heightPlacement, Block target) {}

    private static final class ScaledCount extends PlacementModifier {
        private final PlacementModifier nativeCount;
        private final Settings settings;

        private ScaledCount(PlacementModifier nativeCount, Settings settings) {
            this.nativeCount = nativeCount;
            this.settings = settings;
        }

        @Override
        public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
            // The native repeater may take its count from the companion mod's own active configuration.
            int count = (int)nativeCount.getPositions(context, random, origin).count();
            int scaled = OreGenerationRates.scaleCandidates(count, settings.multiplier(), random::nextDouble);
            var attempts = IntStream.range(0, scaled);
            if (settings.chance() != 1D) attempts = attempts.filter(index -> random.nextDouble() < settings.chance());
            return attempts.mapToObj(index -> origin);
        }

        @Override
        public PlacementModifierType<?> type() {
            return nativeCount.type();
        }
    }
}
