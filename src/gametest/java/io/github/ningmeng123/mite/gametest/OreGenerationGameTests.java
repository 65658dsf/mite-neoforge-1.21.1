package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registry-applied biome rules and genuine pre-light FEATURES chunks; no client or setblock ore fixture. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OreGenerationGameTests {
    private OreGenerationGameTests() {}
    private static final Set<String> REMOVED_OVERWORLD = Set.of("ore_coal_upper", "ore_coal_lower", "ore_iron_upper", "ore_iron_middle", "ore_iron_small",
            "ore_copper", "ore_copper_large", "ore_gold", "ore_gold_lower", "ore_gold_extra", "ore_redstone", "ore_redstone_lower",
            "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried", "ore_lapis", "ore_lapis_buried", "ore_emerald",
            "ore_dirt", "ore_gravel", "ore_infested");
    private static final Set<String> REMOVED_NETHER = Set.of("ore_gold_nether", "ore_gold_deltas", "ore_quartz_nether", "ore_quartz_deltas");

    @GameTest(template = "empty")
    public static void resolvedBiomeFeaturesReplaceVanillaOresOnceWhileKeepingModernNetherContent(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        int checkedOverworld = 0, checkedNether = 0;
        for (Holder.Reference<Biome> biome : registry.holders().toList()) {
            List<ResourceLocation> features = features(biome);
            if (biome.is(BiomeTags.IS_OVERWORLD)) {
                checkedOverworld++;
                for (ResourceLocation feature : features) {
                    helper.assertFalse(feature.getNamespace().equals("minecraft") && REMOVED_OVERWORLD.contains(feature.getPath()),
                            "The biome still includes an independently identified duplicate vanilla ore: " + biome.key().location() + " / " + feature);
                }
                helper.assertValueEqual(features.stream().filter(id -> id.equals(ResourceLocation.parse("mite:ores_overworld"))).count(), 1L,
                        "Every modern overworld biome includes exactly one R196 candidate batch");
                biome.value().modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings().features().stream()
                        .flatMap(set -> set.stream()).flatMap(holder -> holder.unwrapKey().stream()).map(ResourceKey::location)
                        .filter(id -> id.getNamespace().equals("minecraft") && (id.getPath().equals("fossil_upper") || id.getPath().equals("fossil_lower")))
                        .forEach(id -> helper.assertTrue(features.contains(id), "Modern fossil structure features remain present in " + biome.key().location()));
            } else if (biome.is(BiomeTags.IS_NETHER)) {
                checkedNether++;
                for (ResourceLocation feature : features) {
                    helper.assertFalse(feature.getNamespace().equals("minecraft") && REMOVED_NETHER.contains(feature.getPath()),
                            "The Nether biome still includes duplicate old-default ore candidates: " + feature);
                }
                helper.assertValueEqual(features.stream().filter(id -> id.equals(ResourceLocation.parse("mite:ores_nether"))).count(), 1L,
                        "Every modern Nether biome includes exactly one R196 ore batch");
                helper.assertTrue(features.contains(ResourceLocation.parse("minecraft:ore_ancient_debris_large"))
                                && features.contains(ResourceLocation.parse("minecraft:ore_debris_small")),
                        "Modern ancient debris remains available in " + biome.key().location());
            }
        }
        helper.assertTrue(checkedOverworld > 0 && checkedNether == 5, "The registry assertions must cover actual loaded modern biomes");
        var underworld = registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse("mite:underworld")));
        helper.assertValueEqual(features(underworld).stream().filter(id -> id.equals(ResourceLocation.parse("mite:ores_underworld"))).count(), 1L,
                "The underworld receives exactly one ore batch through the actual registry");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modernNoiseSettingsRetainCavesAndHeightWithoutAnIndependentCopperIronVeinBypass(GameTestHelper helper) {
        var settings = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
        for (String name : new String[]{"overworld", "amplified", "large_biomes"}) {
            NoiseGeneratorSettings entry = settings.get(ResourceLocation.withDefaultNamespace(name));
            helper.assertTrue(entry != null, "The modern vanilla noise preset remains registered: " + name);
            helper.assertValueEqual(entry.noiseSettings().minY(), -64, "Ore changes preserve modern negative generation height");
            helper.assertValueEqual(entry.noiseSettings().height(), 384, "Ore changes preserve modern terrain height");
            helper.assertTrue(entry.aquifersEnabled() && !entry.useLegacyRandomSource(), "Modern aquifers and modern noise remain enabled");
            helper.assertFalse(entry.oreVeinsEnabled(), "Independent vanilla large copper/iron veins must not bypass R196 candidate batches");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualOverworldFeatureUsesDeepslateAndNeverFillsProtectedOrOpenColumns(GameTestHelper helper) {
        GenerationFixture f = new GenerationFixture(helper.getLevel(), new ChunkPos(6000, -6000), false);
        f.fill(-64, 129);
        int x = f.center.getPos().getMinBlockX(), z = f.center.getPos().getMinBlockZ();
        List<BlockPos> protectedPositions = new ArrayList<>();
        for (int y = -64; y < 129; y++) {
            for (int dx = 1; dx <= 4; dx++) {
                BlockPos position = new BlockPos(x + dx, y, z + 1);
                f.center.setBlockState(position, switch (dx) {
                    case 1 -> Blocks.AIR.defaultBlockState(); case 2 -> Blocks.WATER.defaultBlockState();
                    case 3 -> Blocks.BEDROCK.defaultBlockState(); default -> MiteWorldBlocks.MANTLE.get().defaultBlockState();
                }, false);
                protectedPositions.add(position);
            }
        }
        var previous = protectedPositions.stream().map(f.region::getBlockState).toList();
        helper.assertTrue(f.place("ores_overworld", 196L), "The actual registered R196 feature executes before INITIALIZE_LIGHT");
        for (int i = 0; i < protectedPositions.size(); i++) {
            helper.assertTrue(previous.get(i).equals(f.region.getBlockState(protectedPositions.get(i))),
                    "Ore generation changes protected/open terrain at " + protectedPositions.get(i));
        }
        int totalOre = 0, negativeDeepslate = 0;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = -64; y < 129; y++) {
            BlockPos at = new BlockPos(x + dx, y, z + dz);
            var id = BuiltInRegistries.BLOCK.getKey(f.region.getBlockState(at).getBlock());
            helper.assertFalse(id.getPath().contains("adamantium_ore"), "R196 never naturally produces adamantium in the overworld");
            if (id.getPath().endsWith("_ore")) {
                totalOre++;
                if (y < 0) {
                    helper.assertTrue(id.getPath().startsWith("deepslate_"), "Negative deepslate substrate becomes a stone-looking ore at " + at);
                    negativeDeepslate++;
                }
            }
        }
        helper.assertTrue(totalOre > 0 && negativeDeepslate > 0, "The real candidate batch must produce both ores and negative-height deepslate ore states");
        helper.assertFalse(f.center.isLightCorrect(), "The fixture remains a genuine pre-light ProtoChunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void realUnderworldGeneratorDecoratesFreshRockWithAdamantiumWithoutBreakingMantle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The actual underworld loads before checking ore generation");
        int adamantium = 0, allOre = 0;
        for (int offset = 0; offset < 3; offset++) {
            ChunkAccess chunk = level.getChunk(6200 + offset, -6200);
            helper.assertValueEqual(chunk.getData(MiteAttachments.UNDERWORLD_LAYOUT_VERSION.get()), 2, "Ore checks use genuine modern generated underworld chunks");
            BlockPos origin = chunk.getPos().getWorldPosition();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                helper.assertTrue(chunk.getBlockState(origin.offset(x, -64, z)).is(MiteWorldBlocks.MANTLE.get()), "Ore decoration cannot replace the mantle floor");
                helper.assertTrue(chunk.getBlockState(origin.offset(x, 319, z)).is(Blocks.BEDROCK), "Ore decoration cannot replace the protected roof");
                for (int y = -63; y < 319; y++) {
                    String id = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(origin.offset(x, y, z)).getBlock()).getPath();
                    if (id.endsWith("_ore")) allOre++;
                    if (id.equals("adamantium_ore")) adamantium++;
                }
            }
        }
        helper.assertTrue(allOre > 0 && adamantium > 0, "The real modern underworld generator must actually decorate rock with its source-only adamantium progression ore");
        helper.succeed();
    }

    private static List<ResourceLocation> features(Holder<Biome> biome) {
        return biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .flatMap(holder -> holder.unwrapKey().stream()).map(ResourceKey::location).toList();
    }

    static final class GenerationFixture {
        final ServerLevel source;
        final ProtoChunk center;
        final List<ProtoChunk> all = new ArrayList<>();
        final WorldGenRegion region;
        final boolean nether;
        GenerationFixture(ServerLevel source, ChunkPos origin, boolean nether) {
            this.source = source; this.nether = nether;
            var biomes = source.registryAccess().registryOrThrow(Registries.BIOME);
            var biome = biomes.getHolderOrThrow(nether ? Biomes.NETHER_WASTES : Biomes.PLAINS);
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(origin.x, origin.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, source, biomes, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, source.getChunkSource().randomState().sampler());
                all.add(chunk);
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null; }
                };
            });
            center = (ProtoChunk) chunks.get(origin.x, origin.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new WorldGenRegion(source, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }
        void fill(int min, int max) {
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (ProtoChunk chunk : all) {
                int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = min; y < max; y++) {
                    chunk.setBlockState(position.set(bx + x, y, bz + z), nether ? Blocks.NETHERRACK.defaultBlockState()
                            : y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState(), false);
                }
            }
        }
        boolean place(String name, long seed) {
            ConfiguredFeature<?, ?> feature = source.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                    .get(ResourceLocation.parse("mite:" + name));
            if (feature == null) throw new IllegalStateException("Configured R196 ore feature is missing: " + name);
            return feature.place(region, source.getChunkSource().getGenerator(), new LegacyRandomSource(seed), center.getPos().getWorldPosition());
        }
    }
}
