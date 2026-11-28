package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.IntToDoubleFunction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Estimates ore density from the effective vein settings and host rock in each dimension. @author NingMeng123 */
public final class OreGenerationDisplay {
    private OreGenerationDisplay() {}

    private static final class Catalog {
        private static final List<Entry> ENTRIES = buildEntries();
    }

    public static List<Entry> entries() { return Catalog.ENTRIES; }

    private static List<Entry> buildEntries() {
        var entries = new ArrayList<Entry>();
        for (Dimension dimension : Dimension.values()) {
            var kinds = new LinkedHashSet<>(R196OreRules.generationKinds(dimension));
            kinds.addAll(R196OreRules.configurableKinds(dimension));
            for (Kind kind : kinds) {
                // The additional surface infestation pass depends on the actual chunk heightmap.
                if (kind == Kind.DIRT || kind == Kind.INFESTED && dimension == Dimension.OVERWORLD) continue;
                Block block = R196OreFeature.generationBlock(kind);
                if (block == Blocks.AIR) continue; // Optional Modern ITE block absent.
                var entry = new Entry(dimension, kind, false, block);
                entries.add(entry);
                Block deep = R196OreFeature.generationBlock(kind, true);
                if (deep != block && deep != Blocks.AIR) entries.add(new Entry(dimension, kind, true, deep));
            }
        }
        return List.copyOf(entries);
    }

    public static final class Entry {
        private final Dimension dimension;
        private final Kind kind;
        private final boolean deepslate;
        private final Block block;
        private MiteConfig.Snapshot lastConfig;
        private Map<?, ?> lastIte;
        private int lastMinY, lastMaxY;
        private Settings lastSettings;
        private IntToDoubleFunction sourceDistribution;
        private OreBlockDensity.Shape lastShape;
        private IntToDoubleFunction densityPerAttempt;

        private Entry(Dimension dimension, Kind kind, boolean deepslate, Block block) {
            this.dimension = dimension;
            this.kind = kind;
            this.deepslate = deepslate;
            this.block = block;
        }

        public Dimension dimension() { return dimension; }
        public Kind kind() { return kind; }
        public Block block() { return block; }

        public Settings settings() {
            return dimension == Dimension.NETHER ? settings(0, 256, false) : settings(-64, 320, false);
        }

        /** A remote client must wait for server-owned controls, including older servers without Nether sync. */
        public Settings settings(int minBuildY, int maxBuildYExclusive, boolean remoteServer) {
            var config = MiteConfig.effectiveSnapshot();
            if (remoteServer && (config == MiteConfig.localSnapshot()
                    || dimension == Dimension.NETHER && config.netherOres().isEmpty())) return null;
            var ite = kind == Kind.NETHER_ADAMANTIUM ? IteSettings.snapshot(remoteServer) : null;
            if (lastSettings == null || lastConfig != config || lastIte != ite ||
                    lastMinY != minBuildY || lastMaxY != maxBuildYExclusive) {
                lastSettings = readSettings(config, ite, minBuildY, maxBuildYExclusive);
                lastConfig = config;
                lastIte = ite;
                lastMinY = minBuildY;
                lastMaxY = maxBuildYExclusive;
            }
            return lastSettings;
        }

        private Settings readSettings(MiteConfig.Snapshot config, Map<?, ?> ite, int minY, int maxY) {
            boolean configurable = R196OreRules.configurableKinds(dimension).contains(kind);
            boolean deepOverride = deepslate && R196OreRules.hasDeepslateOre(kind);
            double candidates = R196OreRules.configuredCandidateCount(kind, dimension);
            double chance = R196OreRules.configuredCandidateChance(kind, dimension);
            int size = R196OreRules.baseSize(kind);
            OreHeightRange override = null;
            if (dimension == Dimension.NETHER) {
                var settings = NetherOreSettings.read(config, kind);
                if (kind == Kind.NETHER_ADAMANTIUM) {
                    // The native ITE repeater uses these synchronized values before MITE scales its count.
                    candidates = ite.get("nether_adamantium_max_count_per_chunk") instanceof Number n ? n.intValue() : Double.NaN;
                    size = ite.get("nether_adamantium_max_count_per_vein") instanceof Number n ? n.intValue() : 0;
                }
                candidates = settings.multiplier() == 0D ? 0D : candidates * settings.multiplier();
                override = settings.height();
            } else if (configurable) {
                candidates = MiteConfig.oreBaseCandidates(kind, dimension) * MiteConfig.oreMultiplier(kind, dimension, deepOverride);
                if (kind == Kind.EMERALD) {
                    // Emeralds scale a uniform 3..8 singleton pass by the editable count relative to its baseline.
                    candidates *= 5.5D / R196OreRules.configuredCandidateCount(kind, dimension);
                }
                chance = MiteConfig.orePassChance(kind, dimension);
                override = MiteConfig.oreHeightOverride(kind, dimension, deepOverride);
            }

            OreHeightRange original = OreHeightRange.defaults(kind, dimension);
            OreHeightRange requested = override == null ? original : override;
            int lower = Math.max(minY, requested.minimum()), upper = Math.min(maxY - 1, requested.maximum());
            if (lower > upper) return new Settings(candidates, chance, y -> 0D);
            var range = new OreHeightRange(lower, upper);
            IntToDoubleFunction origins;
            if (override != null) {
                double weight = 1D / (upper - lower + 1);
                origins = y -> weight;
            } else if (kind == Kind.ANCIENT_DEBRIS) {
                origins = OreGenerationDisplay::debrisWeight;
            } else if (kind == Kind.RED_DIAMOND || dimension == Dimension.NETHER) {
                double weight = 1D / (original.maximum() - original.minimum() + 1);
                origins = y -> weight;
            } else {
                if (sourceDistribution == null) sourceDistribution = sampleOrigins(kind, dimension, original);
                origins = sourceDistribution;
            }
            double attempts = candidates * chance;
            if (!(attempts > 0) || size <= 0) return new Settings(candidates, chance, y -> 0D);
            double shared = 1D;
            if (configurable && R196OreRules.hasDeepslateOre(kind)) {
                double ours = MiteConfig.oreMultiplier(kind, dimension, deepslate);
                shared = Objects.equals(override, MiteConfig.oreHeightOverride(kind, dimension, !deepslate))
                        ? Math.min(ours, MiteConfig.oreMultiplier(kind, dimension, !deepslate)) / ours : 0D;
            }
            var shape = new OreBlockDensity.Shape(dimension, kind, deepslate, minY, maxY, range, override != null, size, shared);
            if (!shape.equals(lastShape)) {
                densityPerAttempt = OreBlockDensity.perAttempt(shape, origins);
                lastShape = shape;
            }
            var perAttempt = densityPerAttempt;
            return new Settings(candidates, chance,
                    y -> Math.min(OreBlockDensity.hostFraction(shape, y), attempts * perAttempt.applyAsDouble(y)));
        }
    }

    public record Settings(double candidates, double chance, IntToDoubleFunction density) {
        /** Estimated fraction of all 256 cells on this layer occupied in eligible host terrain. */
        public double blockDensityAt(int y) { return density.applyAsDouble(y); }
    }

    static IntToDoubleFunction sampleOrigins(Kind kind, Dimension dimension, OreHeightRange range) {
        final int samples = 65536;
        var counts = new int[range.maximum() - range.minimum() + 1];
        var random = new Random(196L);
        for (int i = 0; i < samples; i++) {
            int y = R196OreRules.modernY(dimension, R196OreRules.randomHeight(kind, dimension, random));
            counts[y - range.minimum()]++;
        }
        return y -> (double)counts[y - range.minimum()] / samples;
    }

    // The two native Nether features make one attempt each: uniform 8..119, and trapezoid 8..24.
    static double debrisWeight(int y) {
        double small = y >= 8 && y <= 119 ? 1D / 112 : 0D;
        double large = Math.max(0, 9 - Math.abs(y - 16)) / 81D;
        return (small + large) * .5D;
    }

    /** Deferred until the optional addon's ore is actually displayed. */
    private static final class IteSettings {
        private static final Method GET = findSnapshot("effectiveSnapshot");
        private static final Method LOCAL = findSnapshot("localSnapshot");

        private static Method findSnapshot(String name) {
            try {
                return Class.forName("io.github.ningmeng123.modernite.config.IteConfig").getMethod(name);
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }

        static Map<?, ?> snapshot(boolean remoteServer) {
            try {
                if (GET != null && GET.invoke(null) instanceof Map<?, ?> values
                        && (!remoteServer || LOCAL != null && LOCAL.invoke(null) != values)) return values;
            } catch (ReflectiveOperationException ignored) {
                // An incompatible addon has no trustworthy count to present.
            }
            return Map.of();
        }
    }
}
