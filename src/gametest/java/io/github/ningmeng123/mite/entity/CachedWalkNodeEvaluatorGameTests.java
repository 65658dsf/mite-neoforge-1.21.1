package io.github.ningmeng123.mite.entity;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exact native classifications, adjacent-hook reuse and bounded cache lifetime. @author NingMeng123 */
@GameTestHolder("mite_path_perf")
@PrefixGameTestTemplate(false)
public final class CachedWalkNodeEvaluatorGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();

    @GameTest(template = "empty", batch = "cached_walk_types", timeoutTicks = 100)
    public static void repeatedBlockTypesReuseTheActualNeoForgeAdjacentHook(GameTestHelper h) {
        Fixture f = new Fixture(h);
        CountingNeighbourState neighbour = new CountingNeighbourState();
        f.region.set(f.origin.offset(-1, 0, -1), neighbour);
        var plain = new PlainProbe();
        var cached = new CachedProbe();
        plain.prepare(f.region, f.mob);
        cached.prepare(f.region, f.mob);
        try {
            for (int repeat = 0; repeat < 64; repeat++)
                h.assertTrue(plain.type(f.origin) == PathType.DANGER_OTHER, "The real native adjacent hook supplies the custom danger type");
            h.assertTrue(neighbour.calls == 64, "The uncached native classifier repeats the adjacent hook on every query");
            neighbour.calls = 0;
            for (int repeat = 0; repeat < 64; repeat++)
                h.assertTrue(cached.type(f.origin) == PathType.DANGER_OTHER, "The cached classifier retains the native hook's exact result");
            h.assertTrue(neighbour.calls == 1, "One synchronous search evaluates the same complete adjacent classification only once");
        } finally {
            cached.done();
            plain.done();
            f.mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "cached_walk_types", timeoutTicks = 100)
    public static void hazardsWaterDoorsAndRailsMatchNativeTypesAndDoorPolicies(GameTestHelper h) {
        Fixture f = new Fixture(h);
        BlockState[] terrain = {
                Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.FIRE.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(),
                Blocks.POINTED_DRIPSTONE.defaultBlockState(), Blocks.WITHER_ROSE.defaultBlockState(),
                Blocks.CACTUS.defaultBlockState(), Blocks.SWEET_BERRY_BUSH.defaultBlockState(), Blocks.HONEY_BLOCK.defaultBlockState(),
                Blocks.OAK_DOOR.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true),
                Blocks.IRON_DOOR.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_FENCE_GATE.defaultBlockState(), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true),
                Blocks.RAIL.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true), Blocks.IRON_TRAPDOOR.defaultBlockState()
        };
        for (int index = 0; index < terrain.length; index++) f.region.set(f.origin.east(index * 4), terrain[index]);
        var plain = new PlainProbe();
        var cached = new CachedProbe();
        plain.prepare(f.region, f.mob);
        cached.prepare(f.region, f.mob);
        try {
            for (int policy = 0; policy < 3; policy++) {
                plain.setCanPassDoors(policy > 0); cached.setCanPassDoors(policy > 0);
                plain.setCanOpenDoors(policy == 2); cached.setCanOpenDoors(policy == 2);
                for (int index = 0; index < terrain.length; index++)
                    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 2; y++) for (int z = -1; z <= 1; z++) {
                        BlockPos pos = f.origin.offset(index * 4 + x, y, z);
                        PathType expected = plain.type(pos), actual = cached.type(pos);
                        h.assertTrue(actual == expected,
                                "Complete native block type is unchanged for terrain " + index + ", policy " + policy
                                        + " at " + pos + ": expected=" + expected + ", actual=" + actual);
                        PathType expectedBody = plain.mobType(pos), actualBody = cached.mobType(pos);
                        h.assertTrue(actualBody == expectedBody,
                                "Native body aggregation, rail handling and door policy are unchanged for terrain " + index
                                        + ", policy " + policy + " at " + pos + ": expected=" + expectedBody + ", actual=" + actualBody);
                    }
                h.assertTrue(cached.type(f.origin.east(15 * 4)) == (policy == 2 ? PathType.WALKABLE_DOOR : PathType.FENCE),
                        "The source closed-gate mixin follows the current door policy even after the cache was warmed");
                h.assertTrue(cached.type(f.origin.east(18 * 4)) == (policy == 2 ? PathType.WALKABLE_DOOR : PathType.TRAPDOOR),
                        "The source closed-trapdoor mixin follows the current door policy even after the cache was warmed");
            }
        } finally {
            cached.done();
            plain.done();
            f.mob.discard();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "cached_walk_types", timeoutTicks = 100)
    public static void foreignContextsBypassTheCacheAndTheNextSearchSeesChangedTerrain(GameTestHelper h) {
        Fixture f = new Fixture(h);
        f.region.set(f.origin.east(), Blocks.FIRE.defaultBlockState());
        var cached = new CachedProbe();
        cached.prepare(f.region, f.mob);
        try {
            h.assertTrue(cached.type(f.origin) == PathType.DANGER_FIRE, "The first search observes the neighboring fire");
            f.region.set(f.origin.east(), Blocks.AIR.defaultBlockState());
            h.assertTrue(cached.type(f.origin) == PathType.DANGER_FIRE, "A repeated query within one synchronous search reuses its classification");
            PathfindingContext other = new PathfindingContext(f.region, f.mob);
            h.assertTrue(cached.getPathType(other, f.origin.getX(), f.origin.getY(), f.origin.getZ()) == PathType.WALKABLE,
                    "An independent context never inherits the active search's cached classification");
        } finally { cached.done(); }
        cached.prepare(f.region, f.mob);
        try {
            h.assertTrue(cached.type(f.origin) == PathType.WALKABLE, "The next search observes the newly safe terrain");
        } finally {
            cached.done();
            f.mob.discard();
        }
        h.succeed();
    }

    private static final class PlainProbe extends WalkNodeEvaluator {
        PathType type(BlockPos pos) { return getPathType(currentContext, pos.getX(), pos.getY(), pos.getZ()); }
        PathType mobType(BlockPos pos) { return getPathTypeOfMob(currentContext, pos.getX(), pos.getY(), pos.getZ(), mob); }
    }

    private static final class CachedProbe extends CachedWalkNodeEvaluator {
        PathType type(BlockPos pos) { return getPathType(currentContext, pos.getX(), pos.getY(), pos.getZ()); }
        PathType mobType(BlockPos pos) { return getPathTypeOfMob(currentContext, pos.getX(), pos.getY(), pos.getZ(), mob); }
    }

    private static final class CountingNeighbourState extends BlockState {
        int calls;

        CountingNeighbourState() {
            super(Blocks.STONE, new Reference2ObjectArrayMap<>(), MapCodec.unit(Blocks.STONE.defaultBlockState()));
            initCache();
        }

        @Override public PathType getAdjacentBlockPathType(BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) {
            calls++;
            return PathType.DANGER_OTHER;
        }
    }

    /** An in-memory region exercises native classification without loading or changing world chunks. */
    private static final class TerrainRegion extends PathNavigationRegion {
        private final ServerLevel server;
        private final int floor;
        private final Map<BlockPos, BlockState> terrain = new HashMap<>();

        TerrainRegion(ServerLevel level, BlockPos origin) {
            super(level, origin.offset(-4, -3, -4), origin.offset(96, 4, 4));
            server = level;
            floor = origin.getY() - 1;
        }

        void set(BlockPos pos, BlockState state) {
            terrain.put(pos.immutable(), state);
            // Mirrors the native raw-type invalidation sent by a real block update.
            server.getPathTypeCache().invalidate(pos);
        }

        @Override public BlockState getBlockState(BlockPos pos) {
            return terrain.getOrDefault(pos, (pos.getY() <= floor ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
    }

    private static final class Fixture {
        final BlockPos origin;
        final Zombie mob;
        final TerrainRegion region;

        Fixture(GameTestHelper h) {
            origin = new BlockPos(-3_120_000 + IDS.getAndIncrement() * 128, h.absolutePos(BlockPos.ZERO).getY() + 8, -3_120_000);
            mob = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(mob != null, "The native zombie is available for evaluator preparation");
            mob.moveTo(origin.getX() + .5D, origin.getY(), origin.getZ() + .5D, 0F, 0F);
            region = new TerrainRegion(h.getLevel(), origin);
        }
    }
}
