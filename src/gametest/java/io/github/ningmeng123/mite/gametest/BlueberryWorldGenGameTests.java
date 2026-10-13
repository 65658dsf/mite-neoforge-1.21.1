package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteFoodPlants;
import io.github.ningmeng123.mite.registry.MiteFoodSources;
import io.github.ningmeng123.mite.worldgen.BlueberryPatchFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual FEATURES-region placement uses unlit protochunks, never an already-lit ServerLevel. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class BlueberryWorldGenGameTests {
    private BlueberryWorldGenGameTests() {}

    @GameTest(template = "empty")
    public static void actualFeaturePlacesAMatureBushInAnUnlitFeaturesRegionAndKeeps64Attempts(GameTestHelper helper) {
        Fixture f = new Fixture(helper, true);
        f.assertUninitializedLight(helper);
        CenterRandom random = new CenterRandom();
        helper.assertTrue(f.place(random), "The real configured feature places on exposed forest soil before light initialization");
        BlockState placed = f.region.getBlockState(f.candidate);
        helper.assertTrue(placed.is(MiteFoodPlants.BLUEBERRY_BUSH.get()), "The actual protochunk contains the registered blueberry bush");
        helper.assertValueEqual(placed.getValue(SweetBerryBushBlock.AGE), 3, "Natural source bushes have mature age three");
        helper.assertValueEqual(random.coordinateDraws, 256, "A repeated candidate makes all 64 attempts, each with four independent bounded coordinate draws");
        helper.assertTrue(f.center.getPersistedStatus().isBefore(ChunkStatus.INITIALIZE_LIGHT), "Placement did not advance or initialize the chunk's light stage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualUnlitFeatureRejectsALeafCanopyEvenThoughMissingSkyDataReadsFifteen(GameTestHelper helper) {
        Fixture f = new Fixture(helper, true);
        BlockPos roof = f.candidate.above(3);
        f.region.setBlock(roof, Blocks.OAK_LEAVES.defaultBlockState(), 2);
        f.assertUninitializedLight(helper);
        helper.assertTrue(f.region.canSeeSky(f.candidate), "The real empty sky storage's fifteen fallback falsely reports the covered protochunk column as sky-visible");
        helper.assertValueEqual(f.region.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                f.candidate.getX(), f.candidate.getZ()), f.candidate.getY(), "The native no-leaves candidate height stays beneath the canopy");
        CenterRandom random = new CenterRandom();
        helper.assertFalse(f.place(random), "Actual feature placement rejects the leaf column without trusting the false-positive sky cache");
        helper.assertTrue(f.region.getBlockState(f.candidate).isAir(), "All denied attempts leave the candidate empty");
        helper.assertValueEqual(random.coordinateDraws, 256, "Canopy rejection preserves all source 64 placement attempts");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unlitColumnPredicateKeepsTransparentGlassButRejectsOpaqueCoverFarmlandAndOtherBiomes(GameTestHelper helper) {
        Fixture f = new Fixture(helper, true);
        BlockState bush = MiteFoodPlants.BLUEBERRY_BUSH.get().defaultBlockState();
        BlockPos roof = f.candidate.above(3);
        f.region.setBlock(roof, Blocks.GLASS.defaultBlockState(), 2);
        helper.assertValueEqual(f.region.getBlockState(roof).getLightBlock(f.region, roof), 0, "Native ordinary glass has zero opacity in the real generation region");
        helper.assertTrue(BlueberryPatchFeature.canPlaceDuringGeneration(f.region, f.candidate, bush), "Zero-opacity cover does not itself exclude a forest soil candidate");
        f.region.setBlock(roof, Blocks.TINTED_GLASS.defaultBlockState(), 2);
        helper.assertFalse(BlueberryPatchFeature.canPlaceDuringGeneration(f.region, f.candidate, bush), "Native light-blocking tinted glass excludes the candidate");
        f.region.setBlock(roof, Blocks.AIR.defaultBlockState(), 2);
        f.region.setBlock(f.candidate.below(), Blocks.FARMLAND.defaultBlockState(), 2);
        helper.assertFalse(BlueberryPatchFeature.canPlaceDuringGeneration(f.region, f.candidate, bush), "Fertile or ordinary farmland is still forbidden for blueberry bushes");
        f.region.setBlock(f.candidate.below(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertFalse(BlueberryPatchFeature.canPlaceDuringGeneration(f.region, f.candidate, bush), "Native unsuitable stone soil cannot sustain the bush");
        Fixture plains = new Fixture(helper, false);
        helper.assertFalse(BlueberryPatchFeature.canPlaceDuringGeneration(plains.region, plains.candidate, bush), "A clear unlit plains column cannot bypass the forest habitat tag");
        helper.succeed();
    }

    private static final class Fixture {
        private final ServerLevel source;
        private final ProtoChunk center;
        private final UnlitRegion region;
        private final BlockPos candidate;

        private Fixture(GameTestHelper helper, boolean forest) {
            source = helper.getLevel();
            ChunkPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            var biomes = source.registryAccess().registryOrThrow(Registries.BIOME);
            var biome = biomes.getHolderOrThrow(forest ? Biomes.FOREST : Biomes.PLAINS);
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(origin.x, origin.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, source, biomes, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, source.getChunkSource().randomState().sampler());
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                        return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null;
                    }
                };
            });
            center = (ProtoChunk) chunks.get(origin.x, origin.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new UnlitRegion(source, chunks, center);
            candidate = new BlockPos(origin.getMinBlockX() + 8, source.getMinBuildHeight() + 16, origin.getMinBlockZ() + 8);
            region.setBlock(candidate.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
        private boolean place(CenterRandom random) {
            return new ConfiguredFeature<>(MiteFoodSources.BLUEBERRY_PATCH.get(), NoneFeatureConfiguration.INSTANCE)
                    .place(region, source.getChunkSource().getGenerator(), random, candidate);
        }
        private void assertUninitializedLight(GameTestHelper helper) {
            helper.assertValueEqual(center.getPersistedStatus(), ChunkStatus.CARVERS, "This real protochunk is the input to a FEATURES step, preceding INITIALIZE_LIGHT");
            helper.assertFalse(center.isLightCorrect(), "The fixture does not mark a protochunk as lighted");
            var sky = region.getLightEngine().getLayerListener(LightLayer.SKY);
            helper.assertTrue(sky.getDataLayerData(SectionPos.of(candidate)) == null, "A fresh real sky engine has no initialized section data for this FEATURES column");
            helper.assertValueEqual(sky.getLightValue(candidate), 15, "Fixed-version SkyLightSectionStorage's missing-column visible read is fifteen, not zero");
        }
    }

    /** Only the light engine is isolated; block, biome, heightmap and placement use WorldGenRegion. */
    private static final class UnlitRegion extends WorldGenRegion {
        private final LevelLightEngine uninitializedLight;
        private UnlitRegion(ServerLevel source, StaticCache2D<GenerationChunkHolder> chunks, ProtoChunk center) {
            super(source, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
            uninitializedLight = new LevelLightEngine(new LightChunkGetter() {
                @Override public LightChunk getChunkForLighting(int x, int z) { return UnlitRegion.this.getChunk(x, z); }
                @Override public BlockGetter getLevel() { return UnlitRegion.this; }
            }, true, true);
        }
        @Override public LevelLightEngine getLightEngine() { return uninitializedLight; }
    }

    private static final class CenterRandom extends LegacyRandomSource {
        private int coordinateDraws;
        private CenterRandom() { super(196L); }
        @Override public int nextInt(int bound) {
            if (bound == 8) { coordinateDraws++; return 0; }
            return super.nextInt(bound);
        }
    }
}
