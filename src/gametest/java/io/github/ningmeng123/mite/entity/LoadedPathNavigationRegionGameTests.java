package io.github.ningmeng123.mite.entity;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real loaded terrain, mutable lookup positions, fluid states and search-local cache lifetime. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class LoadedPathNavigationRegionGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_loaded_path_region_test", Integer::compareTo);

    @GameTest(template = "empty", batch = "loaded_path_region", timeoutTicks = 400)
    public static void loadedStatesAndFluidsRetainExactPropertiesWithMutableLookupPositions(GameTestHelper h) {
        Fixture f = new Fixture(h);
        var openGate = Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true);
        h.getLevel().setBlock(f.origin, Blocks.STONE.defaultBlockState(), 2);
        h.getLevel().setBlock(f.origin.east(), Blocks.WATER.defaultBlockState(), 2);
        h.getLevel().setBlock(f.origin.east(2), openGate, 2);
        h.getLevel().setBlock(f.origin.east(3), Blocks.AIR.defaultBlockState(), 2);
        f.whenReady(() -> {
            var region = f.region();
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos().set(f.origin);
            h.assertTrue(region.getBlockState(position).is(Blocks.STONE), "Loaded negative-coordinate stone retains its exact state");
            position.set(f.origin.east());
            h.assertTrue(region.getBlockState(position).is(Blocks.WATER)
                            && region.getFluidState(position).is(Fluids.WATER),
                    "Fluid lookup uses the same loaded block state as path-type lookup");
            position.set(f.origin.east(2));
            h.assertTrue(region.getBlockState(position).equals(openGate), "An open gate retains its collision-relevant state properties");
            position.set(f.origin.east(3));
            h.assertTrue(region.getBlockState(position).isAir() && region.getFluidState(position).isEmpty(),
                    "Actual loaded air remains air rather than becoming an unloaded-terrain barrier");
            position.set(f.origin);
            h.assertTrue(region.getBlockState(position).is(Blocks.STONE), "Reusing a mutable BlockPos cannot corrupt earlier cache keys");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "loaded_path_region", timeoutTicks = 400)
    public static void repeatedStateAndFluidReadsAreCachedOnlyForOneNativeSearch(GameTestHelper h) {
        Fixture f = new Fixture(h);
        h.getLevel().setBlock(f.origin, Blocks.WATER.defaultBlockState(), 2);
        f.whenReady(() -> {
            var first = f.region();
            h.assertTrue(first.getFluidState(f.origin).is(Fluids.WATER), "The first query reads actual water");
            h.getLevel().setBlock(f.origin, Blocks.STONE.defaultBlockState(), 2);
            for (int repeat = 0; repeat < 256; ++repeat) {
                h.assertTrue(first.getBlockState(f.origin).is(Blocks.WATER) && first.getFluidState(f.origin).is(Fluids.WATER),
                        "One synchronous search reuses its first block read for repeated state and fluid requests");
            }
            var second = f.region();
            h.assertTrue(second.getBlockState(f.origin).is(Blocks.STONE) && second.getFluidState(f.origin).isEmpty(),
                    "The next native search observes world changes instead of inheriting a stale block cache");
            f.close();
            h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "loaded_path_region", timeoutTicks = 400)
    public static void missingAndOutOfBoundsTerrainIsBlockedWithoutLoadingChunks(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.whenReady(() -> {
            BlockPos missing = f.origin.offset(20_000, 0, 20_000);
            ChunkPos chunk = new ChunkPos(missing);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                    "The remote missing-chunk fixture begins unloaded");
            var empty = new LoadedPathNavigationRegion(h.getLevel(), missing, missing.offset(15, 3, 15));
            h.assertTrue(empty.getBlockState(missing).is(Blocks.BEDROCK) && empty.getFluidState(missing).isEmpty(),
                    "Missing terrain is an impassable barrier rather than imaginary air or water");
            var loaded = f.region();
            h.assertTrue(loaded.getBlockState(f.origin.offset(64, 0, 0)).is(Blocks.BEDROCK),
                    "Lookups beyond the bounded region remain blocked");
            BlockPos above = new BlockPos(f.origin.getX(), h.getLevel().getMaxBuildHeight(), f.origin.getZ());
            BlockPos below = new BlockPos(f.origin.getX(), h.getLevel().getMinBuildHeight() - 1, f.origin.getZ());
            h.assertTrue(loaded.getBlockState(above).is(Blocks.BEDROCK) && loaded.getBlockState(below).is(Blocks.BEDROCK),
                    "Build-height boundaries remain impassable for pursuit searches");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                    "Constructing and querying a missing region never loads its chunk");
            f.close();
            h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final ChunkPos chunk;
        boolean closed;

        Fixture(GameTestHelper h) {
            helper = h;
            origin = new BlockPos(-2_560_000 + key * 64, h.absolutePos(BlockPos.ZERO).getY() + 8, -2_560_000);
            chunk = new ChunkPos(origin);
            h.getLevel().setBlock(origin, Blocks.STONE.defaultBlockState(), 2);
            h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        LoadedPathNavigationRegion region() {
            return new LoadedPathNavigationRegion(helper.getLevel(), origin.offset(-1, -1, -1), origin.offset(7, 4, 3));
        }

        void whenReady(Runnable action) {
            helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                    helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                    "The fixture chunk is published before constructing the loaded-only region"))
                    .thenExecute(() -> {
                        try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
                    });
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
        }
    }
}
