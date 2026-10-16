package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.R196SpawnRules;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** Real global population accounting and player-area gates, independent of creature spawning luck. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class HostilePopulationGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.<Integer>create("mite_population_test", Comparator.naturalOrder());

    private HostilePopulationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void ordinaryTotalCapIncludesTheOppositeHeightBandBeforeEitherPassConsidersChunks(GameTestHelper helper) {
        ready(helper, arena -> fullPopulationStopsBothPasses(helper, arena, false));
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void bloodDateTotalCapIsTwiceThePinnedVanillaCapForBothDayAndNight(GameTestHelper helper) {
        ready(helper, arena -> fullPopulationStopsBothPasses(helper, arena, true));
    }

    private static void fullPopulationStopsBothPasses(GameTestHelper helper, Arena arena, boolean blood) {
        helper.assertValueEqual(MobCategory.MONSTER.getMaxInstancesPerChunk(), 70,
                "The pinned1.21.1 native monster category supplies the independent seventy baseline");
        for (int hour : List.of(6000, 18000)) for (boolean occupantsAreDeep : List.of(false, true)) {
            long previousTime = arena.level.getDayTime();
            BloodMoonInvasionData progress = BloodMoonInvasionData.get(arena.level);
            long previousHighestDay = progress.highestDay(); boolean previousDirty = progress.isDirty();
            try {
                arena.level.setDayTime((blood ? 31L * 24_000L : 0L) + hour);
                int before = R196MobSpawning.count(arena.level, Category.MONSTER, null);
                int perPlayer = blood ? 140 : 105;
                int areas = before / perPlayer + 1;
                int target = perPlayer * areas;
                var added = new ArrayList<Mob>();
                try {
                    for (int index = before; index < target; index++) added.add(arena.creeper(occupantsAreDeep ? -12 : 100));
                    helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), target,
                            "Real native entity indexing reaches exactly the independent adapted population ceiling");
                    // A completely full global population must refuse the whole candidate search,
                    // even when that population sits in the other height band. A traversal here
                    // is a failed cap, not an absence of lucky terrain or spawnable creatures.
                    Map<Long, Boolean> noCandidateSearch = new AbstractMap<>() {
                        @Override public int size() { return 289 * areas; }
                        @Override public Set<Map.Entry<Long, Boolean>> entrySet() {
                            throw new AssertionError("A full shared monster budget must not search candidate chunks");
                        }
                    };
                    for (boolean passIsDeep : List.of(false, true))
                        helper.assertValueEqual(R196MobSpawning.spawnPass(arena.level, Category.MONSTER, passIsDeep,
                                noCandidateSearch, false), 0, "The real spawning entrypoint respects the whole population in both height passes");
                    helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), target,
                            "Neither pass manufactures an extra mob at the shared population boundary");
                } finally { added.forEach(Mob::discard); }
            } finally {
                arena.level.setDayTime(previousTime);
                progress.restoreProgress(previousHighestDay); progress.setDirty(previousDirty);
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    @SuppressWarnings("removal")
    public static void twentyNearbyMonstersNoLongerEraseThePlayersWholeSpawningArea(GameTestHelper helper) {
        ready(helper, arena -> {
            long previousTime = arena.level.getDayTime();
            BloodMoonInvasionData progress = BloodMoonInvasionData.get(arena.level);
            long previousHighestDay = progress.highestDay(); boolean previousDirty = progress.isDirty();
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            var added = new ArrayList<Mob>();
            try {
                player.moveTo(arena.position.getCenter());
                for (int index = 0; index < 20; index++) added.add(arena.creeper(100));
                helper.assertValueEqual(arena.level.getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(32, 8, 32), mob -> R196MobSpawning.category(mob) == Category.MONSTER).size(), 20,
                        "Twenty actual nearby monsters exceed the old8/12 source crowd guard");
                for (long time : List.of(6000L, 31L * 24_000L + 6000L, 31L * 24_000L + 18000L)) {
                    arena.level.setDayTime(time);
                    Map<Long, Boolean> chunks = R196MobSpawning.eligibleChunks(arena.level, true);
                    int found = 0, interior = 0;
                    for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                        Boolean edge = chunks.get(ChunkPos.asLong(arena.chunk.x + dx, arena.chunk.z + dz));
                        helper.assertTrue(edge != null, "The live player retains each chunk in its seventeen-square spawning area");
                        found++; if (!edge) interior++;
                    }
                    helper.assertValueEqual(found, 289, "The scaling denominator includes all289 player-area chunks");
                    helper.assertValueEqual(interior, 225, "Only225 interior chunks provide spawn sampling positions");
                }
            } finally {
                added.forEach(Mob::discard);
                arena.level.getServer().getPlayerList().remove(player);
                arena.level.setDayTime(previousTime);
                progress.restoreProgress(previousHighestDay); progress.setDirty(previousDirty);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void oneRemainingMonsterPlaceRollsBackTheRealPreAddedSpiderRiderAndItsUnaddedParent(GameTestHelper helper) {
        ready(helper, arena -> savedProgress(arena, () -> {
            int before = R196MobSpawning.count(arena.level, Category.MONSTER, null);
            Spider spider = arena.spider();
            try (var trace = new RiderTrace(spider, false, false)) {
                arena.level.getRandom().setSeed(jockeySeed());
                var result = R196MobSpawning.finalizeNaturalMonster(arena.level, spider, null, 1);
                helper.assertValueEqual(trace.riderJoins(), 1, "The actual source1percent callback really pre-added a skeleton before the budget decision");
                helper.assertValueEqual(result.monsters(), 0, "One remaining place cannot admit a two-monster spider jockey");
                helper.assertTrue(spider.isRemoved() && trace.acceptedRiders().stream().allMatch(Mob::isRemoved),
                        "The rejected spawn discards both the not-yet-added parent and its already registered rider");
                helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), before,
                        "The rejected complete tree leaves no orphan skeleton in actual global accounting");
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void twoRemainingPlacesAdmitARealJockeyOnceAndItsReturnedCostLeavesTheExactNextBudget(GameTestHelper helper) {
        ready(helper, arena -> savedProgress(arena, () -> {
            int before = R196MobSpawning.count(arena.level, Category.MONSTER, null);
            Spider spider = arena.spider();
            try (var trace = new RiderTrace(spider, false, false)) {
                arena.level.getRandom().setSeed(jockeySeed());
                var result = R196MobSpawning.finalizeNaturalMonster(arena.level, spider, null, 2);
                helper.assertValueEqual(result.monsters(), 2, "The canonical spawn transaction charges both actual MONSTER nodes");
                helper.assertValueEqual(trace.riderJoins(), 1, "The source-pre-added rider is not submitted to native add/join again");
                helper.assertValueEqual(trace.parentJoins, 1, "The previously unadded parent enters the world exactly once");
                helper.assertTrue(spider.isAddedToLevel() && spider.getFirstPassenger() instanceof AbstractSkeleton rider
                        && rider.isAddedToLevel() && !rider.isRemoved(), "Both real native entities retain their mounted relationship");
                helper.assertTrue(spider.getSpawnType() == MobSpawnType.NATURAL
                        && trace.acceptedRiders().stream().allMatch(rider -> rider.getSpawnType() == MobSpawnType.NATURAL),
                        "Both the native source rider and its parent retain the real natural-spawn finalization reason");
                helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), before + 2,
                        "The real global population increased by two, not the old main-entity count of one");
                int remaining = 3 - result.monsters();
                Mob next = arena.fresh(EntityType.CREEPER, 100);
                var nextResult = R196MobSpawning.finalizeNaturalMonster(arena.level, next, null, remaining);
                helper.assertValueEqual(nextResult.monsters(), 1, "Exactly one remaining place admits the next solitary monster");
                remaining -= nextResult.monsters();
                helper.assertValueEqual(remaining, 0, "Returned node counts spend the complete three-place shared allowance");
                Mob rejected = arena.fresh(EntityType.CREEPER, 100);
                helper.assertValueEqual(R196MobSpawning.finalizeNaturalMonster(arena.level, rejected, null, remaining).monsters(), 0,
                        "The next attempt cannot create a fourth monster after the jockey consumed two places");
                helper.assertTrue(rejected.isRemoved(), "The zero-budget attempt retains no unadded live fixture");
                helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), before + 3,
                        "Final real population matches all three committed MONSTER nodes");
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void canceledParentAddCleansItsPreAddedRiderEvenWhenNativeRemovalDetachesIt(GameTestHelper helper) {
        ready(helper, arena -> savedProgress(arena, () -> {
            int before = R196MobSpawning.count(arena.level, Category.MONSTER, null);
            Spider spider = arena.spider();
            try (var trace = new RiderTrace(spider, true, false)) {
                arena.level.getRandom().setSeed(jockeySeed());
                var result = R196MobSpawning.finalizeNaturalMonster(arena.level, spider, null, 2);
                helper.assertValueEqual(trace.riderJoins(), 1, "The real source rider entered the world before the parent add failed");
                helper.assertValueEqual(trace.parentJoins, 1, "The native parent add callback was actually attempted and canceled");
                helper.assertValueEqual(result.monsters(), 0, "A canceled parent add cannot commit a partial jockey transaction");
                helper.assertTrue(spider.isRemoved() && spider.getPassengers().isEmpty()
                        && trace.acceptedRiders().stream().allMatch(Mob::isRemoved),
                        "The pre-add tree snapshot cleans the detached rider after parent removal");
                helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), before,
                        "Failed parent insertion leaves no orphan monster or phantom budget charge");
            }
        }));
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void canceledNativeFinalizationNeverCommitsOrChargesAParentMonster(GameTestHelper helper) {
        ready(helper, arena -> savedProgress(arena, () -> {
            int before = R196MobSpawning.count(arena.level, Category.MONSTER, null);
            Spider spider = arena.spider();
            try (var trace = new RiderTrace(spider, false, true)) {
                arena.level.getRandom().setSeed(jockeySeed());
                helper.assertValueEqual(R196MobSpawning.finalizeNaturalMonster(arena.level, spider, null, 2).monsters(), 0,
                        "Real NeoForge canceled finalization and spawn cancellation are respected by the transaction");
                helper.assertTrue(spider.isRemoved() && trace.riderJoins() == 0 && trace.parentJoins == 0,
                        "Canceled finalization runs neither the source rider callback nor the final parent insertion");
                helper.assertValueEqual(R196MobSpawning.count(arena.level, Category.MONSTER, null), before,
                        "A canceled transaction leaves real global population unchanged");
            }
        }));
    }

    private static long jockeySeed() {
        for (long seed = 0; seed < 10_000; seed++) if (new Random(seed).nextInt(100) == 0) return seed;
        throw new AssertionError("Independent java.util.Random oracle produces a source spider jockey");
    }

    private static void savedProgress(Arena arena, Runnable test) {
        BloodMoonInvasionData progress = BloodMoonInvasionData.get(arena.level);
        long highestDay = progress.highestDay(); boolean dirty = progress.isDirty();
        try { test.run(); } finally { progress.restoreProgress(highestDay); progress.setDirty(dirty); }
    }

    /** Test-only event observation; production accounting snapshots only its own actual passenger tree. */
    private static final class RiderTrace implements AutoCloseable {
        final Spider parent;
        final List<AbstractSkeleton> candidates = new ArrayList<>();
        final Map<AbstractSkeleton, Integer> joins = new IdentityHashMap<>();
        final Consumer<FinalizeSpawnEvent> finalization;
        final Consumer<EntityJoinLevelEvent> joining;
        int parentJoins;

        RiderTrace(Spider parent, boolean cancelParentAdd, boolean cancelFinalization) {
            this.parent = parent;
            finalization = event -> {
                if (event.getEntity() == parent && cancelFinalization) {
                    event.setCanceled(true); event.setSpawnCancelled(true);
                }
                if (event.getEntity().level() == parent.level() && event.getEntity() instanceof AbstractSkeleton skeleton
                        && skeleton.position().distanceToSqr(parent.position()) < 1.0E-8) candidates.add(skeleton);
            };
            joining = event -> {
                if (event.getEntity() == parent) {
                    parentJoins++;
                    if (cancelParentAdd) {
                        // The actual native removal immediately ejects the already-mounted
                        // rider; the production transaction must use its earlier tree snapshot.
                        parent.discard(); event.setCanceled(true);
                    }
                }
                if (!event.isCanceled() && event.getEntity() instanceof AbstractSkeleton skeleton && candidates.contains(skeleton))
                    joins.merge(skeleton, 1, Integer::sum);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FinalizeSpawnEvent.class, finalization);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, joining);
        }

        int riderJoins() { return joins.values().stream().mapToInt(Integer::intValue).sum(); }
        List<AbstractSkeleton> acceptedRiders() { return List.copyOf(joins.keySet()); }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(finalization); NeoForge.EVENT_BUS.unregister(joining);
            candidates.forEach(Mob::discard); parent.discard();
        }
    }

    private static void ready(GameTestHelper helper, Consumer<Arena> test) {
        Arena arena = new Arena(helper.getLevel());
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { arena.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { arena.close(); }
        });
        helper.startSequence().thenWaitUntil(() -> {
            if (!arena.level.isPositionEntityTicking(arena.position))
                for (int tasks = 0; tasks < 64 && arena.level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(arena.level.areEntitiesLoaded(arena.chunk.toLong()) && arena.level.isPositionEntityTicking(arena.position),
                    "The native isolated entity section is ready before real population accounting");
        }).thenExecute(() -> {
            try { test.accept(arena); } finally { arena.close(); }
        }).thenSucceed();
    }

    private static final class Arena implements AutoCloseable {
        final ServerLevel level;
        final int ticketKey = SITES.incrementAndGet();
        final ChunkPos chunk = new ChunkPos(1500 + ticketKey * 8, 1500);
        final BlockPos position = new BlockPos(chunk.getMinBlockX() + 8, 100, chunk.getMinBlockZ() + 8);
        final List<Mob> mobs = new ArrayList<>();
        boolean closed;

        Arena(ServerLevel level) {
            this.level = level;
            level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticketKey);
            for (int x = chunk.x - 2; x <= chunk.x + 2; x++) for (int z = chunk.z - 2; z <= chunk.z + 2; z++)
                level.getChunk(x, z, ChunkStatus.FULL, true);
        }

        Mob creeper(int y) {
            Mob mob = fresh(EntityType.CREEPER, y);
            if (!level.addFreshEntity(mob)) throw new AssertionError("Native population fixture enters the ready level");
            return mob;
        }

        Spider spider() { return (Spider)fresh(EntityType.SPIDER, 100); }

        Mob fresh(EntityType<? extends Mob> type, int y) {
            Mob mob = type.create(level);
            if (mob == null) throw new AssertionError("Native creeper fixture creates");
            mob.moveTo(position.getX() + .5D, y, position.getZ() + .5D, 0F, 0F);
            mob.setNoAi(true); mob.setNoGravity(true);
            mobs.add(mob); return mob;
        }

        @Override public void close() {
            if (closed) return;
            closed = true; mobs.forEach(Mob::discard);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticketKey);
        }
    }
}
