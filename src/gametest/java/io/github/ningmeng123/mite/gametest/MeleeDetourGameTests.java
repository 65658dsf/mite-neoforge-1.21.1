package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.R196MonsterAi;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.github.ningmeng123.mite.entity.ZombieRoutePlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual attack endpoints and native movement around an elevated player perch. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class MeleeDetourGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_melee_detour_test", Integer::compareTo);

    @GameTest(template = "empty")
    public static void zombieUsesRightOpeningAndStairsToAttackPlayerOnFencePerch(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.ZOMBIE)) {
                h.assertFalse(SourceMeleeGoal.canStrike(f.mob, f.player), "The wall and height prevent the initial attack");
                var plan = f.zombiePlan();
                h.assertTrue(plan.completeWalkingRoute() && !plan.choosesBreaking(),
                        "A reachable physical attack position makes walking to the player on the fence complete");
                assertStairRoute(h, f, plan.path());
                h.assertTrue(f.mob.position().equals(Vec3.atBottomCenterOf(f.origin)),
                        "Finding another route does not teleport the monster");
                f.assertTerrainIntact();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void installedWitherSkeletonMeleeFindsTheSameReachableAttackLedge(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.WITHER_SKELETON)) {
                h.assertTrue(R196MonsterAi.managed(f.mob) && !ZombieRoutePlanner.applies(f.mob),
                        "The second species exercises shared melee pursuit independently of zombie digging");
                SourceMeleeGoal goal = f.melee();
                h.assertTrue(goal.canUse(), "The installed non-zombie player melee goal pursues the elevated perch");
                goal.start();
                goal.tick();
                assertStairRoute(h, f, f.mob.getNavigation().getPath());
                goal.stop();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void unfinishedMeleeDetourSurvivesMovingAwayAndLosingSight(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.WITHER_SKELETON)) {
                SourceMeleeGoal goal = f.melee();
                h.assertTrue(goal.canUse(), "The real installed melee goal starts the side route");
                goal.start();
                Path original = f.mob.getNavigation().getPath();
                assertStairRoute(h, f, original);
                double initialDistance = f.mob.distanceToSqr(f.player);
                f.mob.moveTo(f.origin.getX() + .5D, f.origin.getY(), f.origin.getZ() + 3.5D, 0F, 0F);
                h.assertTrue(f.mob.distanceToSqr(f.player) > initialDistance,
                        "Following the wall toward the right opening first moves farther from the player");
                h.assertFalse(SourceMonsterTargeting.canSee(f.mob, f.player, false),
                        "The player is still hidden during the outward part of the detour");
                for (int tick = 0; tick < 40; ++tick) goal.tick();
                h.assertTrue(f.mob.getNavigation().getPath() == original && !f.mob.getNavigation().isDone(),
                        "Repeated melee replans preserve the unfinished valid side route while its early nodes move away");
                f.mob.getNavigation().stop();
                h.assertTrue(goal.canContinueToUse(),
                        "An unseen player is retained when a partial or interrupted movement route ends");
                for (int tick = 0; tick < 15; ++tick) goal.tick();
                assertStairRoute(h, f, f.mob.getNavigation().getPath());
                goal.stop();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void movingPlayerInvalidatesAnUnfinishedAttackPositionRoute(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.WITHER_SKELETON)) {
                SourceMeleeGoal goal = f.melee();
                h.assertTrue(goal.canUse(), "The first perched position has a real melee route");
                goal.start();
                Path original = f.mob.getNavigation().getPath();
                assertStairRoute(h, f, original);
                Vec3 oldEnd = MeleePursuitPaths.standingPoint(f.mob, original, original.getNodeCount() - 1);
                f.perch(4);
                h.assertFalse(SourceMeleeGoal.canStrikeFrom(f.mob, f.player, oldEnd),
                        "The old standing position cannot attack the moved player");
                for (int tick = 0; tick < 15; ++tick) goal.tick();
                Path replacement = f.mob.getNavigation().getPath();
                h.assertTrue(replacement != original, "Player movement invalidates the active route before arrival");
                assertStairRoute(h, f, replacement);
                goal.stop();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void inaccessibleLedgeIsNotMistakenForACompleteWalkingAlternative(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.ZOMBIE)) {
                f.stairs(false);
                var plan = f.zombiePlan();
                h.assertTrue(!plan.completeWalkingRoute() && !plan.choosesBreaking(),
                        "Removing the only stairs leaves no attack position reachable from the ground");
                h.assertTrue(plan.path() == null || !SourceMeleeGoal.canStrikeFrom(f.mob, f.player,
                                MeleePursuitPaths.standingPoint(f.mob, plan.path(), plan.path().getNodeCount() - 1)),
                        "A partial native path below the ledge is not advertised as a usable attack endpoint");
                Vec3 throughWall = new Vec3(f.origin.getX() + .5D, f.origin.getY() + 3.5D, f.origin.getZ() + .5D);
                f.player.moveTo(f.origin.getX() + 2D, f.origin.getY() + 3.5D, f.origin.getZ() + .5D, 0F, 0F);
                h.assertFalse(SourceMeleeGoal.canStrikeFrom(f.mob, f.player, throughWall),
                        "Even a point inside numeric melee range cannot strike through the opaque wall");
                f.assertTerrainIntact();
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void nativeStandingPointFitsTheRealHalfSlabFloorUnderALowCeiling(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            try (Fixture f = new Fixture(h, EntityType.ZOMBIE)) {
                BlockPos floor = f.origin.offset(0, 0, 10);
                h.getLevel().setBlock(floor, Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
                h.getLevel().setBlock(floor.above(2), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 2);
                Path path = new Path(List.of(new Node(floor.getX(), floor.getY() + 1, floor.getZ())), floor.above(), true);
                Vec3 raw = path.getEntityPosAtNode(f.mob, 0);
                Vec3 standing = MeleePursuitPaths.standingPoint(f.mob, path, 0);
                h.assertTrue(standing.y == floor.getY() + .5D,
                        "Native standing height follows the bottom slab's half-block floor");
                h.assertFalse(h.getLevel().noCollision(f.mob, f.mob.getBoundingBox().move(raw.subtract(f.mob.position())).deflate(.001D)),
                        "The integer node position would put the zombie's head inside the upper slab ceiling");
                AABB body = f.mob.getBoundingBox().move(standing.subtract(f.mob.position())).deflate(.001D);
                h.assertTrue(h.getLevel().noCollision(f.mob, body) && !h.getLevel().noCollision(f.mob, body.move(0D, -.0625D, 0D)),
                        "The actual native floor position fits below the ceiling and remains physically supported");
            }
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nearbyPlayerCanRequireMoreThanFortyEightBlocksOfWalking(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            BlockPos origin = new BlockPos(390_000 + IDS.getAndIncrement() * 96,
                    h.absolutePos(BlockPos.ZERO).getY() + 12, 390_000);
            for (int x = -4; x <= 12; ++x) for (int z = -30; z <= 30; ++z)
                for (int y = -4; y <= 7; ++y) h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == -1 || y == 7 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            for (int z = -27; z <= 27; ++z) for (int y = 0; y <= 5; ++y)
                h.getLevel().setBlock(origin.offset(2, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            PathfinderMob mob = EntityType.WITHER_SKELETON.create(h.getLevel());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            h.getLevel().addFreshEntity(mob);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            mob.setTarget(player);
            List<ChunkPos> chunks = new ArrayList<>();
            ChunkPos low = new ChunkPos(origin.offset(-4, 0, -30)), high = new ChunkPos(origin.offset(12, 0, 30));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, mob.getId());
            }
            boolean[] closed = {false};
            Runnable cleanup = () -> {
                if (closed[0]) return;
                closed[0] = true;
                mob.getNavigation().stop();
                mob.discard();
                player.discard();
                for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, mob.getId());
            };
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            });
            // Native regions take a getChunkNow snapshot; freshly loaded chunks must first become visible.
            h.startSequence().thenWaitUntil(() -> h.assertTrue(chunks.stream().allMatch(chunk ->
                            h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                    "Every part of the long wall and both openings is visible to native path regions")).thenExecute(() -> {
                        PathfindingTestSettings.runSynchronous(() -> {
                    try {
                    h.assertTrue(mob.distanceToSqr(player) == 64D,
                            "The player is nearby despite the long obstacle between the entities");
                    h.assertTrue(h.getLevel().getBlockState(origin.below()).is(Blocks.BEDROCK)
                                    && h.getLevel().noCollision(mob, mob.getBoundingBox()),
                            "The long-route fixture begins on a solid floor with room for the actual monster body");
                    Path raw192 = nativePlayerBlockPath(h, mob, player.blockPosition(), 0, 192F, 8192);
                    h.assertTrue(raw192 != null && raw192.canReach(),
                            "A fresh native query with the exploratory allowance confirms that the fixture itself is traversable: "
                                    + diagnostic(mob, player, raw192));
                    Path raw48 = nativePlayerBlockPath(h, mob, player.blockPosition(), 0, 48F, 2048);
                    h.assertTrue(raw48 == null || !raw48.canReach(),
                            "The same terrain cannot be completed by the direct forty-eight-block query: " + diagnostic(mob, player, raw48));
                    SourceMeleeGoal goal = SourceMeleeGoal.first(mob);
                    h.assertTrue(goal != null && goal.canUse(), "The installed non-zombie melee task starts the long detour");
                    goal.start();
                    Path path = mob.getNavigation().getPath();
                    h.assertTrue(path != null && path.canReach() && SourceMeleeGoal.canStrikeFrom(mob, player,
                                    MeleePursuitPaths.standingPoint(mob, path, path.getNodeCount() - 1)),
                            "The longer fallback budget finds a complete path to a real attack position; selected="
                                    + diagnostic(mob, player, path) + "; raw192=" + diagnostic(mob, player, raw192));
                    Vec3 previous = mob.position();
                    double length = 0D;
                    boolean aroundEnd = false;
                    for (int index = 0; index < path.getNodeCount(); ++index) {
                        Vec3 point = MeleePursuitPaths.standingPoint(mob, path, index);
                        length += previous.distanceTo(point);
                        previous = point;
                        var node = path.getNode(index);
                        int x = node.x - origin.getX(), z = node.z - origin.getZ();
                        aroundEnd |= Math.abs(z) >= 28;
                        h.assertFalse(x == 2 && Math.abs(z) <= 27,
                                "The complete route never traverses any of the wall's fifty-five cells");
                    }
                    h.assertTrue(aroundEnd && length > 56D && length <= 96D,
                            "Nearby pursuit uses the actual wall end and a real route longer than fifty-six blocks within the ninety-six-block route limit: " + length);
                    goal.stop();
                    } finally { cleanup.run(); }
                        });
                    }).thenSucceed();
        });
    }

    @GameTest(template = "empty", batch = "melee_detour_live", timeoutTicks = 800)
    public static void nativeZombieMovementActuallyFollowsTheOpeningAndClimbsTheStairs(GameTestHelper h) {
        Fixture f = new Fixture(h, EntityType.ZOMBIE);
        boolean[] walkedAway = {false}, usedOpening = {false}, climbed = {false};
        double[] firstDistance = {0D};
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { f.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { f.close(); }
        });
        f.requestTickingChunks();
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(f.chunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                    "The complete native walking area reaches entity-ticking status");
            h.assertTrue(h.getLevel().getEntity(f.mob.getUUID()) == f.mob,
                    "The zombie is indexed in its real ticking region before movement starts");
        }).thenExecute(() -> {
            SourceMeleeGoal goal = f.melee();
            f.mob.targetSelector.removeAllGoals(candidate -> true);
            f.mob.goalSelector.removeAllGoals(candidate -> candidate != goal);
            f.mob.setNoGravity(false);
            f.mob.setDeltaMovement(0D, -.08D, 0D);
            f.mob.setOnGround(true);
            f.mob.setNoAi(false);
            firstDistance[0] = f.mob.distanceToSqr(f.player);
        }).thenWaitUntil(() -> {
            // Let native world ticks service async path callbacks and then perform actual entity travel.
            walkedAway[0] |= f.mob.getX() < f.origin.getX() + 1D && f.mob.distanceToSqr(f.player) > firstDistance[0] + 1D;
            usedOpening[0] |= f.mob.getX() >= f.origin.getX() + 2D && f.mob.getZ() >= f.origin.getZ() + 6D;
            climbed[0] |= f.mob.getY() >= f.origin.getY() + 2.75D;
            h.assertTrue(walkedAway[0] && usedOpening[0] && climbed[0],
                    "Native movement first retreats along the wall, crosses its right opening and climbs real stairs: " + f.mob.position());
            h.assertTrue(SourceMeleeGoal.canStrike(f.mob, f.player),
                    "The full installed melee AI reaches an actual physical attack position beside the perched player: " + f.mob.position());
        }).thenExecute(() -> {
            SourceMeleeGoal goal = f.melee();
            goal.tick();
            h.assertTrue(goal.attackCooldown() > 0, "Reaching the ledge enters the real melee attack cadence");
            f.assertTerrainIntact();
        }).thenSucceed();
    }

    private static void assertStairRoute(GameTestHelper h, Fixture f, Path path) {
        h.assertTrue(path != null && path.canReach() && path.getNodeCount() > 1,
                "A complete native route reaches a separate attack standing position");
        Vec3 end = MeleePursuitPaths.standingPoint(f.mob, path, path.getNodeCount() - 1);
        h.assertTrue(SourceMeleeGoal.canStrikeFrom(f.mob, f.player, end),
                "The selected endpoint satisfies the real weapon distance and physical attack rays");
        AABB endpointBody = f.mob.getBoundingBox().move(end.subtract(f.mob.position())).deflate(.001D);
        h.assertTrue(h.getLevel().noCollision(f.mob, endpointBody)
                        && !h.getLevel().noCollision(f.mob, endpointBody.move(0D, -.0625D, 0D)),
                "The attack endpoint fits the monster and has solid support, including partial-height fence support: " + end);
        boolean opening = false, intermediateStair = false;
        for (int index = 0; index < path.getNodeCount(); ++index) {
            var node = path.getNode(index);
            int x = node.x - f.origin.getX(), y = node.y - f.origin.getY(), z = node.z - f.origin.getZ();
            opening |= x == 1 && z >= 6;
            intermediateStair |= x >= 6 && x <= 8 && z >= 7 && z <= 8 && y >= 1 && y <= 2;
            h.assertFalse(x == 1 && z <= 5 && z >= -16 && y <= 5,
                    "The real path never traverses a cell inside the opaque wall");
            h.assertTrue(h.getLevel().noCollision(f.mob, f.mob.getBoundingBox().move(
                            MeleePursuitPaths.standingPoint(f.mob, path, index).subtract(f.mob.position())).deflate(.001D)),
                    "Every selected standing node fits the actual monster body");
        }
        h.assertTrue(opening && intermediateStair, "Actual path nodes use both the right opening and intermediate stair heights");
    }

    private static Path nativePlayerBlockPath(GameTestHelper helper, PathfinderMob mob, BlockPos target,
            int accuracy, float length, int maxNodes) {
        BlockPos start = mob.blockPosition();
        PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(), start.offset(-56, -56, -56),
                start.offset(56, 56, 56)) {
            @Override public BlockState getBlockState(BlockPos pos) {
                return !helper.getLevel().hasChunkAt(pos) || isOutsideBuildHeight(pos)
                        ? Blocks.BEDROCK.defaultBlockState() : super.getBlockState(pos);
            }
        };
        WalkNodeEvaluator nodes = new WalkNodeEvaluator();
        nodes.setCanOpenDoors(false);
        nodes.setCanPassDoors(true);
        nodes.setCanFloat(mob.getNavigation().getNodeEvaluator().canFloat());
        return new PathFinder(nodes, maxNodes).findPath(region, mob, Set.of(target), length, accuracy, 1F);
    }

    private static String diagnostic(PathfinderMob mob, Player player, Path path) {
        if (path == null) return "null path; mob=" + mob.position() + "; player=" + player.position();
        Vec3 end = MeleePursuitPaths.standingPoint(mob, path, path.getNodeCount() - 1);
        return "nodes=" + path.getNodeCount() + ", canReach=" + path.canReach() + ", target=" + path.getTarget()
                + ", end=" + end + ", distance=" + end.distanceTo(player.position())
                + ", canStrike=" + SourceMeleeGoal.canStrikeFrom(mob, player, end)
                + ", bodyFits=" + mob.level().noCollision(mob, mob.getBoundingBox().move(end.subtract(mob.position())).deflate(.001D));
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final PathfinderMob mob;
        final Player player;
        final List<ChunkPos> chunks = new ArrayList<>();
        final int ticketKey;
        boolean closed;

        Fixture(GameTestHelper h, EntityType<? extends PathfinderMob> type) {
            helper = h;
            ticketKey = IDS.getAndIncrement();
            origin = new BlockPos(380_000 + ticketKey * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 380_000);
            for (int x = -6; x <= 14; ++x) for (int z = -16; z <= 12; ++z) {
                for (int y = -4; y <= 7; ++y) h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == -1 || y == 7 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            for (int z = -16; z <= 5; ++z) for (int y = 0; y <= 5; ++y)
                h.getLevel().setBlock(origin.offset(1, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            for (int x = 6; x <= 9; ++x) for (int z = 0; z <= 5; ++z) for (int y = 0; y <= 2; ++y)
                h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            stairs(true);
            mob = type.create(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setOnGround(true);
            mob.setPersistenceRequired();
            mob.setCanPickUpLoot(false);
            mob.getPersistentData().putBoolean(R196MonsterAi.SMART, false);
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            h.getLevel().addFreshEntity(mob);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            perch(0);
            mob.setTarget(player);
        }

        void stairs(boolean present) {
            for (int x = 6; x <= 8; ++x) for (int z = 6; z <= 8; ++z) for (int y = 0; y < 9 - z; ++y)
                helper.getLevel().setBlock(origin.offset(x, y, z), (present ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
        }

        void perch(int z) {
            BlockPos post = origin.offset(8, 2, z);
            helper.getLevel().setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 2);
            // Lower cardinal platforms leave a diagonal approach beside the raised fence top.
            for (Direction side : Direction.Plane.HORIZONTAL)
                helper.getLevel().setBlock(post.relative(side), Blocks.AIR.defaultBlockState(), 2);
            player.moveTo(origin.getX() + 8.3D, origin.getY() + 3.5D, origin.getZ() + z + .7D, 0F, 0F);
        }

        SourceMeleeGoal melee() {
            SourceMeleeGoal goal = SourceMeleeGoal.first(mob);
            helper.assertTrue(goal != null, "Joining the native species installs its source melee goal");
            return goal;
        }

        ZombieRoutePlanner.Plan zombiePlan() {
            R196DiggerGoal digger = R196DiggerGoal.first(mob);
            helper.assertTrue(digger != null, "The native zombie owns its installed pursuit planner");
            return digger.pursuitPlan();
        }

        void assertTerrainIntact() {
            for (int z = -16; z <= 5; ++z) for (int y = 0; y <= 5; ++y)
                helper.assertTrue(helper.getLevel().getBlockState(origin.offset(1, y, z)).is(Blocks.BEDROCK),
                        "Pursuit preserves the wall while seeking its opening");
            helper.assertTrue(helper.getLevel().getBlockState(origin.offset(8, 2, 0)).is(Blocks.OAK_FENCE),
                    "The player perch remains intact");
        }

        void requestTickingChunks() {
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -16)), high = new ChunkPos(origin.offset(14, 0, 12));
            for (int x = low.x; x <= high.x; ++x) for (int z = low.z; z <= high.z; ++z) {
                ChunkPos chunk = new ChunkPos(x, z);
                chunks.add(chunk);
                helper.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            mob.getNavigation().stop();
            mob.discard();
            player.discard();
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}
