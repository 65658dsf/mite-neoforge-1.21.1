package io.github.ningmeng123.mite.gametest;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Registry wiring and actual cave mushroom placement in terrain generation. @author NingMeng123 */
@GameTestHolder("mite_caves")
@PrefixGameTestTemplate(false)
public final class CaveMushroomGenerationGameTests {
    private static final ResourceLocation MUSHROOMS = ResourceLocation.parse("mite:cave_mushrooms");

    private CaveMushroomGenerationGameTests() {}

    @GameTest(template = "empty")
    public static void caveMushroomsDecorateOverworldBiomesOnce(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var feature = ResourceKey.create(Registries.PLACED_FEATURE, MUSHROOMS);
        helper.assertTrue(registries.registryOrThrow(Registries.CONFIGURED_FEATURE).containsKey(MUSHROOMS),
                "The cave mushroom configured feature is loaded");
        helper.assertTrue(registries.registryOrThrow(Registries.PLACED_FEATURE).containsKey(MUSHROOMS),
                "The cave mushroom placed feature is loaded");
        for (var biome : registries.registryOrThrow(Registries.BIOME).holders().toList()) {
            var steps = biome.value().getGenerationSettings().features();
            int decoration = GenerationStep.Decoration.VEGETAL_DECORATION.ordinal();
            long inDecoration = decoration < steps.size()
                    ? steps.get(decoration).stream().filter(holder -> holder.is(feature)).count() : 0;
            long total = steps.stream().flatMap(holders -> holders.stream()).filter(holder -> holder.is(feature)).count();
            long expected = biome.is(BiomeTags.IS_OVERWORLD) ? 1 : 0;
            helper.assertValueEqual(total, expected, "Cave mushroom biome membership for " + biome.key().location());
            helper.assertValueEqual(inDecoration, expected, "Cave mushroom decoration step for " + biome.key().location());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void caveMushroomsUseBothSourceHeightBandsAndAllPatchAttempts(GameTestHelper helper) {
        var cave = new Cave(helper);
        BlockPos shallow = cave.roomAt(64);
        BlockPos deep = cave.roomAt(20);

        helper.assertFalse(cave.place(-1, -1), "A failed one-in-four roll in each band leaves a suitable cave empty");
        helper.assertTrue(cave.region.isEmptyBlock(shallow) && cave.region.isEmptyBlock(deep),
                "No candidate is changed when all eight rolls fail");
        helper.assertTrue(cave.place(shallow.getY(), deep.getY()), "Both accepted source patches can place brown mushrooms");
        helper.assertTrue(cave.region.getBlockState(shallow).is(Blocks.BROWN_MUSHROOM), "The Y48–79 patch places a brown mushroom");
        helper.assertTrue(cave.region.getBlockState(deep).is(Blocks.BROWN_MUSHROOM), "The Y0–127 patch places a brown mushroom");
        helper.assertFalse(cave.center.isLightCorrect(), "The feature works before Initialize Light without advancing the chunk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void caveMushroomsRejectSkyAndBrightLightAndInvalidGround(GameTestHelper helper) {
        var cave = new Cave(helper);
        BlockPos candidate = cave.roomAt(64);
        BlockPos roof = candidate.above(3);
        cave.region.setBlock(roof, Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(cave.place(64, -1), "A sky-exposed stone floor cannot grow a cave mushroom");
        cave.region.setBlock(roof, Blocks.STONE.defaultBlockState(), 2);
        cave.region.setBlock(roof.east(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertFalse(cave.place(64, -1), "A covered candidate still counts as outdoors when air connects it to an open column");
        cave.region.setBlock(roof.east(), Blocks.STONE.defaultBlockState(), 2);
        cave.region.setBlock(candidate.east(), Blocks.GLOWSTONE.defaultBlockState(), 2);
        helper.assertFalse(cave.place(64, -1), "A neighboring level-15 emitter makes the site too bright even during worldgen");
        cave.region.setBlock(candidate.east(), Blocks.AIR.defaultBlockState(), 2);
        cave.region.setBlock(candidate.below(), Blocks.SAND.defaultBlockState(), 2);
        helper.assertFalse(cave.place(64, -1), "A dark spot on sand cannot grow the brown mushroom");
        helper.assertTrue(cave.region.isEmptyBlock(candidate), "Rejected patches leave the candidate empty");
        cave.region.setBlock(candidate.below(), Blocks.GRAVEL.defaultBlockState(), 2);
        BlockPos torch = candidate.east();
        cave.region.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
        helper.assertValueEqual(cave.region.getBlockState(torch).getLightEmission(cave.region, torch), 14,
                "The neighboring torch emits level fourteen at the source");
        helper.assertTrue(cave.place(64, -1) && cave.region.getBlockState(candidate).is(Blocks.BROWN_MUSHROOM),
                "A covered gravel floor remains legal at light thirteen after one block of falloff");
        helper.succeed();
    }

    private static final class Cave {
        private final ServerLevel source;
        private final ProtoChunk center;
        private final WorldGenRegion region;

        private Cave(GameTestHelper helper) {
            source = helper.getLevel().getServer().overworld();
            ChunkPos site = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            var biomes = source.registryAccess().registryOrThrow(Registries.BIOME);
            var plains = biomes.getHolderOrThrow(Biomes.PLAINS);
            var sampler = source.getChunkSource().randomState().sampler();
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(site.x, site.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, source, biomes, null);
                chunk.fillBiomesFromNoise((qx, qy, qz, noise) -> plains, sampler);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
                };
            });
            center = (ProtoChunk) chunks.get(site.x, site.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new WorldGenRegion(source, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }

        private BlockPos roomAt(int y) {
            BlockPos origin = center.getPos().getWorldPosition();
            BlockPos candidate = new BlockPos(origin.getX() + 8, y, origin.getZ() + 8);
            for (BlockPos floor : BlockPos.betweenClosed(candidate.offset(-3, -1, -3), candidate.offset(3, -1, 3))) {
                region.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
                region.setBlock(floor.above(4), Blocks.STONE.defaultBlockState(), 2);
            }
            return candidate;
        }

        private boolean place(int shallowY, int deepY) {
            var configured = source.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(MUSHROOMS);
            var random = new PatchRolls(shallowY, deepY);
            boolean placed = configured.place(region, source.getChunkSource().getGenerator(), random, center.getPos().getWorldPosition());
            if (!random.draws.isEmpty()) throw new AssertionError("Cave mushroom feature skipped source random draws");
            return placed;
        }
    }

    private static final class PatchRolls extends LegacyRandomSource {
        private record Draw(int bound, int result) {}
        private final ArrayDeque<Draw> draws = new ArrayDeque<>();

        private PatchRolls(int shallowY, int deepY) {
            super(196L);
            for (int round = 0; round < 4; round++) {
                patch(32, round == 0 && shallowY >= 0 ? shallowY - 48 : -1);
                patch(128, round == 0 ? deepY : -1);
            }
        }

        private void patch(int heights, int height) {
            draws.add(new Draw(4, height < 0 ? 1 : 0));
            if (height < 0) return;
            draws.add(new Draw(16, 0));
            draws.add(new Draw(heights, height));
            draws.add(new Draw(16, 0));
            for (int attempt = 0; attempt < 64; attempt++) {
                draws.add(new Draw(4, 0));
                draws.add(new Draw(4, 0));
                for (int horizontal = 0; horizontal < 4; horizontal++) draws.add(new Draw(8, 0));
            }
        }

        @Override public int nextInt(int bound) {
            var draw = draws.poll();
            if (draw == null || draw.bound() != bound) throw new AssertionError("Unexpected cave mushroom random bound: " + bound);
            return draw.result();
        }
    }
}
