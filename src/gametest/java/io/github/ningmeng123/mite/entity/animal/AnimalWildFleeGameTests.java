package io.github.ningmeng123.mite.entity.animal;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Genuine outdoor damage and walking response, including adult waypoint edges. @author NingMeng123 */
@GameTestHolder("mite_animal_flee")
@PrefixGameTestTemplate(false)
public final class AnimalWildFleeGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_wild_flee_test", Integer::compareTo);

    private AnimalWildFleeGameTests() {}

    @GameTest(template="empty", templateNamespace="mite", batch="animal_wild_flee_edge", timeoutTicks=500)
    public static void oneRealHitReliablyMakesUnhitWideAdultsFleeAtAnOrdinaryBlockEdge(GameTestHelper h) {
        Fixture f = new Fixture(h, false);
        f.begin().thenExecute(f::hit).thenIdle(12).thenExecute(() -> {
            for (PathfinderMob animal : f.animals) {
                h.assertTrue(R196AnimalAi032.lastAttacker(animal) == f.player && R196AnimalAi032.spooked(animal),
                        "One genuine player hit, without assigning peer fear, immediately alarms the real mixed outdoor herd");
                if (animal != f.victim) h.assertTrue(f.firstRoute.containsKey(animal),
                        "Every unhit peer installs a real route promptly, including the cow whose native directional skip passes its first node: " + f.describe(animal));
            }
        }).thenIdle(20).thenExecute(() -> {
            for (PathfinderMob animal : f.animals) if (animal != f.victim) h.assertTrue(
                    f.awayGain(animal) >= 1.5D && f.firstRoute.containsKey(animal),
                    "An unhit animal actually walks at least 1.5 blocks away, beyond a status flag or incidental pushing: " + f.describe(animal));
            f.assertNativeLimits(); f.report("flat-block-edge-peers"); f.close();
        }).thenSucceed();
    }

    @GameTest(template="empty", templateNamespace="mite", batch="animal_wild_flee_slope", timeoutTicks=500)
    public static void aCompleteSafeUphillRouteStartsWithoutComparingEveryDistantCandidate(GameTestHelper h) {
        Fixture f = new Fixture(h, true);
        f.begin().thenExecute(f::hit).thenWaitUntil(() -> h.assertTrue(f.firstGroundedTick >= 0,
                "The genuinely damaged animal naturally returns to the ground after real knockback"))
                .thenIdle(6).thenExecute(() -> {
                    h.assertTrue(f.sawAirborne, "This measures a real airborne damage response, without suppressing knockback");
                    h.assertTrue(f.firstRoute.containsKey(f.victim)
                                    && f.firstRoute.get(f.victim) - f.firstGroundedTick <= 6,
                            "A safe complete route is installed within six actual world ticks after landing rather than waiting through 12 or 24 candidates: " + f.describe(f.victim));
                    h.assertTrue(f.animals.stream().flatMap(animal -> f.navigation(animal).queries.stream())
                                    .anyMatch(query -> query.complete && Double.isFinite(query.score) && query.score < 9.5D && query.rise >= 2D),
                            "The real sloped course actually supplies a complete, safe native path below the old immediate-score threshold");
                }).thenIdle(26).thenExecute(() -> {
                    for (PathfinderMob animal : f.animals) h.assertTrue(f.awayGain(animal) >= 1.5D
                                    && f.firstRoute.containsKey(animal),
                            "Every member of the mixed herd really follows an escape route uphill, not merely a native query or fear marker: " + f.describe(animal));
                    h.assertTrue(f.groundedPosition != null && f.victim.position().distanceTo(f.danger)
                                    - f.groundedPosition.distanceTo(f.danger) >= 1.5D,
                            "The damaged animal walks another 1.5 blocks away after actual landing, without counting its damage-knockback displacement");
                    f.assertNativeLimits(); f.report("uphill-complete-response"); f.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", templateNamespace="mite", batch="animal_wild_flee_repeat", timeoutTicks=500)
    public static void anotherRealHitRealertsPeersEvenWhenTheExistingAlarmDeadlineDoesNotIncrease(GameTestHelper h) {
        Fixture f = new Fixture(h, false, true);
        PathfinderMob peer = f.animals.get(1);
        int[] originalHit = {0}, countBeforeRepeat = {0}, repeatedAt = {0};
        long[] deadline = {0};
        f.begin().thenExecute(() -> {
            f.victim.getRandom().setSeed(seedForAlarmRoll(99));
            f.hit(); originalHit[0] = f.tick();
            deadline[0] = R196AnimalAi032.state(peer).getLong("herd_alarm_until");
            h.assertTrue(deadline[0] == h.getLevel().getGameTime() + 299L,
                    "The first real event deterministically rolls the original long herd deadline");
        }).thenWaitUntil(() -> {
            var queries = f.navigation(peer).queries;
            h.assertTrue(queries.size() >= 16 && f.tick() - originalHit[0] >= 21
                            && f.tick() - queries.getLast().tick >= 8,
                    "The actual sealed peer finishes real native candidate searches and enters a measured search-free failed-route backoff: " + f.describe(peer));
        }).thenExecute(() -> {
            h.assertTrue(f.tick() - originalHit[0] < 99,
                    "The second accepted damage occurs while the earlier naturally rolled deadline is still longer than a fresh short alarm");
            countBeforeRepeat[0] = f.navigation(peer).queries.size(); repeatedAt[0] = f.tick();
            Vec3 stationaryThreat = f.player.position();
            f.victim.getRandom().setSeed(seedForAlarmRoll(0)); f.hit();
            h.assertTrue(f.player.position().equals(stationaryThreat)
                            && R196AnimalAi032.state(peer).getLong("herd_alarm_until") == deadline[0],
                    "A real repeat hit by the same stationary player legitimately leaves the maximum alarm deadline unchanged");
        }).thenIdle(6).thenExecute(() -> {
            var queries = f.navigation(peer).queries;
            h.assertTrue(queries.size() > countBeforeRepeat[0]
                            && queries.get(countBeforeRepeat[0]).tick - repeatedAt[0] <= 6,
                    "An unhit peer notices the new real alarm and receives ordinary-budget native work within six ticks despite the unchanged deadline: " + f.describe(peer));
            h.assertTrue(R196AnimalAi032.state(peer).getLong("herd_alarm_until") == deadline[0],
                    "Handling a new real alarm must not renew the existing expiry during ordinary retry ticks");
            f.assertNativeLimits(); f.report("real-repeat-unchanged-alarm-deadline"); f.close();
        }).thenSucceed();
    }

    private static long seedForAlarmRoll(int desired) {
        for (long seed = 0; seed < 100000L; seed++) {
            var random = new java.util.Random(seed);
            random.nextInt(200); // rememberAttacker on the directly hit source
            if (random.nextInt(100) == desired) return seed;
        }
        throw new AssertionError("No deterministic source alarm seed");
    }

    private record Query(int tick, boolean complete, double score, int nodes, double rise) {}

    private static final class TestCow extends Cow {
        TestCow(Level level) { super(EntityType.COW, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
    }
    private static final class TestPig extends Pig {
        TestPig(Level level) { super(EntityType.PIG, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
    }
    private static final class TestSheep extends Sheep {
        TestSheep(Level level) { super(EntityType.SHEEP, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
    }
    private static final class TestChicken extends Chicken {
        TestChicken(Level level) { super(EntityType.CHICKEN, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
    }

    /** Always delegates to the actual native finder and movement controller. */
    private static final class RecordingNavigation extends GroundPathNavigation {
        final List<Query> queries = new ArrayList<>();
        final Map<Integer, Integer> perTick = new HashMap<>();
        @Nullable Fixture fixture;
        RecordingNavigation(Mob mob, Level level) { super(mob, level); }
        @Override protected PathFinder createPathFinder(int maximumVisitedNodes) {
            nodeEvaluator = new WalkNodeEvaluator(); nodeEvaluator.setCanPassDoors(true);
            return new RecordingFinder(this, nodeEvaluator, maximumVisitedNodes);
        }
    }

    private static final class RecordingFinder extends PathFinder {
        final RecordingNavigation navigation;
        RecordingFinder(RecordingNavigation navigation, NodeEvaluator evaluator, int maximumVisitedNodes) {
            super(evaluator, maximumVisitedNodes); this.navigation = navigation;
        }
        @Nullable @Override public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> destinations,
                                                float range, int accuracy, float visitedNodesMultiplier) {
            Path result = super.findPath(region, mob, destinations, range, accuracy, visitedNodesMultiplier);
            Fixture f = navigation.fixture;
            if (f != null && f.active && R196AnimalAi032.isSpookedOrFleeing(mob)
                    && R196AnimalAi032.lastAttacker(mob) != null) {
                int tick = mob.getServer().getTickCount();
                double score = AnimalEscapePath.score((PathfinderMob)mob, result, mob.position(), f.player.position());
                navigation.queries.add(new Query(tick, result != null && result.canReach(), score, result == null ? 0 : result.getNodeCount(),
                        result == null || result.getEndNode() == null ? 0D : result.getEndNode().y - mob.getY()));
                navigation.perTick.merge(tick, 1, Integer::sum); f.nativePerTick.merge(tick, 1, Integer::sum);
            }
            return result;
        }
    }

    @SuppressWarnings("removal")
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final List<PathfinderMob> animals = new ArrayList<>();
        final List<ChunkPos> loaded = new ArrayList<>(), ticking = new ArrayList<>();
        final Map<PathfinderMob, Vec3> initial = new HashMap<>();
        final Map<PathfinderMob, Integer> firstRoute = new HashMap<>();
        final Map<Integer, Integer> nativePerTick = new HashMap<>();
        final PathfinderMob victim;
        final ServerPlayer player;
        final EmbeddedChannel channel;
        Vec3 danger;
        @Nullable Vec3 groundedPosition;
        int hitTick = -1, firstGroundedTick = -1;
        boolean active, closed, sawAirborne;

        Fixture(GameTestHelper h, boolean slope) { this(h, slope, false); }

        Fixture(GameTestHelper h, boolean slope, boolean sealedPeer) {
            this.h = h;
            origin = new BlockPos(9_200_000 + key * 192, h.absolutePos(BlockPos.ZERO).getY() + 12, 9_200_000);
            ChunkPos low = new ChunkPos(origin.offset(-64, 0, -64)), high = new ChunkPos(origin.offset(64, 0, 64));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos pos = new ChunkPos(x, z); loaded.add(pos);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, pos, 0, key);
                h.getLevel().getChunk(x, z, ChunkStatus.FULL, true);
            }
            for (int x = -24; x <= 36; x++) for (int z = -18; z <= 18; z++) {
                int floor = slope ? Math.clamp((x - 4) / 2, 0, 6) : 0;
                for (int y = -2; y <= 11; y++) h.getLevel().setBlock(origin.offset(x, y, z),
                        (y == floor - 1 ? Blocks.GRASS_BLOCK : y < floor ? Blocks.DIRT : Blocks.AIR).defaultBlockState(), 2);
            }
            if (sealedPeer) {
                for (int centerZ : new int[]{0, 3}) for (int x = 2; x <= 4; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++)
                    if (x == 2 || x == 4 || Math.abs(z - centerZ) == 1) for (int y = 0; y <= 3; y++)
                        h.getLevel().setBlock(origin.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 2);
            }
            ChunkPos tickLow = new ChunkPos(origin.offset(-20, 0, -14)), tickHigh = new ChunkPos(origin.offset(36, 0, 14));
            for (int x = tickLow.x; x <= tickHigh.x; x++) for (int z = tickLow.z; z <= tickHigh.z; z++) {
                ChunkPos pos = new ChunkPos(x, z); ticking.add(pos);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, pos, 2, key);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "wildflee" + key), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            player.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.CREATIVE);
            player.moveTo(origin.getX() + (slope || sealedPeer ? 1.5D : 2.5D), origin.getY(), origin.getZ() + .5D);

            if (slope) {
                victim = add(new TestChicken(h.getLevel()), 3.5D, .5D);
                add(new TestCow(h.getLevel()), 3.5D, 2.5D);
                add(new TestPig(h.getLevel()), 3.5D, -1.5D);
                add(new TestSheep(h.getLevel()), 3.5D, 4.5D);
            } else {
                // The unhit cow stays at the ordinary .99 edge; knockback cannot
                // move this regression fixture into a more forgiving sub-block position.
                victim = add(new TestChicken(h.getLevel()), 3.5D, 3.5D);
                add(new TestCow(h.getLevel()), sealedPeer ? 3.5D : 3.99D, .5D);
                add(new TestPig(h.getLevel()), 5.99D, -1.5D);
                add(new TestSheep(h.getLevel()), 5.99D, 1.5D);
                add(new TestChicken(h.getLevel()), 4.5D, -3.5D);
            }
            h.onEachTick(() -> {
                if (!active || closed) return;
                if (hitTick >= 0) {
                    if (!victim.onGround()) sawAirborne = true;
                    if (sawAirborne && victim.onGround() && firstGroundedTick < 0) {
                        firstGroundedTick = tick(); groundedPosition = victim.position();
                    }
                }
                for (PathfinderMob animal : animals) if (R196AnimalAi032.state(animal).getBoolean("fleeing")
                        && !animal.getNavigation().isDone()) firstRoute.putIfAbsent(animal, tick());
                assertNativeLimits();
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { report("failure"); close(); }
            });
        }

        PathfinderMob add(PathfinderMob animal, double x, double z) {
            animal.moveTo(origin.getX() + x, origin.getY(), origin.getZ() + z);
            EventHooks.finalizeMobSpawn(animal, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
            ((net.minecraft.world.entity.AgeableMob)animal).setAge(0);
            animal.setNoAi(true); animal.setPersistenceRequired(); animal.setOnGround(true);
            h.assertTrue(h.getLevel().addFreshEntity(animal), "A genuine vanilla animal is registered and receives its actual source goals");
            Livestock032.needs(animal, new LivestockRules032.Needs(1F, 1F, 1F));
            animal.getRandom().setSeed(311101L + animals.size()); animals.add(animal); initial.put(animal, animal.position());
            return animal;
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
                h.assertTrue(loaded.stream().allMatch(pos -> h.getLevel().getChunkSource().getChunkNow(pos.x, pos.z) != null)
                                && ticking.stream().allMatch(pos -> h.getLevel().isPositionEntityTicking(pos.getWorldPosition())),
                        "The full native search terrain and outdoor herd reach real entity-ticking status before damage");
                h.assertTrue(h.getLevel().getEntity(player.getUUID()) == player, "The remembered attacker is an actual registered server player");
            }).thenExecute(() -> {
                for (PathfinderMob animal : animals) {
                    h.assertTrue(h.getLevel().noCollision(animal, animal.getBoundingBox().deflate(.001D)), "Each initially separated outdoor animal fits the terrain");
                    navigation(animal).fixture = this; animal.setNoAi(false);
                }
                active = true; player.setGameMode(GameType.SURVIVAL);
            });
        }

        void hit() {
            danger = player.position(); hitTick = tick();
            float health = victim.getHealth();
            h.assertTrue(victim.hurt(victim.damageSources().playerAttack(player), .25F) && victim.getHealth() < health,
                    "An accepted actual player damage event is the only initial herd-fear stimulus");
            for (PathfinderMob animal : animals) h.assertTrue(R196AnimalAi032.lastAttacker(animal) == player,
                    "All actual nearby peers immediately receive the same attacker through the real damage event");
        }

        int tick() { return h.getLevel().getServer().getTickCount(); }
        RecordingNavigation navigation(PathfinderMob animal) { return (RecordingNavigation)animal.getNavigation(); }
        double awayGain(PathfinderMob animal) { return animal.position().distanceTo(danger) - initial.get(animal).distanceTo(danger); }
        void assertNativeLimits() {
            h.assertTrue(nativePerTick.values().stream().allMatch(count -> count <= 5), "The whole outdoor herd still shares at most four ordinary plus one urgent native searches per tick: " + nativePerTick);
            for (PathfinderMob animal : animals) h.assertTrue(navigation(animal).perTick.values().stream().allMatch(count -> count <= 1),
                    "An animal enters genuine native A* at most once in one server tick: " + describe(animal));
        }
        String describe(PathfinderMob animal) {
            return animal.getType() + " id=" + animal.getId() + ", tickSinceHit=" + (tick() - hitTick)
                    + ", position=" + animal.position().subtract(Vec3.atBottomCenterOf(origin))
                    + ", firstRoute=" + firstRoute.get(animal) + ", grounded=" + firstGroundedTick
                    + ", fear=" + R196AnimalAi032.state(animal) + ", native=" + navigation(animal).queries;
        }
        void report(String name) {
            System.out.println("ANIMAL WILD FLEE " + name + " hitTick=" + hitTick + ", grounded=" + firstGroundedTick
                    + ", budget=" + AnimalFleeBudget.stats(h.getLevel().getServer()));
            for (PathfinderMob animal : animals) System.out.println("ANIMAL WILD FLEE " + describe(animal));
        }
        @Override public void close() {
            if (closed) return; closed = true;
            animals.forEach(Mob::discard); player.server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            for (ChunkPos pos : ticking) h.getLevel().getChunkSource().removeRegionTicket(TICKET, pos, 2, key);
            for (ChunkPos pos : loaded) h.getLevel().getChunkSource().removeRegionTicket(TICKET, pos, 0, key);
        }
    }
}
