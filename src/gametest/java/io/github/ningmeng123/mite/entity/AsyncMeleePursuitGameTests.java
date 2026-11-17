package io.github.ningmeng123.mite.entity;

import io.github.ningmeng123.mite.config.MiteConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Enabled-only real pursuit checks; synchronous behavior regressions run separately. @author NingMeng123 */
@GameTestHolder("mite_async_paths")
@PrefixGameTestTemplate(false)
public final class AsyncMeleePursuitGameTests {
    private static final AtomicInteger IDS=new AtomicInteger();
    private static final TicketType<Integer> TICKET=TicketType.create("mite_async_pursuit_test",Integer::compareTo);

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_wait",timeoutTicks=600)
    public static void pendingWalkingIsSharedWithoutFailureBackoffOrLegacyMining(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            // Native grounded mobs retain their downward gravity velocity while standing.
            // The standing eligibility check must not mistake that vector for real motion.
            f.mob.setDeltaMovement(0D,-.0784D,0D);
            var first=f.digger.pursuitPlan();
            h.assertTrue(MeleePursuitPaths.pending(f.mob) && !first.choosesBreaking(),
                    "The real planner waits for walking before selecting any destructive shortcut");
            long searches=MeleePursuitPaths.searchStats(f.mob).searches();
            var task=AsyncMeleePursuit.state(f.mob).task;
            for(int i=0;i<12;i++) {
                h.assertFalse(f.digger.canUse(),"Pending walking cannot fall through to legacy mining");
                f.melee.canUse();
                h.assertFalse(new SourceBreakPortalGoal(f.mob).canUse(),"Pending walking cannot start portal pounding");
            }
            var stats=MeleePursuitPaths.searchStats(f.mob);
            h.assertTrue(stats.failures()==0 && stats.retryAt()==Long.MIN_VALUE,
                    "Waiting never creates a failed search or a failure retry deadline");
            h.assertValueEqual(stats.searches(),searches,"All pursuit entrypoints share one in-flight native query");
            h.assertTrue(AsyncMeleePursuit.state(f.mob).task==task,"Normal goal checks preserve the original task");
            f.awaitPath(() -> f.digger.pursuitPlan().path(),path -> {
                h.assertTrue(MeleePursuitPaths.completeRouteClear(f.mob,path,f.player),"The consumed live route is fully valid");
                h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).failures(),0,"Successful waiting never becomes failure backoff");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_detour",timeoutTicks=800)
    public static void longWallOpenEndDetourRetainsAllNativeSearchStagesAndFinalRoute(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.longWall();
        f.ready(() -> {
            long before=MeleePursuitPaths.searchStats(f.mob).searches();
            Path expected=synchronously(f::find);
            long nativeStages=MeleePursuitPaths.searchStats(f.mob).searches()-before;
            h.assertTrue(nativeStages>=2 && MeleePursuitPaths.completeRouteClear(f.mob,expected,f.player),
                    "The proven long-wall fixture needs the larger detour search: stages="+nativeStages+", path="+describe(expected));
            MeleePursuitPaths.invalidate(f.mob);
            long asyncBefore=MeleePursuitPaths.searchStats(f.mob).searches();
            h.assertTrue(f.find()==null && MeleePursuitPaths.pending(f.mob),"The multi-stage route begins as a pending decision");
            f.awaitPath(f::find,path -> {
                h.assertTrue(path.sameAs(expected),"Async checkpoints preserve the original native detour node sequence");
                h.assertTrue(MeleePursuitPaths.completeRouteClear(f.mob,path,f.player),"The entire current detour supports the mob and reaches attack range");
                h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).searches()-asyncBefore,nativeStages,
                        "Polling reuses completed direct/exact stages instead of submitting them again");
                h.assertTrue(h.getLevel().getBlockState(f.origin.offset(2,0,0)).is(Blocks.BEDROCK),"The wall remains intact");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_inputs",timeoutTicks=600)
    public static void replacementTargetEquipmentAndOriginChangesCancelObsoleteWorkAndRespondNatively(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            // Keep all three input-change fallbacks independent of the two-decision server budget.
            try (var settings = io.github.ningmeng123.mite.gametest.PathfindingTestSettings.unboundedAsync()) {
                f.find();var oldTargetTask=AsyncMeleePursuit.state(f.mob).task;
                Player replacement=h.makeMockPlayer(GameType.SURVIVAL);
                f.players.add(replacement);replacement.moveTo(Vec3.atBottomCenterOf(f.origin.east(10)));
                f.player=replacement;f.mob.setTarget(replacement);
                h.assertTrue(MeleePursuitPaths.reaches(f.mob,f.find(),replacement) && !MeleePursuitPaths.pending(f.mob),
                        "A replacement target receives a fresh synchronous response rather than an old task's path");
                h.assertTrue(oldTargetTask.cancelled(),"The old target's task is cancelled");
                MeleePursuitPaths.invalidate(f.mob);f.find();var oldGearTask=AsyncMeleePursuit.state(f.mob).task;
                f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
                h.assertTrue(MeleePursuitPaths.reaches(f.mob,f.find(),replacement) && !MeleePursuitPaths.pending(f.mob),
                        "Weapon changes immediately re-evaluate physical attack endpoints");
                h.assertTrue(oldGearTask.cancelled(),"Equipment changes cancel the obsolete query");
                MeleePursuitPaths.invalidate(f.mob);f.find();var oldOriginTask=AsyncMeleePursuit.state(f.mob).task;
                f.mob.moveTo(f.mob.getX()+.25D,f.mob.getY(),f.mob.getZ(),0F,0F);
                h.assertTrue(MeleePursuitPaths.reaches(f.mob,f.find(),replacement) && !MeleePursuitPaths.pending(f.mob),
                        "Sub-block movement does not install a path computed from the old origin");
                h.assertTrue(oldOriginTask.cancelled(),"Movement cancels the previous origin's task");
                h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).failures(),0,"Input changes do not become failed pursuits");
                f.close();h.succeed();

            }
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_terrain",timeoutTicks=600)
    public static void openingTerrainAfterWorkerCompletionCannotCacheAStaleFailure(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.divider();
        f.ready(() -> {
            f.find();var state=AsyncMeleePursuit.state(f.mob);var task=state.task;
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                h.assertTrue(task.done(),"The original sealed-wall query finishes before opening the passage");
            }).thenExecute(() -> {
                h.assertTrue(task.failure()==null,"A real completed native query is available for stale-result validation");
                h.assertTrue(MeleePursuitPaths.pending(f.mob),"A finished worker remains pending until the full pursuit consumes it");
                h.getLevel().setBlock(f.origin.east(3),Blocks.AIR.defaultBlockState(),2);
                h.getLevel().setBlock(f.origin.east(3).above(),Blocks.AIR.defaultBlockState(),2);
                Path current=f.find();
                h.assertTrue(!MeleePursuitPaths.pending(f.mob) && MeleePursuitPaths.completeRouteClear(f.mob,current,f.player),
                        "The stale failure falls back to the newly opened real route: pending="+MeleePursuitPaths.pending(f.mob)
                                +", path="+describe(current)+", stats="+MeleePursuitPaths.searchStats(f.mob));
                h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).failures(),0,"Changed terrain cannot create false failure backoff");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_active_dig",timeoutTicks=600)
    public static void activeMiningKeepsWorkingWithoutResettingProgressOrCancellingItsWalkingQuery(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            BlockPos obstacle=f.origin.east();
            h.getLevel().setBlock(obstacle,Blocks.OAK_PLANKS.defaultBlockState(),2);
            f.mob.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_AXE));
            h.assertTrue(f.digger.setBlockToDig(obstacle,true),"The actual mining goal owns a nearby legal work block");
            f.digger.start();
            int progress=f.digger.progress(),cooldown=f.digger.cooldown();
            f.find();var task=AsyncMeleePursuit.state(f.mob).task;
            h.assertTrue(f.digger.canContinueToUse(),"Pending walking retains an already validated active work goal");
            f.digger.tick();
            h.assertValueEqual(f.digger.progress(),progress,"Pending navigation preserves mining progress");
            h.assertValueEqual(f.digger.cooldown(),cooldown-1,"Active mining counts down while the walking query is pending");
            h.assertTrue(AsyncMeleePursuit.state(f.mob).task==task,"The goal does not cancel its own shared walking task");
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                boolean continuing=f.digger.canContinueToUse();
                h.assertTrue(!MeleePursuitPaths.pending(f.mob) && !continuing,
                        "The active goal consumes the completed route and yields to real walking");
            }).thenExecute(() -> {
                h.assertTrue(h.getLevel().getBlockState(obstacle).is(Blocks.OAK_PLANKS),"Waiting and deciding to walk never breaks the old work block");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_region",timeoutTicks=600)
    public static void collisionQueriesMissingChunkTransitionsAndBlockEntitiesInvalidateDelayedResults(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            BlockPos sample=f.origin.east(2);
            var collisionRegion=new LoadedPathNavigationRegion(h.getLevel(),f.origin.offset(-4,-2,-4),f.origin.offset(12,8,4));
            h.getLevel().setBlock(sample,Blocks.STONE.defaultBlockState(),2);
            collisionRegion.getBlockCollisions(f.mob,new AABB(sample)).forEach(shape -> {});
            h.assertTrue(collisionRegion.terrainUnchanged(),"A native collision-only probe records current terrain");
            h.getLevel().setBlock(sample,Blocks.AIR.defaultBlockState(),2);
            h.assertFalse(collisionRegion.terrainUnchanged(),"Collision-only block reads also invalidate delayed failed paths");
            h.getLevel().setBlock(sample,Blocks.CHEST.defaultBlockState(),2);
            var dynamic=new LoadedPathNavigationRegion(h.getLevel(),sample,sample.offset(2,2,2));
            dynamic.getBlockState(sample);
            h.assertFalse(dynamic.terrainUnchanged(),"Queried block entities require the synchronous compatibility path");
            BlockPos missing=f.origin.offset(50_000,0,50_000);
            ChunkPos remote=new ChunkPos(missing);
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.x,remote.z)==null,"The remote terrain begins missing");
            var missingRegion=new LoadedPathNavigationRegion(h.getLevel(),missing,missing.offset(1,1,1));
            h.assertTrue(missingRegion.terrainUnchanged(),"Unchanged missing slots do not force a chunk load");
            h.getLevel().getChunk(remote.x,remote.z);
            h.assertFalse(missingRegion.terrainUnchanged(),"A previously missing slot becoming loaded invalidates the delayed query");
            f.close();h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_compat",timeoutTicks=600)
    public static void overriddenPathfindingCallbacksKeepTheirOriginalSynchronousLifecycle(GameTestHelper h) {
        Fixture f=new Fixture(h,true);
        f.ready(() -> {
            var custom=(CallbackZombie)f.mob;
            Path path=f.find();
            h.assertTrue(!MeleePursuitPaths.pending(f.mob) && MeleePursuitPaths.completeRouteClear(f.mob,path,f.player),
                    "A persistent callback override keeps native synchronous pathfinding ability");
            h.assertTrue(custom.starts>0 && custom.starts==custom.finishes,"All custom start/done hooks preserve their original synchronous lifetime");
            f.close();h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_scheduler",timeoutTicks=600)
    public static void realGoalSelectorReservesMovementWhileWaitingAndInstallsTheCompletedRoute(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            AlwaysReadyMove wander=installControlledScheduler(f);
            f.mob.goalSelector.tick();
            h.assertTrue(MeleePursuitPaths.pending(f.mob) && running(f,f.melee),
                    "The actual selector starts the melee MOVE/LOOK goal while its first route is pending");
            h.assertValueEqual(wander.starts,0,"A ready lower-priority movement goal cannot start during the decision");
            var task=AsyncMeleePursuit.state(f.mob).task;
            for(int i=0;i<8;i++) f.mob.goalSelector.tick();
            h.assertTrue(AsyncMeleePursuit.state(f.mob).task==task,"Scheduler ticks do not starve or replace the original task");
            h.assertValueEqual(wander.starts,0,"Waiting keeps MOVE reserved across repeated native selector checks");
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                f.mob.goalSelector.tick();
                h.assertTrue(!MeleePursuitPaths.pending(f.mob)
                                && MeleePursuitPaths.completeRouteClear(f.mob,f.mob.getNavigation().getPath(),f.player),
                        "The running goal consumes and installs the real completed route");
            }).thenExecute(() -> {
                f.mob.goalSelector.tick();
                h.assertTrue(running(f,f.melee),"A valid path preserves ordinary melee goal continuation");
                h.assertValueEqual(wander.starts,0,"A lower-priority goal never steals movement from the real pursuit");
                h.assertValueEqual(MeleePursuitPaths.searchStats(f.mob).failures(),0,"Waiting for MOVE does not fabricate path failures");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_scheduler_null",timeoutTicks=600)
    public static void pendingOnlyAdmissionReleasesMovementAfterARealNullFailure(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.ready(() -> {
            // Every returned route begins inside this real obstruction and must fail the
            // existing full-body path validator. The resulting high-level path is null.
            h.getLevel().setBlock(f.origin,Blocks.BEDROCK.defaultBlockState(),2);
            AlwaysReadyMove wander=installControlledScheduler(f);
            f.mob.goalSelector.tick();
            h.assertTrue(MeleePursuitPaths.pending(f.mob) && running(f,f.melee),
                    "A legitimate pending query temporarily owns movement even before a path exists");
            h.assertValueEqual(wander.starts,0,"The fallback movement goal waits for an actual path decision");
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                f.mob.goalSelector.tick();
                h.assertTrue(!MeleePursuitPaths.pending(f.mob) && MeleePursuitPaths.searchStats(f.mob).failures()>0,
                        "The native search and live validator finish with a real unsuccessful pursuit");
                // GoalSelector admits ready goals every third update. Exercise arbitration
                // after completion without changing world time or triggering a failure retry.
                for(int i=0;i<4;i++) f.mob.goalSelector.tick();
                h.assertTrue(!running(f,f.melee) && running(f,wander) && wander.starts==1,
                        "A goal admitted only for pending/null yields MOVE once no path or immediate attack exists");
            }).thenExecute(() -> {
                h.assertTrue(f.mob.getNavigation().getPath()==null && !SourceMeleeGoal.canStrike(f.mob,f.player),
                        "Movement release follows a genuine null path, not a valid or partial route");
                f.close();h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="mite_path_perf",batch="async_pursuit_scheduler_partial",timeoutTicks=600)
    public static void completedPartialRouteKeepsTheOriginalMeleeMovementBehavior(GameTestHelper h) {
        Fixture f=new Fixture(h,false);
        f.divider();
        f.ready(() -> {
            AlwaysReadyMove wander=installControlledScheduler(f);
            f.mob.goalSelector.tick();
            h.assertTrue(MeleePursuitPaths.pending(f.mob) && running(f,f.melee),"The real partial-route decision first reserves movement while pending");
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                f.mob.goalSelector.tick();
                Path partial=f.mob.getNavigation().getPath();
                h.assertTrue(!MeleePursuitPaths.pending(f.mob) && partial!=null && !partial.canReach()
                                && MeleePursuitPaths.searchStats(f.mob).failures()>0,
                        "The sealed divider produces the original usable partial route and real failure backoff");
            }).thenExecute(() -> {
                for(int i=0;i<4;i++) f.mob.goalSelector.tick();
                h.assertTrue(running(f,f.melee),"A non-null partial path retains ordinary melee continuation");
                h.assertValueEqual(wander.starts,0,"Null-result release does not discard an existing partial pursuit route");
                f.close();h.succeed();
            });
        });
    }

    private static AlwaysReadyMove installControlledScheduler(Fixture f) {
        f.mob.goalSelector.removeAllGoals(goal -> true);
        AlwaysReadyMove wander=new AlwaysReadyMove();
        f.mob.goalSelector.addGoal(2,f.melee);
        f.mob.goalSelector.addGoal(9,wander);
        return wander;
    }

    private static boolean running(Fixture f,Goal goal) {
        return f.mob.goalSelector.getAvailableGoals().stream().anyMatch(wrapped -> wrapped.getGoal()==goal && wrapped.isRunning());
    }

    private static final class AlwaysReadyMove extends Goal {
        int starts;
        AlwaysReadyMove() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return true; }
        @Override public boolean canContinueToUse() { return true; }
        @Override public void start() { starts++; }
    }

    private static <T> T synchronously(Supplier<T> action) {
        String previous=System.getProperty("mite.asyncPathfinding");
        System.setProperty("mite.asyncPathfinding","false");
        try { return action.get(); }
        finally { if(previous==null) System.clearProperty("mite.asyncPathfinding"); else System.setProperty("mite.asyncPathfinding",previous); }
    }

    private static String describe(Path path) {
        return path==null?"null":path+" reachable="+path.canReach()+" target="+path.getTarget()+" end="+path.getEndNode();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int id=IDS.getAndIncrement();
        final BlockPos origin;
        final Zombie mob;
        Player player;
        final R196DiggerGoal digger;
        final SourceMeleeGoal melee;
        final List<Player> players=new ArrayList<>();
        final List<ChunkPos> chunks=new ArrayList<>();
        final List<ChunkPos> tickingChunks=new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper h,boolean customCallbacks) {
            this.h=h;
            origin=new BlockPos(4_200_000+id*192,h.absolutePos(BlockPos.ZERO).getY()+12,4_200_000);
            for(int x=-6;x<=14;x++) for(int z=-16;z<=12;z++) for(int y=-1;y<=7;y++) {
                boolean solid=y==-1 || y==7 || x==-6 || x==14 || z==-16 || z==12;
                h.getLevel().setBlock(origin.offset(x,y,z),(solid?Blocks.BEDROCK:Blocks.AIR).defaultBlockState(),2);
            }
            mob=customCallbacks?new CallbackZombie(h.getLevel()):EntityType.ZOMBIE.create(h.getLevel());
            mob.moveTo(Vec3.atBottomCenterOf(origin));mob.setNoAi(true);mob.setNoGravity(true);mob.setOnGround(true);
            mob.setPersistenceRequired();mob.setCanPickUpLoot(false);mob.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
            R196NpcNeeds.join(new EntityJoinLevelEvent(mob,h.getLevel(),false));
            R196MonsterAi.join(new EntityJoinLevelEvent(mob,h.getLevel(),false));
            player=h.makeMockPlayer(GameType.SURVIVAL);players.add(player);
            player.moveTo(Vec3.atBottomCenterOf(origin.east(8)));mob.setTarget(player);
            digger=R196DiggerGoal.first(mob);melee=SourceMeleeGoal.first(mob);
            // Stabilize every chunk captured by the original loaded-only search region,
            // including its margin. Late neighboring FULL promotion is a legitimate stale
            // result but must not obscure this test's strict native-stage reuse comparison.
            ChunkPos low=new ChunkPos(origin.offset(-56,0,-56)),high=new ChunkPos(origin.offset(56,0,56));
            for(int x=low.x;x<=high.x;x++) for(int z=low.z;z<=high.z;z++) {
                ChunkPos chunk=new ChunkPos(x,z);chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,0,id);
                h.getLevel().getChunk(x,z);
            }
            ChunkPos roomLow=new ChunkPos(origin.offset(-6,0,-16)),roomHigh=new ChunkPos(origin.offset(14,0,12));
            for(int x=roomLow.x;x<=roomHigh.x;x++) for(int z=roomLow.z;z<=roomHigh.z;z++) {
                ChunkPos chunk=new ChunkPos(x,z);tickingChunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,id);
            }
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original,GameTestInfo rerun,GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info,GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info,GameTestRunner runner) { close(); }
            });
        }
        void ready(Runnable action) {
            h.startSequence().thenWaitUntil(() -> {
                h.assertTrue(chunks.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x,chunk.z)!=null),
                        "All actual route fixture chunks are published");
                h.assertTrue(tickingChunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "Mutated room chunks have native ticking status so block changes invalidate path-type caches");
            })
                    .thenExecute(() -> {
                        h.assertTrue(MiteConfig.asyncPathfindingEnabled(),"Dedicated integration tests explicitly enable asynchronous pursuit");
                        h.assertTrue(digger!=null && melee!=null && MeleePursuitPaths.applies(mob),"The actual native zombie has managed melee and mining goals");
                        action.run();
                    });
        }
        Path find() { return MeleePursuitPaths.find(mob,player,null); }
        void awaitPath(Supplier<Path> query,java.util.function.Consumer<Path> complete) {
            Path[] result=new Path[1];
            h.startSequence().thenWaitUntil(() -> {
                AsyncPathSearchEngine.pump(h.getLevel().getServer(),TimeUnit.MILLISECONDS.toNanos(4));
                result[0]=query.get();
                h.assertTrue(!MeleePursuitPaths.pending(mob) && MeleePursuitPaths.reaches(mob,result[0],player),
                        "Waiting for the full native pursuit decision, not just its first worker stage");
            }).thenExecute(() -> complete.accept(result[0]));
        }
        void divider() {
            for(int z=-16;z<=12;z++) for(int y=0;y<7;y++) h.getLevel().setBlock(origin.offset(3,y,z),Blocks.BEDROCK.defaultBlockState(),2);
        }
        void longWall() {
            // Reuse the existing long-wall regression: reaching a nearby target requires a
            // real route over 56 blocks, beyond the direct search's 48-block allowance.
            for(int x=-6;x<=14;x++) for(int z=-30;z<=30;z++) for(int y=-1;y<=7;y++) {
                boolean solid=y==-1 || y==7 || x==-6 || x==14 || z==-30 || z==30;
                h.getLevel().setBlock(origin.offset(x,y,z),(solid?Blocks.BEDROCK:Blocks.AIR).defaultBlockState(),2);
            }
            for(int z=-27;z<=27;z++) for(int y=0;y<=5;y++)
                h.getLevel().setBlock(origin.offset(2,y,z),Blocks.BEDROCK.defaultBlockState(),2);
        }
        @Override public void close() {
            if(closed) return;closed=true;MeleePursuitPaths.invalidate(mob);if(digger!=null)digger.stop();mob.discard();
            for(Player target:players) target.discard();
            for(ChunkPos chunk:chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,0,id);
            for(ChunkPos chunk:tickingChunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,id);
        }
    }

    private static final class CallbackZombie extends Zombie {
        int starts,finishes;
        CallbackZombie(Level level) { super(EntityType.ZOMBIE,level); }
        @Override public void onPathfindingStart() { starts++; }
        @Override public void onPathfindingDone() { finishes++; }
    }
}
