package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.R196DiggerGoal;
import io.github.ningmeng123.mite.entity.SourceMeleeGoal;
import io.github.ningmeng123.mite.entity.VanillaMeleePursuit;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Existing native goals use real loaded-terrain detours without replacing their attacks. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class VanillaMeleePursuitGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_vanilla_melee_test", Integer::compareTo);

    private VanillaMeleePursuitGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void vindicatorKeepsItsNativeGoalAndUsesACompleteHiddenSideRoute(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                MeleeAttackGoal nativeGoal = f.melee();
                h.assertTrue(nativeGoal.getClass() == MeleeAttackGoal.class && SourceMeleeGoal.first(f.mob) == null,
                        "The vindicator still owns its original native melee goal");
                h.assertTrue(R196DiggerGoal.first(f.mob) == null, "Walking support does not grant zombie digging");
                h.assertFalse(f.mob.getSensing().hasLineOfSight(f.player), "The divider initially hides the already assigned player");
                h.assertTrue(nativeGoal.canUse(), "The installed native goal accepts the bounded detour");
                nativeGoal.start(); nativeGoal.tick();
                f.assertRoute();
                h.assertTrue(f.melee() == nativeGoal, "Planning did not replace the native goal or its priority");
                f.mob.getNavigation().tick();
                h.assertTrue(Math.abs(f.mob.getMoveControl().getSpeedModifier() - 1D) < .00001D,
                        "The native vindicator speed modifier is retained");
                f.mob.getNavigation().stop();
                h.assertTrue(nativeGoal.canContinueToUse(), "A temporarily ended unseen route can be replanned");
                for (int tick = 0; tick < 11; tick++) nativeGoal.tick();
                f.assertRoute();
                nativeGoal.stop();
                h.assertFalse(VanillaMeleePursuit.followsReachableRoute(f.mob, f.player), "Stopping the actual melee goal ends its route memory");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "vanilla_melee_live")
    public static void nativeVindicatorMovementActuallyRoundsTheWallAndAttacks(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                MeleeAttackGoal nativeGoal = f.melee();
                f.mob.targetSelector.removeAllGoals(goal -> true);
                f.mob.goalSelector.removeAllGoals(goal -> goal != nativeGoal);
                f.mob.setNoAi(false); f.mob.setNoGravity(false);
                f.mob.setDeltaMovement(0D, -.08D, 0D); f.mob.setOnGround(true);
                float health = f.player.getHealth();
                boolean movedAway = false, passedOpening = false;
                double distance = f.mob.distanceToSqr(f.player);
                for (int tick = 0; tick < 600 && f.player.getHealth() == health; tick++) {
                    h.getLevel().tickNonPassenger(f.mob);
                    movedAway |= f.mob.getX() < f.origin.getX() + 2D && f.mob.distanceToSqr(f.player) > distance + 1D;
                    passedOpening |= f.mob.getX() >= f.origin.getX() + 2D && f.mob.getZ() > f.origin.getZ() + 5D;
                }
                h.assertTrue(movedAway && passedOpening,
                        "Full native mob ticks first move away from the target, then pass the wall opening: " + f.mob.position());
                h.assertTrue(f.player.getHealth() < health,
                        "The original installed attack goal reaches and damages the player after following the detour");
                f.assertWallIntact();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void nativeAttackCooldownAndOpaqueReachRemainNative(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                f.wall(false);
                f.player.moveTo(f.mob.getX() + 1.25D, f.mob.getY(), f.mob.getZ(), 0F, 0F);
                f.mob.getSensing().tick();
                MeleeAttackGoal nativeGoal = f.melee();
                h.assertTrue(nativeGoal.canUse(), "The installed unarmed native goal starts within its normal reach");
                nativeGoal.start();
                float health = f.player.getHealth();
                nativeGoal.tick();
                float afterFirst = f.player.getHealth();
                h.assertTrue(afterFirst < health, "The native first attack still hits immediately");
                for (int tick = 0; tick < 19; tick++) { f.player.invulnerableTime = 0; nativeGoal.tick(); }
                h.assertValueEqual(f.player.getHealth(), afterFirst, "Navigation bridging does not shorten the native twenty-tick attack cooldown");
                f.player.invulnerableTime = 0; nativeGoal.tick();
                h.assertTrue(f.player.getHealth() < afterFirst, "The next native attack still arrives after twenty ticks");
                for (int y = 0; y < 3; y++) h.getLevel().setBlock(f.origin.offset(1, y, 0), Blocks.GLASS.defaultBlockState(), 2);
                f.mob.getSensing().tick();
                h.assertFalse(VanillaMeleePursuit.canStrikeFrom(f.mob, f.player, f.mob.position()), "A planned native attack cannot pass through glass");
                float blocked = f.player.getHealth();
                for (int tick = 0; tick < 25; tick++) { f.player.invulnerableTime = 0; nativeGoal.tick(); }
                h.assertValueEqual(f.player.getHealth(), blocked, "The real native attack also respects the opaque collider");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void ravagerDetourFitsItsWideBodyAndNativeAttackVolume(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.RAVAGER);
            f.run(() -> {
                MeleeAttackGoal nativeGoal = f.melee();
                h.assertTrue(nativeGoal.canUse(), "The native ravager goal finds a route sized for its body");
                nativeGoal.start(); nativeGoal.tick();
                Path path = f.assertRoute();
                Vec3 origin = f.mob.position();
                Vec3 endpoint = MeleePursuitPaths.standingPoint(f.mob, path, path.getNodeCount() - 1);
                f.mob.setPos(endpoint); f.mob.getSensing().tick();
                h.assertTrue(f.mob.isWithinMeleeAttackRange(f.player) && f.mob.getSensing().hasLineOfSight(f.player),
                        "The selected endpoint agrees with the ravager's actual overridden attack volume");
                f.mob.setPos(origin);
                h.assertTrue(f.mob.getBbWidth() > 1.9F && f.melee() == nativeGoal,
                        "The wide native species and its original controller are preserved");
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void replacingThePlayerAndEndpointModeCannotReuseAnOldPursuit(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                MeleeAttackGoal nativeGoal = f.melee();
                h.assertTrue(nativeGoal.canUse(), "The first assigned player has a complete native melee route");
                nativeGoal.start(); nativeGoal.tick();
                Path old = f.assertRoute();
                Player replacement = h.makeMockPlayer(GameType.SURVIVAL);
                try {
                    replacement.moveTo(Vec3.atBottomCenterOf(f.origin.west(4)));
                    f.mob.setTarget(replacement);
                    nativeGoal.tick();
                    Path updated = f.mob.getNavigation().getPath();
                    h.assertTrue(updated != old && MeleePursuitPaths.reachesVanilla(f.mob, updated, replacement),
                            "A different player immediately replaces the old route rather than following its endpoint");
                    h.assertFalse(VanillaMeleePursuit.followsReachableRoute(f.mob, f.player), "Old player memory cannot borrow the replacement player's route");
                    f.mob.getNavigation().stop();
                    MeleePursuitPaths.find(f.mob, replacement, null);
                    long sourceSearches = MeleePursuitPaths.searchStats(f.mob).searches();
                    MeleePursuitPaths.findVanilla(f.mob, replacement, null);
                    h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > sourceSearches,
                            "Native attack-volume planning cannot borrow the source endpoint mode's complete cache");
                    long nativeSearches = MeleePursuitPaths.searchStats(f.mob).searches();
                    MeleePursuitPaths.find(f.mob, replacement, null);
                    h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > nativeSearches,
                            "Source planning cannot borrow the native endpoint mode's complete cache");
                } finally { replacement.discard(); }
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void raidVindicatorRetainsItsRealOpenDoorGoalAndDoorPolicyInvalidatesRoutes(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                BlockPos door = f.origin.east(2);
                var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, false);
                h.getLevel().setBlock(door, lower, 2);
                h.getLevel().setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
                ((Vindicator)f.mob).setCurrentRaid(new Raid(900_000 + f.key, h.getLevel(), f.origin));
                var evaluator = f.mob.getNavigation().getNodeEvaluator();
                evaluator.setCanOpenDoors(true); evaluator.setCanPassDoors(true);
                MeleeAttackGoal nativeGoal = f.melee();
                h.assertTrue(nativeGoal.canUse(), "An active-raid vindicator retains enhanced pursuit when its native open-door flag is enabled");
                nativeGoal.start(); nativeGoal.tick();
                Path route = f.mob.getNavigation().getPath();
                h.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(f.mob, route, f.player),
                        "A complete native walking route accepts the real open shape of the closed wooden doorway");
                int doorIndex = -1;
                for (int i = 0; i < route.getNodeCount(); i++)
                    if (route.getNode(i).asBlockPos().equals(door)) doorIndex = i;
                h.assertTrue(doorIndex >= 1, "The native path keeps its wooden-door node for DoorInteractGoal");
                h.assertFalse(h.getLevel().getBlockState(door).getValue(DoorBlock.OPEN), "Planning never changes the actual door");
                long searches = MeleePursuitPaths.searchStats(f.mob).searches();
                evaluator.setCanOpenDoors(false);
                Path withoutOpening = MeleePursuitPaths.findVanilla(f.mob, f.player, route);
                h.assertTrue(MeleePursuitPaths.searchStats(f.mob).searches() > searches
                                && MeleePursuitPaths.completeVanillaRouteClear(f.mob, withoutOpening, f.player),
                        "Changing the native open-door flag invalidates the active cache and finds the real side opening");
                for (int i = 0; i < withoutOpening.getNodeCount(); i++)
                    h.assertFalse(withoutOpening.getNode(i).asBlockPos().equals(door), "A mob without opening permission cannot reuse a closed-door route");
                evaluator.setCanOpenDoors(true);
                route = MeleePursuitPaths.findVanilla(f.mob, f.player, null);
                f.mob.getNavigation().moveTo(route, 1D);
                route = f.mob.getNavigation().getPath();
                doorIndex = -1;
                for (int i = 0; i < route.getNodeCount(); i++) if (route.getNode(i).asBlockPos().equals(door)) doorIndex = i;
                h.assertTrue(doorIndex >= 1, "Restoring native permission restores the shorter doorway route");
                OpenDoorGoal opener = f.mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                        .filter(OpenDoorGoal.class::isInstance).map(OpenDoorGoal.class::cast).findFirst().orElse(null);
                h.assertTrue(opener != null && ((Vindicator)f.mob).hasActiveRaid(), "The real active-raid open-door goal remains registered");
                f.mob.setNoAi(false); f.mob.setNoGravity(false);
                boolean collided = false;
                for (int tick = 0; tick < 160 && !opener.canUse(); tick++) {
                    nativeGoal.tick();
                    f.mob.getNavigation().tick();
                    f.mob.getMoveControl().tick();
                    f.mob.getJumpControl().tick();
                    f.mob.travel(new Vec3(f.mob.xxa, f.mob.yya, f.mob.zza));
                    collided |= f.mob.horizontalCollision;
                }
                route = f.mob.getNavigation().getPath();
                h.assertTrue(collided && opener.canUse(),
                        "Real navigation advances its actual door node and collision travel activates the original raid opener: position="
                                + f.mob.position() + ", next=" + (route == null ? -1 : route.getNextNodeIndex())
                                + ", collision=" + f.mob.horizontalCollision + ", opens=" + evaluator.canOpenDoors()
                                + ", passes=" + evaluator.canPassDoors());
                opener.start();
                h.assertTrue(h.getLevel().getBlockState(door).getValue(DoorBlock.OPEN)
                                && h.getLevel().getBlockState(door.above()).getValue(DoorBlock.OPEN),
                        "The actual native raid goal opens both halves of the wooden door");
                evaluator.setCanPassDoors(false);
                h.assertFalse(MeleePursuitPaths.completeVanillaRouteClear(f.mob, route, f.player),
                        "Even an open door cannot justify route memory after native passage permission is disabled");
                evaluator.setCanPassDoors(true);
                opener.stop();
                var iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, false);
                h.getLevel().setBlock(door, iron, 2);
                h.getLevel().setBlock(door.above(), iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
                Path ironRoute = MeleePursuitPaths.findVanilla(f.mob, f.player, route);
                h.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(f.mob, ironRoute, f.player), "The iron door still leaves the real side detour");
                for (int i = 0; i < ironRoute.getNodeCount(); i++)
                    h.assertFalse(ironRoute.getNode(i).asBlockPos().equals(door), "Opening permission never treats an iron door as wood");
                h.getLevel().setBlock(door, lower, 2);
                h.getLevel().setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
                var ravager = EntityType.RAVAGER.create(h.getLevel());
                try {
                    ravager.moveTo(Vec3.atBottomCenterOf(f.origin));
                    ravager.getNavigation().getNodeEvaluator().setCanOpenDoors(true);
                    Path wideRoute = MeleePursuitPaths.findVanilla(ravager, f.player, null);
                    h.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(ravager, wideRoute, f.player), "A wide ravager uses the side opening rather than the narrow door frame");
                    for (int i = 0; i < wideRoute.getNodeCount(); i++)
                        h.assertFalse(wideRoute.getNode(i).asBlockPos().equals(door), "Assuming a door is open never removes its frame or widens the opening");
                } finally { ravager.discard(); }
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void animalNeutralRangedFlyingAndNonPlayerBehaviorsStayOutsideTheBridge(GameTestHelper h) {
        PathfindingTestSettings.runSynchronous(() -> {
            Fixture f = new Fixture(h, EntityType.VINDICATOR);
            f.run(() -> {
                for (EntityType<? extends PathfinderMob> type : List.of(EntityType.COW, EntityType.WOLF,
                        EntityType.ENDERMAN, EntityType.ZOMBIFIED_PIGLIN, EntityType.VEX, EntityType.GUARDIAN,
                        EntityType.PILLAGER, EntityType.ZOMBIE)) {
                    PathfinderMob other = type.create(h.getLevel());
                    try { h.assertFalse(VanillaMeleePursuit.supports(other), "The native bridge excludes " + type); }
                    finally { other.discard(); }
                }
                Drowned drowned = EntityType.DROWNED.create(h.getLevel());
                try {
                    drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                    h.assertFalse(VanillaMeleePursuit.supports(drowned), "A trident drowned keeps its native ranged pursuit");
                } finally { drowned.discard(); }
                var villager = EntityType.VILLAGER.create(h.getLevel());
                try { h.assertFalse(VanillaMeleePursuit.applies(f.mob, villager), "Native village and other non-player targets retain their original behavior"); }
                finally { villager.discard(); }
                Player creative = h.makeMockPlayer(GameType.CREATIVE);
                try { h.assertFalse(VanillaMeleePursuit.applies(f.mob, creative), "Creative players do not activate native hostile pursuit"); }
                finally { creative.discard(); }
                h.assertFalse(VanillaMeleePursuit.followsReachableRoute(f.mob, null), "An absent target cannot extend memory");
            });
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos origin;
        final PathfinderMob mob;
        final Player player;
        final int key = IDS.getAndIncrement();
        final List<ChunkPos> chunks = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h, EntityType<? extends PathfinderMob> type) {
            helper = h;
            origin = new BlockPos(525_000 + key * 96, h.absolutePos(BlockPos.ZERO).getY() + 12, 525_000);
            for (int x = -6; x <= 14; x++) for (int z = -15; z <= 15; z++) for (int y = -1; y <= 6; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 || y == 6 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            wall(true);
            mob = type.create(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));
            mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true); mob.setPersistenceRequired();
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            h.getLevel().addFreshEntity(mob);
            player = h.makeMockPlayer(GameType.SURVIVAL);
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100D); player.setHealth(100F);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));
            mob.setTarget(player);
            ChunkPos low = new ChunkPos(origin.offset(-6, 0, -15)), high = new ChunkPos(origin.offset(14, 0, 15));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
            }
            // Native entity-ticking promotion waits for a two-chunk FULL border around each room chunk.
            // Resolve those generation dependencies before the accelerated test clock begins waiting.
            for (int x = low.x - 2; x <= high.x + 2; x++) for (int z = low.z - 2; z <= high.z + 2; z++)
                h.getLevel().getChunk(x, z);
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        void run(Runnable action) {
            helper.startSequence().thenWaitUntil(() -> {
                // Publish completed native chunk work before checking real ticking/index state.
                // Each wait attempt stays bounded so unrelated server tasks still make progress.
                for (int tasks = 0; tasks < 64 && helper.getLevel().getChunkSource().pollTask(); tasks++) {}
                for (ChunkPos chunk : chunks) helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                                && helper.getLevel().isPositionEntityTicking(chunk.getWorldPosition()),
                        "Prepared remote terrain is visible and entity-ticking for native navigation: " + chunk
                                + ", holder=" + helper.getLevel().getChunkSource().chunkMap.getChunkDebugData(chunk));
                helper.assertTrue(helper.getLevel().getEntity(mob.getUUID()) == mob, "The native mob is indexed before goal execution");
            }).thenExecute(() -> { try { PathfindingTestSettings.runSynchronous(action); } finally { close(); } }).thenSucceed();
        }

        void wall(boolean present) {
            for (int z = -15; z <= 5; z++) for (int y = 0; y <= 5; y++)
                helper.getLevel().setBlock(origin.offset(2, y, z), (present ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
        }

        MeleeAttackGoal melee() {
            var goal = mob.goalSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(MeleeAttackGoal.class::isInstance).map(MeleeAttackGoal.class::cast).findFirst().orElse(null);
            helper.assertTrue(goal != null, "The native species already registered a melee attack controller");
            return goal;
        }

        Path assertRoute() {
            Path route = mob.getNavigation().getPath();
            helper.assertTrue(MeleePursuitPaths.completeVanillaRouteClear(mob, route, player),
                    "The actual native navigation route is complete, supported and ends within native attack reach");
            boolean opening = false;
            for (int i = 0; i < route.getNodeCount(); i++) {
                Vec3 point = MeleePursuitPaths.standingPoint(mob, route, i);
                opening |= point.x >= origin.getX() + 2D && point.z > origin.getZ() + 5D;
                helper.assertTrue(helper.getLevel().noCollision(mob, mob.getBoundingBox().move(point.subtract(mob.position())).deflate(.001D)),
                        "Every accepted route point fits the real species body");
            }
            helper.assertTrue(opening, "The actual route passes the side opening rather than crossing the wall");
            assertWallIntact();
            return route;
        }

        void assertWallIntact() {
            for (int z = -15; z <= 5; z++) for (int y = 0; y <= 5; y++)
                helper.assertTrue(helper.getLevel().getBlockState(origin.offset(2, y, z)).is(Blocks.BEDROCK), "Pursuit leaves the wall intact");
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            VanillaMeleePursuit.clear(mob); mob.getNavigation().stop(); mob.discard(); player.discard();
            for (ChunkPos chunk : chunks) helper.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
        }
    }
}
