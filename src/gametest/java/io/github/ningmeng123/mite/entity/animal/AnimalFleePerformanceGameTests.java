package io.github.ningmeng123.mite.entity.animal;

import com.mojang.authlib.GameProfile;
import io.github.ningmeng123.mite.config.MiteConfig;
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
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Chicken;
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

/** Actual native A*, damage, movement and timer regressions for crowded fleeing. @author NingMeng123 */
@GameTestHolder("mite_animal_flee")
@PrefixGameTestTemplate(false)
public final class AnimalFleePerformanceGameTests {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.create("mite_animal_flee_test", Integer::compareTo);

    private AnimalFleePerformanceGameTests() {}

    @GameTest(template="empty", templateNamespace="mite", batch="animal_flee_dense", timeoutTicks=1500)
    public static void oneRealHitInADenseClosedHerdSharesNativeWorkAndEveryChickenAdvances(GameTestHelper h) {
        Fixture f = new Fixture(h, Layout.DENSE, 24);
        long[] alarm = {0}, deferred = {0};
        int[] alarmTick = {0};
        int[] firstRound = new int[24];
        f.begin().thenExecute(() -> {
            deferred[0] = AnimalFleeBudget.stats(h.getLevel().getServer()).deferred();
            f.hit(f.chickens.getFirst(), f.player);
            alarmTick[0] = f.tick();
            alarm[0] = R196AnimalAi032.state(f.chickens.getFirst()).getLong("herd_alarm_until");
            for (TestChicken chicken : f.chickens) h.assertTrue(R196AnimalAi032.lastAttacker(chicken) == f.player
                            && R196AnimalAi032.spooked(chicken)
                            && R196AnimalAi032.state(chicken).getLong("herd_alarm_until") == alarm[0],
                    "One accepted player hit, without assigning fear to peers, immediately alarms all 24 real nearby chickens");
        }).thenIdle(64).thenExecute(() -> {
            f.assertFirstNativeRound(alarmTick[0], 64);
        }).thenIdle(96).thenExecute(() -> {
            f.assertNativeLimits();
            for (int i = 0; i < f.chickens.size(); i++) {
                TestChicken chicken = f.chickens.get(i);
                firstRound[i] = chicken.recording().escapeQueries();
                h.assertTrue(firstRound[i] > 0 && chicken.nativeTicks > 100 && chicken.furthest > .2D,
                        "Every chicken receives real native A* work and physically advances under complete world ticks: " + f.describe(chicken));
            }
        }).thenIdle(400).thenExecute(() -> {
            f.assertNativeLimits();
            for (int i = 0; i < f.chickens.size(); i++) {
                TestChicken chicken = f.chickens.get(i);
                h.assertTrue(chicken.recording().escapeQueries() > firstRound[i],
                        "Later work remains fair after the closed herd packs against its far wall: " + f.describe(chicken));
                var state = R196AnimalAi032.state(chicken);
                h.assertTrue(!R196AnimalAi032.spooked(chicken) && state.getInt("panic_countdown") == 0
                                && state.getLong("herd_alarm_until") <= alarm[0],
                        "Repeated blocked routes and peer relays cannot renew an old alarm or panic without new damage: " + f.describe(chicken));
                h.assertTrue(chicken.isAlive() && chicken.getX() > f.origin.getX()
                                && chicken.getX() < f.origin.getX() + 9D && Math.abs(chicken.getZ() - f.origin.getZ()) < 4D,
                        "The dense herd remains alive inside the original solid enclosure");
            }
            if (MiteConfig.animalSearchBudgetEnabled()) h.assertTrue(
                    AnimalFleeBudget.stats(h.getLevel().getServer()).deferred() > deferred[0],
                    "The actual 24-animal world-tick fixture witnesses shared budget contention");
            f.report("dense-herd"); f.close();
        }).thenSucceed();
    }

    @GameTest(template="empty", templateNamespace="mite", batch="animal_flee_failure", timeoutTicks=2200)
    public static void aGenuinelySealedFailureBacksOffButANewSamePlayerHitRespondsImmediatelyAndFearExpires(GameTestHelper h) {
        Fixture f = new Fixture(h, Layout.CELL, 1);
        TestChicken chicken = f.chickens.getFirst();
        int[] beforeHit = {0}, hitTick = {0};
        long[] alarm = {0}, memoryEnd = {0};
        f.begin().thenExecute(() -> { f.hit(chicken, f.player); hitTick[0] = f.tick(); }).thenWaitUntil(() -> {
            RecordingNavigation navigation = chicken.recording();
            h.assertTrue(navigation.escapeQueries() >= 8 && f.tick() - hitTick[0] >= 21
                            && (!MiteConfig.animalSearchBudgetEnabled() || f.tick() - navigation.lastEscapeTick() >= 12),
                    "The real sealed cell exhausts native escape candidates and then leaves a measured search-free backoff interval: " + f.describe(chicken));
        }).thenExecute(() -> {
            h.assertTrue(chicken.position().distanceTo(chicken.start) < .4D,
                    "The failed route is caused by actual solid walls, without a fake navigator or artificially stopped animal");
            beforeHit[0] = chicken.recording().escapeQueries(); hitTick[0] = f.tick();
            f.hit(chicken, f.player);
            alarm[0] = R196AnimalAi032.state(chicken).getLong("herd_alarm_until");
            memoryEnd[0] = h.getLevel().getGameTime() + R196AnimalAi032.state(chicken).getInt("attacker_memory");
        }).thenWaitUntil(() -> h.assertTrue(chicken.recording().escapeQueries() > beforeHit[0],
                "New real damage by the same stationary player bypasses the existing failed-search cooldown"))
                .thenExecute(() -> h.assertTrue(chicken.recording().escapeTickAtIndex(beforeHit[0]) - hitTick[0]
                                <= (MiteConfig.animalSearchBudgetEnabled() ? 4 : 10),
                        "The new hit reaches actual native A* within four optimized or ten synchronous compatibility world ticks: " + f.describe(chicken)))
                .thenWaitUntil(() -> h.assertTrue(h.getLevel().getGameTime() >= alarm[0] + 201L,
                        "Let the genuine short herd alarm and its maximum remaining panic finish without altering either timer"))
                .thenExecute(() -> {
                    h.assertTrue(!R196AnimalAi032.spooked(chicken) && R196AnimalAi032.state(chicken).getInt("panic_countdown") == 0
                                    && R196AnimalAi032.state(chicken).getLong("herd_alarm_until") <= alarm[0],
                            "Ordinary failed-route retries cannot keep extending old panic after its stimulus has expired: " + f.describe(chicken));
                    f.assertNativeLimits();
                }).thenWaitUntil(() -> h.assertTrue(h.getLevel().getGameTime() >= memoryEnd[0] + 10L,
                        "Wait for real attacker-memory ticks, without manually editing fear state or calling the goal"))
                .thenExecute(() -> {
                    h.assertTrue(chicken.nativeTicks >= 900 && chicken.isAlive() && R196AnimalAi032.lastAttacker(chicken) == null
                                    && !R196AnimalAi032.isSpookedOrFleeing(chicken)
                                    && R196AnimalAi032.state(chicken).getInt("attacker_memory") == 0,
                            "A surviving enclosed chicken naturally forgets the old threat and stops fleeing: " + f.describe(chicken));
                    f.assertNativeLimits(); f.report("sealed-backoff-new-hit-expiry"); f.close();
                }).thenSucceed();
    }

    @GameTest(template="empty", templateNamespace="mite", batch="animal_flee_response", timeoutTicks=1500)
    public static void aNewAttackerReversesRealEscapeAndNativeMovementKeepsNinetyFivePercentSpeeds(GameTestHelper h) {
        Fixture f = new Fixture(h, Layout.ROOM, 1);
        TestChicken chicken = f.chickens.getFirst();
        ServerPlayer second = f.extraPlayer("other", Vec3.atBottomCenterOf(f.origin.east(10)));
        int[] hitTick = {0}, previousQueries = {0};
        double[] turningX = {0};
        long[] alarm = {0};
        f.begin().thenExecute(() -> { f.hit(chicken, f.player); hitTick[0] = f.tick(); }).thenWaitUntil(() -> h.assertTrue(
                f.tick() - hitTick[0] >= 21 && chicken.getX() > chicken.start.x + 3D && chicken.recording().returnedWalkingRoute(),
                "The original player causes actual eastward escape along a genuine native route: " + f.describe(chicken)))
                .thenExecute(() -> {
                    h.assertTrue(chicken.recording().requestedSpeed(1.425D),
                            "Real navigation receives (1.3 + 0.2) × 95% while the chicken panics");
                    // Only the new human moves into striking distance. The animal's position,
                    // controls and navigation remain driven entirely by normal world ticks.
                    second.moveTo(chicken.position().add(2.5D, 0D, 0D));
                    turningX[0] = chicken.getX(); previousQueries[0] = chicken.recording().escapeQueries(); hitTick[0] = f.tick();
                    f.hit(chicken, second);
                    h.assertTrue(R196AnimalAi032.lastAttacker(chicken) == second,
                            "Accepted new player damage changes the remembered threat immediately");
                    alarm[0] = R196AnimalAi032.state(chicken).getLong("herd_alarm_until");
                }).thenWaitUntil(() -> h.assertTrue(chicken.recording().escapeQueries() > previousQueries[0],
                        "The new attacker triggers a fresh native escape search"))
                .thenExecute(() -> h.assertTrue(chicken.recording().escapeTickAtIndex(previousQueries[0]) - hitTick[0]
                                <= (MiteConfig.animalSearchBudgetEnabled() ? 4 : 10),
                        "A changed attacker gets native work within four optimized or ten synchronous compatibility world ticks"))
                .thenWaitUntil(() -> h.assertTrue(chicken.getX() < turningX[0] - 3D
                                && chicken.recording().returnedRouteWestOf(turningX[0], hitTick[0]),
                        "The chicken really follows a new westward native route, beyond the short damage-knockback distance: " + f.describe(chicken)))
                .thenWaitUntil(() -> h.assertTrue(h.getLevel().getGameTime() >= alarm[0] + 201L,
                        "The bounded roomy enclosure keeps the threat nearby while the original panic expires naturally"))
                .thenExecute(() -> {
                    h.assertTrue(R196AnimalAi032.state(chicken).getInt("panic_countdown") == 0
                                    && R196AnimalAi032.lastAttacker(chicken) == second && chicken.recording().requestedSpeed(1.235D),
                            "Normal ongoing fleeing receives 1.3 × 95% immediately after the real panic boost finishes: " + f.describe(chicken));
                    h.assertTrue(chicken.nativeTicks > 200 && chicken.furthest > 3D && chicken.isAlive(),
                            "Speed and response checks use a naturally moving live chicken through full entity ticks");
                    f.assertNativeLimits(); f.report("changed-attacker-speed"); f.close();
                }).thenSucceed();
    }

    private enum Layout { DENSE, CELL, ROOM }

    private record Query(int tick, boolean escaping, long nanos, int nodes, @Nullable Vec3 end) {}

    private static final class TestChicken extends Chicken {
        Vec3 start;
        int activeTick, nativeTicks;
        double furthest;
        TestChicken(Level level) { super(EntityType.CHICKEN, level); }
        @Override protected PathNavigation createNavigation(Level level) { return new RecordingNavigation(this, level); }
        RecordingNavigation recording() { return (RecordingNavigation)getNavigation(); }
    }

    /** Records the native entry and delegates every path, node and movement decision unchanged. */
    private static final class RecordingNavigation extends GroundPathNavigation {
        final List<Query> queries = new ArrayList<>();
        final List<Double> speeds = new ArrayList<>();
        final Map<Integer, Integer> escapePerTick = new HashMap<>();
        Fixture fixture;
        int peakEscapePerTick;
        boolean recording;
        RecordingNavigation(Mob mob, Level level) { super(mob, level); }

        @Override protected PathFinder createPathFinder(int maximumVisitedNodes) {
            // These are the original GroundPathNavigation evaluator and door settings.
            nodeEvaluator = new WalkNodeEvaluator(); nodeEvaluator.setCanPassDoors(true);
            return new RecordingFinder(this, nodeEvaluator, maximumVisitedNodes);
        }

        @Override public void setSpeedModifier(double speed) {
            if (recording) speeds.add(speed);
            super.setSpeedModifier(speed);
        }

        int escapeQueries() { return (int)queries.stream().filter(Query::escaping).count(); }
        int firstEscapeTick() { return queries.stream().filter(Query::escaping).mapToInt(Query::tick).min().orElse(Integer.MAX_VALUE); }
        int lastEscapeTick() { return queries.stream().filter(Query::escaping).mapToInt(Query::tick).max().orElse(Integer.MIN_VALUE); }
        int escapeTickAtIndex(int index) {
            return queries.stream().filter(Query::escaping).skip(index).mapToInt(Query::tick).findFirst().orElse(Integer.MAX_VALUE);
        }
        boolean returnedWalkingRoute() { return queries.stream().anyMatch(q -> q.escaping() && q.nodes() > 2); }
        boolean returnedRouteWestOf(double x, int since) {
            return queries.stream().anyMatch(q -> q.escaping() && q.tick() >= since && q.nodes() > 2 && q.end() != null && q.end().x < x - 3D);
        }
        boolean requestedSpeed(double speed) { return speeds.stream().anyMatch(actual -> Math.abs(actual - speed) < .0001D); }
    }

    /** Measures actual PathFinder.findPath calls, including native navigation recomputations. */
    private static final class RecordingFinder extends PathFinder {
        private final RecordingNavigation navigation;
        RecordingFinder(RecordingNavigation navigation, NodeEvaluator evaluator, int maximumVisitedNodes) {
            super(evaluator, maximumVisitedNodes); this.navigation = navigation;
        }
        @Nullable @Override public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> destinations,
                                                float range, int accuracy, float visitedNodesMultiplier) {
            long began = System.nanoTime();
            Path result = super.findPath(region, mob, destinations, range, accuracy, visitedNodesMultiplier);
            if (navigation.recording) {
                boolean escaping = R196AnimalAi032.isSpookedOrFleeing(mob) && R196AnimalAi032.lastAttacker(mob) != null;
                var end = result == null ? null : result.getEndNode();
                Query query = new Query(mob.getServer().getTickCount(), escaping, System.nanoTime() - began,
                        result == null ? 0 : result.getNodeCount(), end == null ? null : new Vec3(end.x + .5D, end.y, end.z + .5D));
                navigation.queries.add(query);
                if (escaping) {
                    navigation.peakEscapePerTick = Math.max(navigation.peakEscapePerTick,
                            navigation.escapePerTick.merge(query.tick(), 1, Integer::sum));
                    if (navigation.fixture != null) navigation.fixture.observeNativeQuery(query);
                }
            }
            return result;
        }
    }

    @SuppressWarnings("removal")
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final int key = IDS.getAndIncrement();
        final BlockPos origin;
        final List<TestChicken> chickens = new ArrayList<>();
        final List<ChunkPos> loadedChunks = new ArrayList<>(), tickingChunks = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final Map<Integer, Integer> nativePerTick = new HashMap<>();
        final ServerPlayer player;
        int peakNativePerTick;
        boolean active, closed;

        Fixture(GameTestHelper h, Layout layout, int count) {
            this.h = h;
            origin = new BlockPos(8_900_000 + key * 192, h.absolutePos(BlockPos.ZERO).getY() + 12, 8_900_000);
            int nativeRegion = layout == Layout.ROOM ? 80 : 64;
            ChunkPos low = new ChunkPos(origin.offset(-nativeRegion, 0, -nativeRegion)), high = new ChunkPos(origin.offset(nativeRegion, 0, nativeRegion));
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); loadedChunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 0, key);
                h.getLevel().getChunk(x, z, ChunkStatus.FULL, true);
            }
            int west = layout == Layout.ROOM ? -16 : layout == Layout.CELL ? -1 : 0;
            int east = layout == Layout.ROOM ? 16 : layout == Layout.CELL ? 1 : 9;
            int radius = layout == Layout.ROOM ? 6 : layout == Layout.CELL ? 1 : 4;
            for (int x = -25; x <= 25; x++) for (int z = -12; z <= 12; z++) for (int y = -1; y <= 4; y++) {
                boolean wall = x >= west && x <= east && Math.abs(z) <= radius
                        && (x == west || x == east || Math.abs(z) == radius);
                h.getLevel().setBlock(origin.offset(x, y, z), (y == -1 || y == 4 || wall ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
            }
            ChunkPos tickLow = new ChunkPos(origin.offset(-18, 0, -8)), tickHigh = new ChunkPos(origin.offset(18, 0, 8));
            for (int x = tickLow.x; x <= tickHigh.x; x++) for (int z = tickLow.z; z <= tickHigh.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z); tickingChunks.add(chunk);
                h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, key);
            }
            player = extraPlayer("first", Vec3.atBottomCenterOf(layout == Layout.DENSE ? origin.east(1) : origin.west(3)));
            for (int i = 0; i < count; i++) {
                TestChicken chicken = new TestChicken(h.getLevel());
                chicken.moveTo(origin.getX() + (layout == Layout.DENSE ? 2.2D + i % 6 * .65D : .5D), origin.getY(),
                        origin.getZ() + (layout == Layout.DENSE ? -1.2D + i / 6 * .8D : .5D));
                EventHooks.finalizeMobSpawn(chicken, h.getLevel(), h.getLevel().getCurrentDifficultyAt(origin), MobSpawnType.SPAWN_EGG, null);
                chicken.setAge(0); chicken.setNoAi(true); chicken.setPersistenceRequired(); chicken.setOnGround(true);
                h.assertTrue(h.getLevel().addFreshEntity(chicken), "The actual chicken enters the server world and installs all source animal goals");
                Livestock032.needs(chicken, new LivestockRules032.Needs(1F, 1F, 1F));
                chicken.getRandom().setSeed(311101L + i); chicken.start = chicken.position(); chickens.add(chicken);
            }
            h.onEachTick(() -> {
                if (!active || closed) return;
                for (TestChicken chicken : chickens) {
                    chicken.nativeTicks = Math.max(chicken.nativeTicks, chicken.tickCount - chicken.activeTick);
                    chicken.furthest = Math.max(chicken.furthest, chicken.position().distanceTo(chicken.start));
                }
                // Check the first real burst on the next game-test tick, rather than allowing
                // a legacy 24-animal unbounded search to keep running until a late assertion.
                assertObservedNativeLimits();
            });
            h.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { report("failure"); close(); }
            });
        }

        ServerPlayer extraPlayer(String suffix, Vec3 position) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "flee" + key + suffix), false);
            ServerPlayer actual = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection);
            actual.server.getPlayerList().placeNewPlayer(connection, actual, cookie);
            actual.setGameMode(GameType.CREATIVE); actual.moveTo(position);
            players.add(actual); channels.add(channel); return actual;
        }

        GameTestSequence begin() {
            return h.startSequence().thenWaitUntil(() -> {
                for (int tasks = 0; tasks < 64 && h.getLevel().getChunkSource().pollTask(); tasks++) {}
                h.assertTrue(loadedChunks.stream().allMatch(chunk -> h.getLevel().getChunkSource().getChunkNow(chunk.x, chunk.z) != null),
                        "All terrain touched by the actual 48-block native searches is published before measuring them");
                h.assertTrue(tickingChunks.stream().allMatch(chunk -> h.getLevel().isPositionEntityTicking(chunk.getWorldPosition())),
                        "The entire enclosure reaches native entity-ticking status before the fear stimulus");
                h.assertTrue(players.stream().allMatch(p -> h.getLevel().getEntity(p.getUUID()) == p),
                        "Every remembered attacker is an actually registered living server player");
            }).thenExecute(() -> {
                for (TestChicken chicken : chickens) {
                    h.assertTrue(chicken.goalSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() instanceof AnimalCommon032Goals.Flee),
                            "The normal source Flee goal is installed, without substituting or manually ticking it");
                    h.assertTrue(h.getLevel().noCollision(chicken, chicken.getBoundingBox().deflate(.001D)), "The initial live animal body fits the enclosure");
                    chicken.activeTick = chicken.tickCount; chicken.recording().fixture = this;
                    chicken.recording().recording = true; chicken.setNoAi(false);
                }
                players.forEach(p -> p.setGameMode(GameType.SURVIVAL)); active = true;
            });
        }

        void hit(TestChicken chicken, ServerPlayer attacker) {
            float before = chicken.getHealth();
            h.assertTrue(chicken.hurt(chicken.damageSources().playerAttack(attacker), .25F)
                            && chicken.isAlive() && chicken.getHealth() < before,
                    "A genuine accepted player damage event causes the only fear stimulus, without editing panic or threat state");
        }

        int tick() { return h.getLevel().getServer().getTickCount(); }

        void observeNativeQuery(Query query) {
            peakNativePerTick = Math.max(peakNativePerTick, nativePerTick.merge(query.tick(), 1, Integer::sum));
        }

        void assertFirstNativeRound(int alarmTick, int maximumTicks) {
            int first = Integer.MAX_VALUE, last = Integer.MIN_VALUE;
            List<Integer> delays = new ArrayList<>();
            for (TestChicken chicken : chickens) {
                int received = chicken.recording().firstEscapeTick();
                h.assertTrue(received != Integer.MAX_VALUE,
                        "Every alarmed chicken, including the back of the fixed entity tick order, receives native work in the first "
                                + maximumTicks + " world ticks: " + describe(chicken));
                delays.add(received - alarmTick); first = Math.min(first, received); last = Math.max(last, received);
            }
            h.assertTrue(last - alarmTick <= maximumTicks && last - first <= maximumTicks,
                    "The first actual A* admission for all 24 chickens has a bounded span rather than merely finishing eventually: delays=" + delays);
            System.out.println("ANIMAL FLEE FIRST ROUND animals=" + chickens.size() + ", firstQueryDelays=" + delays
                    + ", span=" + (last - first) + ", budget=" + MiteConfig.animalSearchBudgetEnabled());
        }

        void assertObservedNativeLimits() {
            if (!MiteConfig.animalSearchBudgetEnabled()) return;
            for (TestChicken chicken : chickens) if (chicken.recording().peakEscapePerTick > 1)
                h.assertTrue(false, "Actual native A* exceeds the one-query animal tick ceiling: peak=" + chicken.recording().peakEscapePerTick
                        + ", observed=" + chicken.recording().escapePerTick + ", " + describe(chicken));
            if (peakNativePerTick > 5) h.assertTrue(false,
                    "Actual native A* exceeds the simultaneous herd tick ceiling of four ordinary plus one urgent query: peak="
                            + peakNativePerTick + ", observed=" + nativePerTick);
        }

        void assertNativeLimits() {
            assertObservedNativeLimits();
            Map<Integer, Integer> total = new HashMap<>();
            for (TestChicken chicken : chickens) {
                Map<Integer, Integer> perAnimal = new HashMap<>();
                for (Query query : chicken.recording().queries) if (query.escaping()) {
                    total.merge(query.tick(), 1, Integer::sum); perAnimal.merge(query.tick(), 1, Integer::sum);
                }
                if (MiteConfig.animalSearchBudgetEnabled()) h.assertTrue(perAnimal.values().stream().allMatch(count -> count <= 1),
                        "One animal enters actual native PathFinder at most once per server tick, including replans and recomputations: " + describe(chicken) + ", perTick=" + perAnimal);
            }
            if (MiteConfig.animalSearchBudgetEnabled()) {
                h.assertTrue(total.values().stream().allMatch(count -> count <= 5),
                        "The whole simultaneous herd shares four ordinary plus one directly-hit urgent native searches per server tick: " + total);
                var stats = AnimalFleeBudget.stats(h.getLevel().getServer());
                h.assertTrue(stats.peakTickNativeQueries() <= 5 && stats.tickUrgentQueries() <= 1,
                        "Server-wide production counters independently obey the shared native-search ceiling: " + stats);
            }
        }

        String describe(TestChicken chicken) {
            return "id=" + chicken.getId() + ", tick=" + tick() + ", pos=" + chicken.position().subtract(Vec3.atBottomCenterOf(origin))
                    + ", realTicks=" + chicken.nativeTicks + ", movement=" + chicken.furthest
                    + ", nativeQueries=" + chicken.recording().escapeQueries() + ", lastNative=" + chicken.recording().lastEscapeTick()
                    + ", fear=" + R196AnimalAi032.state(chicken) + ", navigation=" + chicken.getNavigation().getPath();
        }

        void report(String scenario) {
            long queries = chickens.stream().mapToLong(c -> c.recording().escapeQueries()).sum();
            long nanos = chickens.stream().flatMap(c -> c.recording().queries.stream()).filter(Query::escaping).mapToLong(Query::nanos).sum();
            System.out.println("ANIMAL FLEE REAL ASTAR scenario=" + scenario + ", budget=" + MiteConfig.animalSearchBudgetEnabled()
                    + ", animals=" + chickens.size() + ", actualNativeQueries=" + queries + ", nativeNanos=" + nanos
                    + ", stats=" + AnimalFleeBudget.stats(h.getLevel().getServer()));
            if (scenario.equals("failure")) chickens.forEach(c -> System.out.println("ANIMAL FLEE FAILURE " + describe(c)));
        }

        @Override public void close() {
            if (closed) return; closed = true;
            chickens.forEach(Chicken::discard);
            for (int i = 0; i < players.size(); i++) {
                players.get(i).server.getPlayerList().remove(players.get(i)); channels.get(i).finishAndReleaseAll();
            }
            for (ChunkPos chunk : tickingChunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, key);
            for (ChunkPos chunk : loadedChunks) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 0, key);
        }
    }
}
