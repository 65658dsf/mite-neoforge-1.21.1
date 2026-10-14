package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.registry.MiteDecorativeContent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Genuine pre-light worldgen with separate cave and surface biome layers. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DecorativeGenerationGameTests {
    private DecorativeGenerationGameTests() {}
    @GameTest(template = "empty")
    public static void lowestSectionOriginDoesNotHideSurfaceDahliaBehindCaveBiome(GameTestHelper helper) {
        var fixture = new SurfaceFixture(helper.getLevel(), new ChunkPos(7310, -7310), Biomes.PLAINS);
        helper.assertValueEqual(fixture.origin().getY(), -64, "Actual modern decoration starts at the negative minimum section");
        helper.assertFalse(fixture.center.isLightCorrect(), "Fixture reaches the genuine pre-light features stage");
        helper.assertTrue(fixture.region.getBiome(fixture.origin()).is(Biomes.LUSH_CAVES), "Actual origin Y=-64 has the cave biome");
        helper.assertTrue(fixture.region.getBiome(fixture.surface()).is(Biomes.PLAINS), "Surface above the same column has plains");
        helper.assertTrue(fixture.place(MiteDecorativeContent.DAHLIA.get()), "Actual feature places dahlia from the surface selection rather than the cave candidate list");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void lowestSectionOriginDoesNotLoseTheSurfaceSwampAlliumRule(GameTestHelper helper) {
        var fixture = new SurfaceFixture(helper.getLevel(), new ChunkPos(7330, -7330), Biomes.SWAMP);
        helper.assertValueEqual(fixture.origin().getY(), -64, "Actual modern decoration starts at the negative minimum section");
        helper.assertTrue(fixture.region.getBiome(fixture.origin()).is(Biomes.LUSH_CAVES), "Actual origin has a non-swamp cave biome");
        helper.assertTrue(fixture.region.getBiome(fixture.surface()).is(Biomes.SWAMP), "Surface above the cave has the real swamp biome");
        helper.assertTrue(fixture.place(Blocks.ALLIUM), "Source swamp-only allium can actually generate on the swamp surface");
        helper.succeed();
    }
    private static final class SurfaceFixture {
        final ServerLevel source;
        final ProtoChunk center;
        final List<ProtoChunk> chunks = new ArrayList<>();
        final WorldGenRegion region;
        SurfaceFixture(ServerLevel source, ChunkPos chunkPos, ResourceKey<Biome> surface) {
            this.source = source;
            var biomes = source.registryAccess().registryOrThrow(Registries.BIOME);
            var caveHolder = biomes.getHolderOrThrow(Biomes.LUSH_CAVES);
            var surfaceHolder = biomes.getHolderOrThrow(surface);
            StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(chunkPos.x, chunkPos.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, source, biomes, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> qy < 0 ? caveHolder : surfaceHolder,
                        source.getChunkSource().randomState().sampler());
                chunks.add(chunk);
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null; }
                };
            });
            center = (ProtoChunk) cache.get(chunkPos.x, chunkPos.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            for (ProtoChunk chunk : chunks) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                    chunk.setBlockState(pos.set(chunk.getPos().getMinBlockX() + x, 64, chunk.getPos().getMinBlockZ() + z), Blocks.GRASS_BLOCK.defaultBlockState(), false);
                Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
            }
            region = new WorldGenRegion(source, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }
        BlockPos origin() { return center.getPos().getWorldPosition().atY(source.getMinBuildHeight()); }
        BlockPos surface() { return origin().atY(region.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin().getX(), origin().getZ())); }
        boolean place(Block plant) {
            for (int seed = 0; seed < 64; seed++) {
                boolean placed = MiteDecorativeContent.DECORATIVE_PATCH.get().place(new BlockStateConfiguration(plant.defaultBlockState()),
                        region, source.getChunkSource().getGenerator(), new LegacyRandomSource(seed), origin());
                if (placed) return true;
            }
            return false;
        }
    }
}
