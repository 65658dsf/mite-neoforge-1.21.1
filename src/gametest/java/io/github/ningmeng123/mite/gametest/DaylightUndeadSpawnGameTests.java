package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Current blood dates alone relax the shared outdoor undead daylight gate. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class DaylightUndeadSpawnGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final TicketType<Integer> LIGHT_TICKET = TicketType.create("mite_undead_spawn_light_test", Comparator.<Integer>naturalOrder());
    private static final long BLOOD_DAY = 31L * 24_000L;

    private DaylightUndeadSpawnGameTests() {}

    @GameTest(template = "empty")
    public static void sourceAndModernUndeadShareTheCurrentBloodDateDaylightRestriction(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), false)) {
            List<Mob> undead = new ArrayList<>();
            for (EntityType<? extends Mob> type : List.of(EntityType.ZOMBIE, EntityType.SKELETON,
                    EntityType.HUSK, EntityType.STRAY, EntityType.BOGGED, EntityType.DROWNED)) {
                Mob mob = site.mob(type);
                helper.assertTrue(mob.getType().is(EntityTypeTags.UNDEAD), "The real native undead tag covers " + type);
                helper.assertTrue(mob.getType().is(R196MobSpawning.DAYLIGHT_RESTRICTED_UNDEAD),
                        "The configurable spawn category includes the native undead category: " + type);
                undead.add(mob);
            }
            for (MobKind kind : List.of(MobKind.GHOUL, MobKind.INVISIBLE_STALKER, MobKind.REVENANT, MobKind.LONGDEAD,
                    MobKind.ANCIENT_BONE_LORD, MobKind.NIGHTWING)) {
                Mob mob = site.mob(MiteMobs.type(kind));
                helper.assertTrue(mob.getType().is(R196MobSpawning.DAYLIGHT_RESTRICTED_UNDEAD),
                        "The configurable spawn category includes source " + kind);
                undead.add(mob);
            }
            Mob spider = site.mob(EntityType.SPIDER);
            helper.assertFalse(spider.getType().is(R196MobSpawning.DAYLIGHT_RESTRICTED_UNDEAD),
                    "An ordinary spider remains outside the undead spawn category");
            helper.assertTrue(R196OutdoorChecks.outdoors(site.level, site.pos), "The shared habitat is genuinely outdoors");
            for (long time : List.of(6_000L, BLOOD_DAY + 6_000L, 18_000L)) {
                progress.time(time);
                boolean allowed = time != 6_000L;
                for (Mob mob : undead)
                    helper.assertTrue(R196MobSpawning.naturalDaylightAllows(mob, site.level) == allowed,
                            "The actual time controls outdoor eligibility for " + mob.getType() + " at " + time);
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(spider, site.level),
                        "The undead restriction does not reject an ordinary spider at " + time);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bloodDateEndBlueMoonAndPermanentInvasionUnlockCannotKeepDaylightUndeadEnabled(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), false)) {
            Mob zombie = site.mob(EntityType.ZOMBIE);
            progress.time(BLOOD_DAY + 6_000L);
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "Actual blood-date daylight is eligible");
            progress.time(32L * 24_000L + 6_000L);
            helper.assertFalse(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "The next ordinary date immediately loses the exception");
            progress.time(96L * 24_000L + 6_000L);
            helper.assertTrue(BloodMoonInvasion.unlocked(site.level), "The fixture reaches the real day97 permanent invasion unlock");
            helper.assertFalse(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "The saved invasion unlock does not turn day97 into a blood date");
            progress.time(127L * 24_000L + 6_000L);
            helper.assertFalse(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "The blue moon on day128 does not count as blood");
            progress.time(6_000L);
            helper.assertTrue(BloodMoonInvasion.unlocked(site.level), "Permanent progression survives the actual time rollback");
            helper.assertFalse(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "Time rollback still uses the current ordinary date");
            progress.time(BLOOD_DAY + 6_000L);
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(zombie, site.level), "Rolling back onto a blood date restores its current-date exception");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enclosedHabitatsAndOtherDimensionsRemainEligibleForTheirOwnSpawnChecks(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site room = new Site(helper.getLevel(), true)) {
            progress.time(6_000L);
            Mob zombie = room.mob(EntityType.ZOMBIE);
            helper.assertFalse(R196OutdoorChecks.outdoors(room.level, room.pos), "A sealed unlit stone room has no short path to an open column");
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(zombie, room.level), "The new outdoor daylight gate leaves an enclosed hostile habitat eligible");
            for (var dimension : List.of(Level.NETHER, Level.END, MiteWorldDimensions.UNDERWORLD)) {
                ServerLevel other = room.level.getServer().getLevel(dimension);
                helper.assertTrue(other != null, "The real test server has dimension " + dimension);
                Mob otherZombie = EntityType.ZOMBIE.create(other);
                helper.assertTrue(otherZombie != null, "The native zombie creates in " + dimension);
                long previousTime = other.getDayTime();
                try {
                    other.setDayTime(6_000L);
                    otherZombie.moveTo(room.pos.getCenter());
                    helper.assertTrue(R196MobSpawning.naturalDaylightAllows(otherZombie, other), "The daylight restriction belongs only to the Overworld: " + dimension);
                } finally { other.setDayTime(previousTime); otherZombie.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalPositionSuccessCannotBypassDaylightButOtherSpawnReasonsKeepTheirEventContract(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), false)) {
            Mob zombie = site.mob(EntityType.ZOMBIE);
            AtomicInteger checks = new AtomicInteger();
            Consumer<MobSpawnEvent.PositionCheck> allow = event -> {
                if (event.getEntity() == zombie) {
                    checks.incrementAndGet();
                    event.setResult(MobSpawnEvent.PositionCheck.Result.SUCCEED);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobSpawnEvent.PositionCheck.class, allow);
            try {
                progress.time(6_000L);
                helper.assertFalse(R196MobSpawning.qualifiesWithEvent(zombie, site.level, MobSpawnType.NATURAL, false, false),
                        "Even a success override cannot admit a natural outdoor undead on an ordinary day");
                helper.assertValueEqual(checks.get(), 0, "The daylight restriction runs before the position-success override");
                for (MobSpawnType reason : List.of(MobSpawnType.SPAWNER, MobSpawnType.CHUNK_GENERATION))
                    helper.assertTrue(R196MobSpawning.qualifiesWithEvent(zombie, site.level, reason, false, false),
                            "Other spawn reasons retain their existing position-event contract: " + reason);
                helper.assertValueEqual(checks.get(), 2, "Each unaffected reason still dispatches its original event");
                progress.time(BLOOD_DAY + 6_000L);
                helper.assertTrue(R196MobSpawning.qualifiesWithEvent(zombie, site.level, MobSpawnType.NATURAL, false, false),
                        "Actual blood-date daylight reaches the original natural position event");
                progress.time(18_000L);
                helper.assertTrue(R196MobSpawning.qualifiesWithEvent(zombie, site.level, MobSpawnType.NATURAL, false, false),
                        "Ordinary nighttime also reaches the original natural position event");
                helper.assertValueEqual(checks.get(), 4, "Eligible natural checks retain their original event calls");
            } finally { NeoForge.EVENT_BUS.unregister(allow); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void denseTreeCanopiesAndOverheadBranchesCannotHideSurfaceBoggedAtEitherElevation(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel())) {
            for (int height : List.of(40, 280)) try (Site site = new Site(helper.getLevel(), false, height)) {
                Mob bogged = site.mob(EntityType.BOGGED);
                site.fill(site.pos.offset(-5, 4, -5), site.pos.offset(5, 4, 5), Blocks.OAK_LEAVES.defaultBlockState());
                helper.assertFalse(R196OutdoorChecks.outdoors(site.level, site.pos),
                        "The eleven-block canopy reproduces the old shaded-surface misclassification at Y=" + height);
                helper.assertTrue(R196OutdoorChecks.surface(site.level, site.pos),
                        "Leaves above the ground do not turn the surface into a cave at Y=" + height);
                progress.time(6_000L);
                helper.assertFalse(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                        "An ordinary day's surface restriction includes a bogged in dense shade");
                helper.assertFalse(R196MobSpawning.qualifiesWithEvent(bogged, site.level, MobSpawnType.NATURAL, true, true),
                        "The actual modern natural-spawn branch rejects a bogged under the canopy");
                site.fill(site.pos.offset(-3, 3, 0), site.pos.offset(3, 3, 0), Blocks.OAK_LOG.defaultBlockState());
                helper.assertTrue(R196OutdoorChecks.surface(site.level, site.pos),
                        "A solid tree branch below the leaves also remains part of the surface habitat");
                helper.assertFalse(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                        "A branch cannot reopen ordinary daytime spawning");
                progress.time(BLOOD_DAY + 6_000L);
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                        "The actual blood date releases the surface gate under both branches and leaves");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void surfaceWaterDoesNotCountAsAnUndergroundRoofForTheDaylightGate(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), false, 40)) {
            Mob bogged = site.mob(EntityType.BOGGED);
            site.fill(site.pos.offset(-4, 0, -4), site.pos.offset(4, 2, 4), Blocks.WATER.defaultBlockState());
            helper.assertTrue(R196OutdoorChecks.surface(site.level, site.pos),
                    "A pure water column above the candidate is a surface water habitat, not an underground roof");
            progress.time(6_000L);
            helper.assertFalse(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                    "Ordinary daytime still applies the surface gate beneath water");
            progress.time(BLOOD_DAY + 6_000L);
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                    "Blood daylight releases only this gate; native bogged water-spawn qualification remains separate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stoneRoofKeepsTheLowerCaveEligibleBesideAnEntranceAtBothElevations(GameTestHelper helper) {
        try (Progress progress = new Progress(helper.getLevel())) {
            progress.time(6_000L);
            for (int height : List.of(40, 280)) try (Site site = new Site(helper.getLevel(), false, height)) {
                site.fill(site.pos.offset(-4, 3, -4), site.pos.offset(1, 3, 4), Blocks.STONE.defaultBlockState());
                site.openColumn(site.pos.east(2));
                Mob lower = site.mob(EntityType.BOGGED), upper = site.mob(EntityType.BOGGED);
                upper.moveTo(site.pos.getX() + .5D, site.pos.getY() + 4.005D, site.pos.getZ() + .5D, 0F, 0F);
                helper.assertTrue(R196OutdoorChecks.outdoors(site.level, lower.blockPosition()),
                        "The old short-path search can reach the nearby cave entrance at Y=" + height);
                helper.assertFalse(R196OutdoorChecks.surface(site.level, lower.blockPosition()),
                        "The real stone ceiling keeps the lower point underground despite the nearby entrance");
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(lower, site.level),
                        "Ordinary daytime preserves underground eligibility at Y=" + height);
                helper.assertTrue(R196OutdoorChecks.surface(site.level, upper.blockPosition()),
                        "The upper point in the same X/Z column stands on the terrain's surface");
                helper.assertFalse(R196MobSpawning.naturalDaylightAllows(upper, site.level),
                        "The same-column surface point remains restricted without a fixed altitude threshold");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void modernSpawnCandidatesCannotStartInsideSolidGrassStoneOrTreeTrunks(GameTestHelper helper) {
        inDarkCave(helper, site -> {
            for (EntityType<? extends Mob> type : List.of(EntityType.BOGGED, EntityType.PILLAGER)) {
                Mob mob = site.mob(type);
                for (var block : List.of(Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.OAK_LOG)) {
                    site.set(site.pos, block.defaultBlockState());
                    helper.assertTrue(R196MobSpawning.naturalDaylightAllows(mob, site.level),
                            "The real underground fixture passes the daylight gate before physical validation");
                    helper.assertFalse(site.level.noCollision(mob), "The full candidate body intersects the solid " + block);
                    helper.assertFalse(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, true),
                            "Modern natural qualification rejects " + type + " inside " + block);
                }
                site.set(site.pos, Blocks.AIR.defaultBlockState());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void modernSpawnCandidatesNeedClearHeadroomEvenWhenTheirFeetAreInAir(GameTestHelper helper) {
        inDarkCave(helper, site -> {
            site.set(site.pos.above(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(site.level.getBlockState(site.pos).isAir(), "Only the head position is obstructed");
            for (EntityType<? extends Mob> type : List.of(EntityType.BOGGED, EntityType.PILLAGER)) {
                Mob mob = site.mob(type);
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(mob, site.level), "Daylight itself permits this underground candidate");
                helper.assertFalse(site.level.noCollision(mob), "The upper body intersects the real head-height block");
                helper.assertFalse(R196MobSpawning.qualifiesWithEvent(mob, site.level, MobSpawnType.NATURAL, true, true),
                        "Modern natural qualification rejects blocked headroom for " + type);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void modernBoggedStillQualifiesInRealDarkCaveAirWithValidGroundDuringOrdinaryDaylight(GameTestHelper helper) {
        inDarkCave(helper, site -> {
            Mob bogged = site.mob(EntityType.BOGGED);
            helper.assertTrue(site.level.getBlockState(site.pos).isAir() && site.level.getBlockState(site.pos.above()).isAir(),
                    "The cave has actual empty foot and head blocks");
            helper.assertTrue(site.level.noCollision(bogged), "The complete bogged body has no block collision");
            helper.assertTrue(SpawnPlacements.isSpawnPositionOk(bogged.getType(), site.level, bogged.blockPosition()),
                    "The native placement type accepts the real stone floor and clear body space");
            helper.assertTrue(R196MobSpawning.naturalDaylightAllows(bogged, site.level),
                    "An ordinary day's surface restriction leaves the real cave eligible");
            helper.assertTrue(R196MobSpawning.qualifiesWithEvent(bogged, site.level, MobSpawnType.NATURAL, true, true),
                    "The actual modern native light, placement and event path still admits an underground bogged");
        });
    }

    private static void inDarkCave(GameTestHelper helper, Consumer<Site> test) {
        Site site = new Site(helper.getLevel(), true, 100);
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> {
            site.requireLightingReady(helper);
            helper.assertValueEqual(site.level.getMaxLocalRawBrightness(site.pos), 0, "The sealed habitat reaches real darkness before qualification");
        }).thenExecute(() -> {
            try (site; Progress progress = new Progress(site.level)) {
                progress.time(6_000L);
                test.accept(site);
            }
        }).thenSucceed();
    }

    private static final class Progress implements AutoCloseable {
        final SpawnPreset preset = new SpawnPreset(false);
        final ServerLevel level;
        final BloodMoonInvasionData data;
        final long time, highestDay;
        final boolean dirty;

        Progress(ServerLevel level) {
            this.level = level.getServer().overworld(); data = BloodMoonInvasionData.get(this.level);
            time = this.level.getDayTime(); highestDay = data.highestDay(); dirty = data.isDirty();
            data.restoreProgress(0L);
        }

        void time(long value) { level.setDayTime(value); level.updateSkyBrightness(); }

        @Override public void close() {
            data.restoreProgress(highestDay); data.setDirty(dirty); time(time);
            BloodMoonInvasionData.get(level);
            preset.close();
        }
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        final List<Mob> mobs = new ArrayList<>();
        CompletableFuture<?> lightBarrier;
        boolean ticketed, closed;

        Site(ServerLevel level, boolean enclosed) {
            this(level, enclosed, level.getMaxBuildHeight() - 20);
        }

        Site(ServerLevel level, boolean enclosed, int height) {
            this.level = level;
            pos = new BlockPos(420_008 + SITES.getAndIncrement() * 32, height, 420_008);
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 4, 4))) {
                boolean shell = cell.getY() == pos.getY() - 1 || enclosed && (cell.getY() == pos.getY() + 4
                        || Math.abs(cell.getX() - pos.getX()) == 4 || Math.abs(cell.getZ() - pos.getZ()) == 4);
                set(cell, shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            if (!enclosed) openColumn(pos.above(5));
        }

        void set(BlockPos point, BlockState state) {
            previous.putIfAbsent(point.immutable(), level.getBlockState(point)); level.setBlock(point, state, 2);
        }

        void fill(BlockPos low, BlockPos high, BlockState state) {
            for (BlockPos point : BlockPos.betweenClosed(low, high)) set(point, state);
        }

        void openColumn(BlockPos from) {
            for (int y = from.getY(); y < level.getMaxBuildHeight(); y++)
                set(new BlockPos(from.getX(), y, from.getZ()), Blocks.AIR.defaultBlockState());
        }

        void keepLoaded(GameTestHelper helper) {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
            try {
                ChunkPos chunk = new ChunkPos(pos);
                level.getChunkSource().addRegionTicket(LIGHT_TICKET, chunk, 2, pos.getX()); ticketed = true;
                for (int x = chunk.x - 2; x <= chunk.x + 2; x++) for (int z = chunk.z - 2; z <= chunk.z + 2; z++)
                    level.getChunk(x, z, ChunkStatus.FULL, true);
                var light = level.getChunkSource().getLightEngine();
                light.checkBlock(pos); light.checkBlock(pos.above(2));
                lightBarrier = light.waitForPendingTasks(chunk.x, chunk.z);
                light.tryScheduleUpdate();
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void requireLightingReady(GameTestHelper helper) {
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
            for (int tasks = 0; tasks < 64 && level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(level.isPositionEntityTicking(pos) && lightBarrier != null && lightBarrier.isDone(),
                    "The isolated cave is ticking and its native lighting tasks have completed");
            lightBarrier.join();
        }

        Mob mob(EntityType<? extends Mob> type) {
            Mob mob = type.create(level);
            if (mob == null) throw new AssertionError("Registered spawn candidate creates: " + type);
            mob.moveTo(pos.getX() + .5D, pos.getY() + .005D, pos.getZ() + .5D, 0F, 0F);
            mob.setNoAi(true); mob.setNoGravity(true); mobs.add(mob); return mob;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { mobs.forEach(Mob::discard); previous.forEach((point, state) -> level.setBlock(point, state, 2)); }
            finally { if (ticketed) level.getChunkSource().removeRegionTicket(LIGHT_TICKET, new ChunkPos(pos), 2, pos.getX()); }
        }
    }
}
