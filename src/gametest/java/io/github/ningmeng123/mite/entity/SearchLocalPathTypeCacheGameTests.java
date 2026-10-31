package io.github.ningmeng123.mite.entity;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native raw-cache collisions, mutable evaluation positions and search lifetime. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class SearchLocalPathTypeCacheGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty", batch = "search_local_raw_types", timeoutTicks = 100)
    public static void nativeHashCollisionsRecomputeWhileOneSearchClassifiesOnlyTheTwoUniqueCells(GameTestHelper h) {
        BlockPos origin = origin(h);
        BlockPos[] pair = collidingPositions(origin);
        h.assertTrue(!pair[0].equals(pair[1]) && nativeIndex(pair[0]) == nativeIndex(pair[1]),
                "The fixture uses distinct packed positions in the same actual native direct-map slot");
        TerrainRegion plain = new TerrainRegion(h.getLevel(), origin);
        SearchTerrainRegion search = new SearchTerrainRegion(h.getLevel(), origin);
        RawTypeState plainA = new RawTypeState(Blocks.STONE, PathType.FENCE), plainB = new RawTypeState(Blocks.STONE, PathType.WATER);
        RawTypeState cachedA = new RawTypeState(Blocks.STONE, PathType.FENCE), cachedB = new RawTypeState(Blocks.STONE, PathType.WATER);
        plain.set(pair[0], plainA); plain.set(pair[1], plainB);
        search.set(pair[0], cachedA); search.set(pair[1], cachedB);
        PathTypeCache plainCache = new PathTypeCache(), searchCache = new PathTypeCache();
        BlockPos.MutableBlockPos lookup = new BlockPos.MutableBlockPos();
        for (int repeat = 0; repeat < 32; repeat++) {
            lookup.set(pair[0]);
            h.assertTrue(plainCache.getOrCompute(plain, lookup) == PathType.FENCE
                            && searchCache.getOrCompute(search, lookup) == PathType.FENCE,
                    "The first raw NeoForge hook retains its exact classification with mutable lookup positions");
            lookup.set(pair[1]);
            h.assertTrue(plainCache.getOrCompute(plain, lookup) == PathType.WATER
                            && searchCache.getOrCompute(search, lookup) == PathType.WATER,
                    "The colliding cell never inherits the first cell's classification");
        }
        h.assertTrue(plainA.rawCalls == 32 && plainB.rawCalls == 32,
                "Ordinary native callers retain their direct-map behavior and actually recompute all 64 alternating queries");
        h.assertTrue(cachedA.rawCalls == 1 && cachedB.rawCalls == 1,
                "One opted-in search performs the real raw hook only once for each unique cell despite native hash collisions");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "search_local_raw_types", timeoutTicks = 100)
    public static void cachedRawHitsStillAdvanceTheNativePositionUsedByEveryAdjacentHook(GameTestHelper h) {
        BlockPos origin = origin(h);
        SearchTerrainRegion region = new SearchTerrainRegion(h.getLevel(), origin);
        List<PositionState> neighbors = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x == 0 && z == 0) continue;
            PositionState state = new PositionState(origin.offset(x, y, z));
            neighbors.add(state);
            region.set(state.position, state);
        }
        Zombie mob = EntityType.ZOMBIE.create(h.getLevel());
        h.assertTrue(mob != null, "The real native mob is available for its pathfinding context");
        mob.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
        try {
            PathfindingContext context = new PathfindingContext(region, mob);
            // Prime every raw entry in reverse traversal order, leaving an unrelated mutable position behind.
            for (int index = neighbors.size() - 1; index >= 0; index--) {
                BlockPos pos = neighbors.get(index).position;
                h.assertTrue(context.getPathTypeFromState(pos.getX(), pos.getY(), pos.getZ()) == PathType.OPEN,
                        "Each fixture cell begins with an actual native raw classification");
            }
            for (int repeat = 0; repeat < 2; repeat++)
                h.assertTrue(WalkNodeEvaluator.checkNeighbourBlocks(context, origin.getX(), origin.getY(), origin.getZ(), PathType.WALKABLE)
                                == PathType.WALKABLE,
                        "The unchanged native neighbor traversal keeps the safe type while raw lookups hit the local cache");
            for (PositionState state : neighbors) {
                h.assertTrue(state.rawCalls == 1, "Primed raw cells never repeat their block hook: " + state.position);
                h.assertTrue(state.adjacentCalls == 2 && state.wrongPositions == 0,
                        "Every adjacent hook sees its own exact native evaluation position on both traversals: " + state.position
                                + ", calls=" + state.adjacentCalls + ", wrong=" + state.wrongPositions);
            }
        } finally { mob.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "search_local_raw_types", timeoutTicks = 100)
    public static void rawClassificationsMatchNativeAcrossHazardsFluidsPortalsAndRails(GameTestHelper h) {
        BlockPos origin = origin(h);
        TerrainRegion plain = new TerrainRegion(h.getLevel(), origin);
        SearchTerrainRegion search = new SearchTerrainRegion(h.getLevel(), origin);
        BlockState[] terrain = {
                Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.FIRE.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(),
                Blocks.POINTED_DRIPSTONE.defaultBlockState(), Blocks.WITHER_ROSE.defaultBlockState(), Blocks.CACTUS.defaultBlockState(),
                Blocks.SWEET_BERRY_BUSH.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(),
                Blocks.OAK_DOOR.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true),
                Blocks.IRON_DOOR.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState(), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true),
                Blocks.RAIL.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true), Blocks.IRON_TRAPDOOR.defaultBlockState()
        };
        for (int index = 0; index < terrain.length; index++) {
            plain.set(origin.east(index * 4), terrain[index]);
            search.set(origin.east(index * 4), terrain[index]);
        }
        PathTypeCache plainCache = new PathTypeCache(), searchCache = new PathTypeCache();
        for (int repeat = 0; repeat < 2; repeat++) for (int index = 0; index < terrain.length; index++)
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                BlockPos pos = origin.offset(index * 4 + x, y, z);
                PathType expected = plainCache.getOrCompute(plain, pos), actual = searchCache.getOrCompute(search, pos);
                h.assertTrue(actual == expected, "Raw native classification is unchanged for terrain " + index + " at " + pos
                        + ": expected=" + expected + ", actual=" + actual);
            }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "search_local_raw_types", timeoutTicks = 100)
    public static void realLoadedRegionsKeepRawTypesWithinOneSearchAndReadUpdatesInTheNextSearch(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null,
                "The actual test chunk is published before constructing a loaded-only search region")).thenExecute(() -> {
                    PathTypeCache nativeCache = new PathTypeCache();
                    var first = new LoadedPathNavigationRegion(h.getLevel(), pos.offset(-2, -2, -2), pos.offset(2, 2, 2));
                    h.assertTrue(nativeCache.getOrCompute(first, pos) == PathType.WATER, "The first real search classifies actual loaded water");
                    h.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                    nativeCache.invalidate(pos);
                    h.assertTrue(nativeCache.getOrCompute(first, pos) == PathType.WATER,
                            "The active synchronous region retains its original block and raw-type snapshot");
                    var second = new LoadedPathNavigationRegion(h.getLevel(), pos.offset(-2, -2, -2), pos.offset(2, 2, 2));
                    h.assertTrue(nativeCache.getOrCompute(second, pos) == PathType.BLOCKED && second.getFluidState(pos).isEmpty(),
                            "A new native search reads the changed actual terrain without inheriting stale raw types or fluids");
                }).thenSucceed();
    }

    private static BlockPos origin(GameTestHelper h) {
        return new BlockPos(-3_280_000 + IDS.getAndIncrement() * 128, h.absolutePos(BlockPos.ZERO).getY() + 8, -3_280_000);
    }

    private static int nativeIndex(BlockPos pos) { return (int)HashCommon.mix(pos.asLong()) & 4095; }

    private static BlockPos[] collidingPositions(BlockPos origin) {
        Map<Integer, BlockPos> slots = new HashMap<>();
        for (int index = 0; index < 8192; index++) {
            BlockPos pos = origin.offset(index % 64, index / 64 % 4, index / 256);
            BlockPos previous = slots.putIfAbsent(nativeIndex(pos), pos);
            if (previous != null) return new BlockPos[]{previous, pos};
        }
        throw new AssertionError("Distinct fixture positions must collide in the native 4096-slot cache");
    }

    private static class RawTypeState extends BlockState {
        final PathType type;
        int rawCalls;

        RawTypeState(Block block, PathType type) {
            super(block, new Reference2ObjectArrayMap<>(), MapCodec.unit(block.defaultBlockState()));
            this.type = type;
            initCache();
        }

        @Override public PathType getBlockPathType(BlockGetter level, BlockPos pos, @Nullable Mob mob) {
            rawCalls++;
            return type;
        }
    }

    private static final class PositionState extends RawTypeState {
        final BlockPos position;
        int adjacentCalls, wrongPositions;

        PositionState(BlockPos position) { super(Blocks.AIR, PathType.OPEN); this.position = position; }

        @Override public PathType getAdjacentBlockPathType(BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) {
            adjacentCalls++;
            if (!position.equals(pos) || level.getBlockState(pos) != this) wrongPositions++;
            return null;
        }
    }

    private static class TerrainRegion extends PathNavigationRegion {
        private final ServerLevel server;
        private final int floor;
        private final Map<BlockPos, BlockState> terrain = new HashMap<>();

        TerrainRegion(ServerLevel level, BlockPos origin) {
            super(level, origin.offset(-4, -3, -4), origin.offset(96, 4, 40));
            server = level;
            floor = origin.getY() - 1;
        }

        void set(BlockPos pos, BlockState state) {
            terrain.put(pos.immutable(), state);
            server.getPathTypeCache().invalidate(pos);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return terrain.getOrDefault(pos, (pos.getY() <= floor ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
    }

    /** Isolated hook fixtures delegate storage to the actual production per-search region cache. */
    private static final class SearchTerrainRegion extends TerrainRegion implements SearchLocalPathTypeCache {
        private final LoadedPathNavigationRegion cache;

        SearchTerrainRegion(ServerLevel level, BlockPos origin) {
            super(level, origin);
            cache = new LoadedPathNavigationRegion(level, origin.offset(-4, -3, -4), origin.offset(96, 4, 40));
        }

        @Override public PathType cachedRawPathType(long position) { return cache.cachedRawPathType(position); }
        @Override public void cacheRawPathType(long position, PathType type) { cache.cacheRawPathType(position, type); }
    }
}
