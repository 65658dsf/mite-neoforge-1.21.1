package io.github.ningmeng123.mite.gametest;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.entity.CavePlayerDiscovery;
import io.github.ningmeng123.mite.entity.MeleePursuitPaths;
import io.github.ningmeng123.mite.entity.SourceMonsterTargeting;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual hidden cave routes, failure reuse, candidate rotation and server-wide probe limits. @author NingMeng123 */
@GameTestHolder("mite_cave_discovery")
@PrefixGameTestTemplate(false)
public final class CavePlayerDiscoveryGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_cave_discovery_test", Integer::compareTo);

    @GameTest(template = "empty", batch = "cave_discovery_route", timeoutTicks = 400)
    public static void hiddenCavePlayerIsDiscoveredThroughACompleteRouteAndTheRouteIsReused(GameTestHelper h) {
        Fixture f = new Fixture(h, false, 1);
        f.whenReady(() -> {
            Zombie mob = f.mobs.getFirst();
            ServerPlayer player = f.players.getFirst().player;
            h.assertTrue(CavePlayerDiscovery.underground(h.getLevel(), mob) && CavePlayerDiscovery.underground(h.getLevel(), player),
                    "Both sides of the opaque corner are actual covered cave candidates");
            h.assertFalse(SourceMonsterTargeting.canSee(mob, player, false), "The fresh cave zombie has never seen the player through the wall");
            var goal = f.goal(mob);
            long before = MeleePursuitPaths.searchStats(mob).searches();
            h.assertTrue(goal.canUse(), "The installed player goal discovers an unseen player through a verified walking route");
            h.assertTrue(mob.getTarget() == null && mob.getNavigation().getPath() == null,
                    "A candidate query itself neither assigns aggro nor starts navigation");
            long searched = MeleePursuitPaths.searchStats(mob).searches();
            h.assertTrue(searched > before, "Discovery really performed a native route query");
            goal.start();
            h.assertTrue(mob.getTarget() == player, "The installed selector assigns the proven player on start");
            Path path = MeleePursuitPaths.find(mob, player, null);
            h.assertTrue(MeleePursuitPaths.completeRouteClear(mob, path, player), "The actual route reaches a physically attackable endpoint");
            h.assertTrue(MeleePursuitPaths.searchStats(mob).searches() == searched,
                    "The first pursuit entry reuses the discovery success without a second native search");
            boolean around = false;
            for (int index = 0; index < path.getNodeCount(); index++) around |= Math.abs(path.getNode(index).z - f.origin.getZ()) >= 4;
            h.assertTrue(around, "The real native path bends around the opaque divider");
            h.assertTrue(mob.getNavigation().getPath() == null, "Route proof alone still does not issue movement");
            goal.stop(); f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "cave_discovery_sealed", timeoutTicks = 400)
    public static void cachedRouteNewlyBlockedBeyondSixNodesCannotDiscoverThePlayer(GameTestHelper h) {
        Fixture f = new Fixture(h, false, 1);
        f.whenReady(() -> {
            Zombie mob = f.mobs.getFirst();
            ServerPlayer player = f.players.getFirst().player;
            Path warm = MeleePursuitPaths.find(mob, player, null);
            h.assertTrue(MeleePursuitPaths.completeRouteClear(mob, warm, player), "The original hidden cave has a real complete walking route");
            boolean distant = false;
            for (int index = 7; index < warm.getNodeCount(); index++) distant |= warm.getNode(index).x == f.origin.getX() + 8;
            h.assertTrue(distant, "The newly sealed cross-section is beyond the next six cached nodes");
            f.crossWall(8, -8, 8);
            long warmQueries = MeleePursuitPaths.searchStats(mob).searches();
            var goal = f.goal(mob);
            h.assertFalse(goal.canUse(), "Whole-route validation rejects a cached success after a distant wall is placed");
            h.assertTrue(mob.getTarget() == null && mob.getNavigation().getPath() == null,
                    "A newly sealed route never assigns or moves toward the unseen player");
            h.assertTrue(MeleePursuitPaths.searchStats(mob).searches() == warmQueries,
                    "This rejection exercises the successful cache rather than hiding the stale-path case behind a fresh search");
            Path failed = MeleePursuitPaths.find(mob, player, null);
            h.assertFalse(MeleePursuitPaths.reaches(mob, failed, player), "A fresh native query confirms the completely sealed cave has no route");
            long failedQueries = MeleePursuitPaths.searchStats(mob).searches();
            for (int call = 0; call < 30; call++) h.assertFalse(goal.canUse(), "Repeated idle goal callbacks cannot acquire the sealed player");
            MeleePursuitPaths.find(mob, player, null);
            h.assertTrue(MeleePursuitPaths.searchStats(mob).searches() == failedQueries,
                    "The discovery cooldown and shared negative cache prevent repeated native searches");
            h.assertTrue(mob.getTarget() == null, "All rejected discovery callbacks leave the real target field empty");
            f.close(); h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "cave_discovery_rotation", timeoutTicks = 640)
    public static void anUnreachableNearestPlayerDoesNotPermanentlyHideTheNextReachablePlayer(GameTestHelper h) {
        Fixture f = new Fixture(h, true, 1);
        f.players.getFirst().player.moveTo(f.origin.getX() + 3.5D, f.origin.getY(), f.origin.getZ() + .5D);
        RealPlayer reachable = f.addPlayer(0, 6);
        f.sideWall(2, -4, 0);
        Zombie mob = f.mobs.getFirst();
        var goal = f.goal(mob);
        long[] due = {Long.MAX_VALUE};
        f.whenReady(() -> {
            h.assertFalse(SourceMonsterTargeting.canSee(mob, reachable.player, false), "The farther reachable player is also genuinely hidden by a corner");
            h.assertFalse(goal.canUse(), "The first discovery only tests the nearest, sealed player");
            h.assertTrue(CavePlayerDiscovery.probeStats(mob).lastCandidateId() == f.players.getFirst().player.getId(),
                    "The first actual probe chose the geometrically nearest player");
            due[0] = CavePlayerDiscovery.probeStats(mob).nextProbeAt();
        }).thenWaitUntil(() -> {
            h.assertTrue(h.getLevel().getGameTime() >= due[0] && CavePlayerDiscovery.probeStats(h.getLevel()).probesThisTick() == 0,
                    "The second probe respects the per-mob cooldown and has an available server tick");
        }).thenExecute(() -> f.guard(() -> {
            h.assertTrue(goal.canUse(), "The next due request rotates to the reachable hidden player");
            h.assertTrue(CavePlayerDiscovery.probeStats(mob).lastCandidateId() == reachable.player.getId(),
                    "The blocked nearest candidate does not monopolize later probes");
            goal.start();
            h.assertTrue(mob.getTarget() == reachable.player, "Only the player with the complete route becomes the actual target");
            goal.stop(); f.close();
        })).thenSucceed();
    }

    @GameTest(template = "empty", batch = "cave_discovery_budget", timeoutTicks = 640)
    public static void serverProbeLimitDefersCrowdedMobsFairlyAndDoesNotLimitVisibleTargets(GameTestHelper h) {
        Fixture f = new Fixture(h, true, 24);
        Zombie tail = f.mobs.getLast();
        tail.moveTo(f.origin.getX() + 5.5D, f.origin.getY(), f.origin.getZ() + 6.5D);
        f.sideWall(2, 3, 8);
        RealPlayer visible = f.addPlayer(0, 3);
        visible.player.setGameMode(GameType.SPECTATOR);
        Zombie control = f.addMob(0, 0);
        f.whenReady(() -> {
            var initial = CavePlayerDiscovery.probeStats(h.getLevel());
            for (Zombie mob : f.mobs) h.assertFalse(f.goal(mob).canUse(), "The first crowded tick cannot skip sealed routes or the exhausted probe budget");
            var first = CavePlayerDiscovery.probeStats(h.getLevel());
            h.assertTrue(first.probes() - initial.probes() == 2 && first.probesThisTick() == 2 && first.peakProbesPerTick() <= 2,
                    "The server performs at most two actual hidden-discovery searches in one tick");
            h.assertTrue(first.budgetDeferrals() - initial.budgetDeferrals() >= f.mobs.size() - 2,
                    "Other idle mobs receive explicit short budget deferrals");
            h.assertTrue(MeleePursuitPaths.searchStats(tail).searches() == 0 && MeleePursuitPaths.searchStats(tail).failures() == 0,
                    "Budget refusal neither searches nor records an unreachable result for the reachable queue tail");
            visible.player.setGameMode(GameType.SURVIVAL);
            try {
                h.assertTrue(h.getLevel().getEntitiesOfClass(Player.class, control.getBoundingBox().inflate(24D, 6D, 24D),
                        entity -> entity == visible.player).contains(visible.player), "The visible positive control was indexed before the same-tick budget assertion");
                var goal = f.goal(control);
                h.assertTrue(goal.canUse(), "Actual sight acquires a visible player even after the hidden-search permits are exhausted");
                goal.start();
                h.assertTrue(control.getTarget() == visible.player, "The visible positive control uses the installed selector");
                h.assertTrue(CavePlayerDiscovery.probeStats(h.getLevel()).probes() == first.probes(), "Visible acquisition consumes no hidden-discovery search");
                goal.stop();
            } finally { control.discard(); visible.close(); }
        }).thenWaitUntil(() -> {
            f.guard(() -> {
                for (Zombie mob : f.mobs) {
                    if (mob.isRemoved() || mob.getTarget() != null) continue;
                    var goal = f.goal(mob);
                    if (!goal.canUse()) continue;
                    goal.start();
                    h.assertTrue(mob == tail && mob.getTarget() == f.players.getFirst().player,
                            "Only the hidden queue tail has a real complete route through its side of the cave");
                    h.assertTrue(CavePlayerDiscovery.probeStats(h.getLevel()).peakProbesPerTick() <= 2,
                            "The fair retry queue never exceeds the server's two-probe limit");
                    return;
                }
            });
            h.assertTrue(tail.getTarget() == f.players.getFirst().player, "The deferred reachable queue tail eventually receives a probe despite fixed iteration order");
        }).thenExecute(f::close).thenSucceed();
    }

    private static final class RealPlayer implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        boolean closed;
        RealPlayer(GameTestHelper h) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mite-cave"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true);
        }
        @Override public void close() {
            if (closed) return;
            closed = true; player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final BlockPos origin;
        final List<Zombie> mobs = new ArrayList<>();
        final List<RealPlayer> players = new ArrayList<>();
        final List<ChunkPos> chunks = new ArrayList<>();
        final int ticketKey;
        boolean closed;
        Fixture(GameTestHelper h, boolean sealed, int mobCount) {
            this.h = h; ticketKey = IDS.incrementAndGet();
            origin = new BlockPos(1_620_002 + ticketKey * 128, h.absolutePos(BlockPos.ZERO).getY() + 12, 1_620_008);
            for (int x = -4; x <= 12; x++) for (int z = -8; z <= 8; z++) for (int y = -1; y <= 7; y++) {
                boolean boundary = y == -1 || y == 7 || x == -4 || x == 12 || z == -8 || z == 8;
                h.getLevel().setBlock(origin.offset(x, y, z), (boundary ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            crossWall(2, sealed ? -8 : -3, sealed ? 8 : 3);
            for (int index = 0; index < mobCount; index++) addMob(0, 0);
            addPlayer(10, 0);
            ChunkPos low = new ChunkPos(origin.offset(-4, 0, -8)), high = new ChunkPos(origin.offset(12, 0, 8));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); chunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                    h.getLevel().getChunk(x + dx, z + dz, ChunkStatus.FULL, true);
            }
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }
        Zombie addMob(int x, int z) {
            Zombie mob = EntityType.ZOMBIE.create(h.getLevel());
            mob.moveTo(origin.getX() + x + .5D, origin.getY(), origin.getZ() + z + .5D);
            mob.setNoAi(true); mob.setNoGravity(true); mob.setOnGround(true);
            mob.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.getLevel().addFreshEntity(mob); mobs.add(mob); return mob;
        }
        RealPlayer addPlayer(int x, int z) {
            RealPlayer fixture = new RealPlayer(h);
            fixture.player.moveTo(origin.getX() + x + .5D, origin.getY(), origin.getZ() + z + .5D);
            players.add(fixture); return fixture;
        }
        void crossWall(int x, int minZ, int maxZ) {
            for (int z = minZ; z <= maxZ; z++) for (int y = 0; y <= 6; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        void sideWall(int z, int minX, int maxX) {
            for (int x = minX; x <= maxX; x++) for (int y = 0; y <= 6; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
        }
        SourceMonsterTargeting.Nearest goal(Zombie mob) {
            return mob.targetSelector.getAvailableGoals().stream().map(wrapped -> wrapped.getGoal())
                    .filter(SourceMonsterTargeting.Nearest.class::isInstance).map(SourceMonsterTargeting.Nearest.class::cast)
                    .filter(goal -> goal.targetType() == Player.class).findFirst().orElseThrow();
        }
        GameTestSequence whenReady(Runnable action) {
            return h.startSequence().thenWaitUntil(() -> {
                for (int count = 0; count < 128 && h.getLevel().getChunkSource().pollTask(); count++) {}
                h.assertTrue(chunks.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "All cave chunks are visible to PathNavigationRegion");
                h.assertTrue(chunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "All cave chunks reach native ticking status before terrain changes and path-cache invalidation are tested");
                h.assertTrue(mobs.stream().allMatch(mob -> h.getLevel().getEntity(mob.getUUID()) == mob), "All actual cave mobs are indexed");
                h.assertTrue(players.stream().filter(player -> !player.closed).allMatch(player -> h.getLevel().getEntitiesOfClass(Player.class,
                                player.player.getBoundingBox().inflate(1D), entity -> entity == player.player).contains(player.player)),
                        "Native player queries actually see every registered survival player");
                h.assertTrue(CavePlayerDiscovery.probeStats(h.getLevel()).probesThisTick() == 0, "Each scenario begins in a fresh hidden-probe budget tick");
            }).thenExecute(() -> guard(action));
        }
        void guard(Runnable action) {
            if (closed) return;
            try { action.run(); } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            mobs.forEach(mob -> { mob.getNavigation().stop(); mob.discard(); });
            players.forEach(RealPlayer::close);
            for (ChunkPos chunk : chunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}
