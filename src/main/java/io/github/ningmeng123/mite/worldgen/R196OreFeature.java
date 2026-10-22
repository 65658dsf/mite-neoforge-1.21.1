package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.R196OreRules;
import io.github.ningmeng123.mite.rules.R196OreRules.Dimension;
import io.github.ningmeng123.mite.rules.R196OreRules.Kind;
import io.github.ningmeng123.mite.rules.OreHeightRange;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** R196 candidates and six-neighbour veins inside the modern FEATURES lifecycle. @author NingMeng123 */
public final class R196OreFeature extends Feature<OreFeatureConfiguration> {
    public static final TagKey<Biome> EMERALD_BIOMES = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("mite", "has_emerald_ore"));
    public R196OreFeature() { super(OreFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<OreFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        Dimension dimension = context.config().dimension();
        if (!matches(level, dimension)) return false;
        // Modern worldgen supplies the independent feature seed; the inner sequence is Java's 48-bit Random.
        Random random = new Random(context.random().nextLong());
        int originX = Math.floorDiv(context.origin().getX(), 16) * 16;
        int originZ = Math.floorDiv(context.origin().getZ(), 16) * 16;
        List<Kind> order = R196OreRules.generationKinds(dimension);
        int placed = 0;
        for (Kind kind : order) {
            int count = R196OreRules.candidateCount(kind, dimension, random);
            boolean configurable = R196OreRules.configurableKinds(dimension).contains(kind);
            OreHeightRange stoneHeight = configurable ? MiteConfig.oreHeightOverride(kind, dimension, false) : null;
            OreHeightRange deepHeight = configurable && R196OreRules.hasDeepslateOre(kind) ? MiteConfig.oreHeightOverride(kind, dimension, true) : stoneHeight;
            var batches = configurable && R196OreRules.hasDeepslateOre(kind) && !java.util.Objects.equals(stoneHeight, deepHeight)
                    ? List.of(new OreGenerationRates.Batch(MiteConfig.oreCandidates(kind, dimension), true, false),
                            new OreGenerationRates.Batch(MiteConfig.oreBaseCandidates(kind, dimension) * MiteConfig.oreMultiplier(kind, dimension, true), false, true))
                    : configurable && R196OreRules.hasDeepslateOre(kind)
                    ? OreGenerationRates.batches(MiteConfig.oreBaseCandidates(kind, dimension),
                            MiteConfig.oreMultiplier(kind, dimension), MiteConfig.oreMultiplier(kind, dimension, true))
                    : List.of(new OreGenerationRates.Batch(configurable ? MiteConfig.oreCandidates(kind, dimension) : count, true, true));
            for (var batch : batches) {
                count = OreGenerationRates.scaleCandidates(1, batch.candidates(), random);
                Access access = new Access(level, dimension, kind, batch.stone(), batch.deepslate(), stoneHeight, deepHeight);
                for (int candidate = 0; candidate < count; candidate++) {
                    if (configurable) {
                        if (!OreGenerationRates.passesChance(MiteConfig.orePassChance(kind, dimension), dimension, random)) continue;
                    } else if (dimension != Dimension.NETHER && random.nextInt(R196OreRules.candidateRarity(dimension)) != 0) continue;
                    int x = originX + random.nextInt(16);
                    int legacyY = R196OreRules.randomHeight(kind, dimension, random);
                    int z = originZ + random.nextInt(16);
                    int y = R196OreRules.modernY(dimension, legacyY);
                    OreHeightRange height = batch.stone() ? stoneHeight : deepHeight;
                    if (height != null) {
                        Integer sampled = height.sample(random, level.getMinBuildHeight(), level.getMaxBuildHeight());
                        if (sampled == null) continue;
                        y = sampled;
                        legacyY = dimension == Dimension.UNDERWORLD ? Math.floorDiv((y + 64) * 256, 384)
                                : y <= 64 ? Math.floorDiv(y + 64, 2) : y;
                        legacyY = Math.clamp(legacyY, R196OreRules.minHeight(kind, dimension), R196OreRules.maxHeight(kind, dimension));
                    }
                    placed += R196OreRules.generateVein(kind, dimension, access, x, y, z, legacyY,
                            R196OreRules.depthScaled(kind, dimension), random);
                }
            }
        }
        if (dimension == Dimension.OVERWORLD) {
            placed += surfaceInfestation(level, originX, originZ, random);
            Access access = new Access(level, dimension, Kind.EMERALD);
            int count = R196OreRules.candidateCount(Kind.EMERALD, dimension, random);
            for (int attempt = 0; attempt < count; attempt++) {
                int x = originX + random.nextInt(16);
                int y = R196OreRules.modernY(dimension, R196OreRules.randomHeight(Kind.EMERALD, dimension, random));
                int z = originZ + random.nextInt(16);
                if (access.canReplace(x, y, z)) { access.replace(x, y, z); placed++; }
            }
        }
        return placed > 0;
    }
    private static boolean matches(WorldGenLevel level, Dimension dimension) {
        return switch (dimension) {
            case OVERWORLD -> level.getLevel().dimension().equals(Level.OVERWORLD);
            case UNDERWORLD -> level.getLevel().dimension().equals(MiteWorldDimensions.UNDERWORLD);
            case NETHER -> level.getLevel().dimension().equals(Level.NETHER);
        };
    }
    private static int surfaceInfestation(WorldGenLevel level, int originX, int originZ, Random random) {
        int maximum = 64, sum = 0;
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int modern = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, originX + x, originZ + z);
            int legacy = modern <= 64 ? Math.floorDiv(modern + 64, 2) : modern;
            legacy = Math.max(0, Math.min(256, legacy));
            maximum = Math.max(maximum, legacy);
            sum += legacy;
        }
        int base = Math.max((sum - 8192) / 256 / 8, 2);
        int placed = 0;
        Access access = new Access(level, Dimension.OVERWORLD, Kind.INFESTED);
        for (int attempt = 0; attempt < base - 1; attempt++) {
            int x = originX + random.nextInt(16);
            int legacyY = random.nextInt(maximum);
            int z = originZ + random.nextInt(16);
            int y = R196OreRules.modernY(Dimension.OVERWORLD, legacyY);
            if (!access.canReplace(x, y, z)) continue;
            int size = R196OreRules.veinSize(Kind.INFESTED, Dimension.OVERWORLD, legacyY, random, false, base);
            placed += R196OreRules.growVein(access, size, x, y, z, false, random);
        }
        return placed;
    }

    private static final class Access implements R196OreRules.VeinAccess {
        private final WorldGenLevel level;
        private final Dimension dimension;
        private final Kind kind;
        private final boolean stone, deepslate;
        private final OreHeightRange stoneHeight, deepHeight;
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private Access(WorldGenLevel level, Dimension dimension, Kind kind) {
            this(level, dimension, kind, true, true, null, null);
        }
        private Access(WorldGenLevel level, Dimension dimension, Kind kind, boolean stone, boolean deepslate,
                OreHeightRange stoneHeight, OreHeightRange deepHeight) {
            this.level = level; this.dimension = dimension; this.kind = kind;
            this.stone = stone; this.deepslate = deepslate;
            this.stoneHeight = stoneHeight; this.deepHeight = deepHeight;
        }
        @Override
        public boolean canReplace(int x, int y, int z) {
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.ensureCanWrite(pos)) return false;
            BlockState current = level.getBlockState(pos);
            // Existing ore, boundary blocks, structures, containers, fluids and air never become new ore.
            return dimension == Dimension.NETHER ? current.is(Blocks.NETHERRACK)
                    : stone && current.is(Blocks.STONE) && (stoneHeight == null || stoneHeight.contains(y))
                            || deepslate && current.is(Blocks.DEEPSLATE) && (deepHeight == null || deepHeight.contains(y));
        }
        @Override
        public boolean supported(int x, int y, int z) {
            if (y <= level.getMinBuildHeight()) return false;
            BlockState below = level.getBlockState(pos.set(x, y - 1, z));
            return below.blocksMotion() && below.getFluidState().isEmpty();
        }
        @Override
        public void replace(int x, int y, int z) {
            pos.set(x, y, z);
            boolean deep = level.getBlockState(pos).is(Blocks.DEEPSLATE);
            BlockState target = block(kind, deep).defaultBlockState();
            if (kind == Kind.DIRT && y + 1 >= level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z)) {
                target = (level.getBiome(pos).is(Biomes.DESERT) ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
            }
            level.setBlock(pos, target, 2);
        }
    }
    /** Settings use the actual registered target block and its translated name. */
    public static Block generationBlock(Kind kind) { return block(kind, false); }
    public static Block generationBlock(Kind kind, boolean deepslate) { return block(kind, deepslate); }

    private static Block block(Kind kind, boolean deep) {
        return switch (kind) {
            case DIRT -> Blocks.DIRT;
            case GRAVEL -> Blocks.GRAVEL;
            case COAL -> deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
            case COPPER -> deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
            case IRON -> deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
            case GOLD -> deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
            case SILVER, MITHRIL, ADAMANTIUM -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mite",
                    (deep ? "deepslate_" : "") + kind.name().toLowerCase(java.util.Locale.ROOT) + "_ore"));
            case INFESTED -> deep ? Blocks.INFESTED_DEEPSLATE : Blocks.INFESTED_STONE;
            case REDSTONE -> deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
            case DIAMOND -> deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
            case LAPIS -> deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
            case EMERALD -> deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
            case QUARTZ -> Blocks.NETHER_QUARTZ_ORE;
            case NETHER_GOLD -> Blocks.NETHER_GOLD_ORE;
            case NETHER_INFESTED -> BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mite", "infested_netherrack"));
        };
    }
}
