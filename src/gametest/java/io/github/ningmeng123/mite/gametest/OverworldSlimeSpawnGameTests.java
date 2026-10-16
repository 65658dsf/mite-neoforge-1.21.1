package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.MobKind;
import io.github.ningmeng123.mite.entity.R196Cube;
import io.github.ningmeng123.mite.registry.MiteMobs;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasion;
import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.OverworldSlimeSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry;
import io.github.ningmeng123.mite.worldgen.R196SpawnQualification;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real weighted selection and habitat checks for the four ordinary-overworld slimes. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class OverworldSlimeSpawnGameTests {
    private static final List<MobKind> KINDS = List.of(MobKind.JELLY, MobKind.BLOB, MobKind.OOZE, MobKind.PUDDING);
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final TicketType<Integer> LIGHT_TICKET = TicketType.create("mite_slime_habitat_light_test", Comparator.<Integer>naturalOrder());

    private OverworldSlimeSpawnGameTests() {}

    @GameTest(template = "empty")
    public static void everyOverworldBiomeUsesFourIndependentVanillaSizedWeightsWithoutChangingArchivedProfiles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int id = 0; id <= 25; id++) {
            if (id == 8 || id == 9 || id == 14 || id == 15) continue;
            List<Entry> archived = R196MobSpawning.sourceProfile(id, Category.MONSTER);
            List<Entry> pool = OverworldSlimeSpawns.pool(level, Category.MONSTER, archived);
            for (MobKind kind : KINDS) {
                Entry original = named(archived, kind.sourceClass()), adapted = named(pool, kind.sourceClass());
                helper.assertValueEqual(adapted.weight(), 100, "Each slime independently receives the common zombie/skeleton/creeper weight in biome " + id);
                helper.assertValueEqual(pool.stream().filter(entry -> entry.sourceClass().equals(kind.sourceClass())).count(), 1L,
                        "The four slimes do not get duplicated when adapting biome " + id);
                helper.assertTrue(original.minimum() == adapted.minimum() && original.maximum() == adapted.maximum() && original.pack() == adapted.pack(),
                        "Source group/pack sizes survive the new commonness for " + kind);
                helper.assertValueEqual(original.weight(), kind == MobKind.OOZE ? 20 : 30, "The archival weight remains untouched");
            }
            for (Entry original : archived) if (!OverworldSlimeSpawns.species(original.sourceClass()))
                helper.assertTrue(pool.contains(original), "Unrelated original spawn entries survive unchanged in biome " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingSlimesJoinExistingHostilePoolsButEmptyPoolsAnimalsAndOtherDimensionsRemainUntouched(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entry zombie = R196MobSpawning.sourceProfile(1, Category.MONSTER).stream().filter(entry -> entry.type() == EntityType.ZOMBIE).findFirst().orElseThrow();
        List<Entry> source = List.of(zombie), expanded = OverworldSlimeSpawns.pool(level, Category.MONSTER, source);
        helper.assertValueEqual(expanded.size(), 5, "A hostile ecology missing all four slimes receives each one exactly once");
        for (MobKind kind : KINDS) helper.assertValueEqual(named(expanded, kind.sourceClass()).weight(), 100, "Appended species has its independent common weight");
        List<Entry> empty = List.of();
        helper.assertTrue(OverworldSlimeSpawns.pool(level, Category.MONSTER, empty) == empty, "A completely empty hostile pool remains safe");
        for (Category category : List.of(Category.ANIMAL, Category.AMBIENT, Category.AQUATIC))
            helper.assertTrue(OverworldSlimeSpawns.pool(level, category, source) == source, "The adaptation cannot change another creature category");
        for (var dimension : List.of(Level.NETHER, Level.END, MiteWorldDimensions.UNDERWORLD)) {
            ServerLevel other = level.getServer().getLevel(dimension);
            helper.assertTrue(other != null && OverworldSlimeSpawns.pool(other, Category.MONSTER, source) == source,
                    "The runtime adaptation leaves another dimension untouched: " + dimension);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everySlimeTicketSelectsOnTheFirstOrdinaryNightAboveGroundWithoutAStoneRoof(GameTestHelper helper) throws ReflectiveOperationException {
        try (Progress progress = new Progress(helper.getLevel()); Site site = new Site(helper.getLevel(), 270, false)) {
            progress.time(13_000L);
            helper.assertFalse(BloodMoonInvasion.unlocked(site.level), "The first-night test never unlocks the day97 invasion");
            List<Entry> pool = actualPool(site.level, site.pos);
            int total = pool.stream().mapToInt(Entry::weight).sum(), offset = 0, checked = 0;
            for (Entry entry : pool) {
                if (OverworldSlimeSpawns.species(entry.sourceClass())) for (int ticket = offset; ticket < offset + entry.weight(); ticket++) {
                    Entry selected = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, new TicketRandom(total, ticket), false);
                    helper.assertTrue(selected != null && selected.sourceClass().equals(entry.sourceClass()), "Every ordinary slime ticket reaches actual source selection");
                    checked++;
                }
                offset += entry.weight();
            }
            helper.assertValueEqual(checked, 400, "All four independent100-ticket entries are eligible before any blood moon");
            for (MobKind kind : KINDS) {
                R196Cube cube = cube(site, kind);
                try { helper.assertTrue(R196SpawnQualification.qualifies(cube, site.level, false, false), "Grass surface requires neither a slime chunk nor a swamp nor a stone floor: " + kind); }
                finally { cube.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void physicalGroundWallsLeavesAndBothFluidsStillRejectInvalidNaturalHabitats(GameTestHelper helper) {
        try (Site site = new Site(helper.getLevel(), 270, false)) {
            for (MobKind kind : KINDS) {
                R196Cube cube = cube(site, kind);
                try {
                    helper.assertTrue(R196SpawnQualification.qualifies(cube, site.level, false, false), "Solid grass and open room are eligible");
                    site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
                    helper.assertFalse(R196SpawnQualification.qualifies(cube, site.level, false, false), "A slime cannot spawn in unsupported air");
                    site.set(site.pos.below(), Blocks.OAK_LEAVES.defaultBlockState());
                    helper.assertFalse(R196SpawnQualification.qualifies(cube, site.level, false, false), "The normal leaves-ground rejection remains");
                    site.set(site.pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (var obstruction : List.of(Blocks.STONE, Blocks.WATER, Blocks.LAVA)) {
                        site.set(site.pos, obstruction.defaultBlockState());
                        helper.assertFalse(R196SpawnQualification.qualifies(cube, site.level, false, false), "A wall or liquid cannot become a slime spawn volume: " + obstruction);
                    }
                    site.set(site.pos, Blocks.AIR.defaultBlockState());
                } finally { cube.discard(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void ordinaryDaylightRejectsOpenSurfaceWhileNightAllowsEachSlime(GameTestHelper helper) {
        Site site = new Site(helper.getLevel(), 270, false);
        site.keepLoaded(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    site.requireLightingReady(helper);
                    helper.assertValueEqual(site.level.getBrightness(LightLayer.SKY, site.pos), 15, "The actual exposed-sky light has propagated");
                })
                .thenExecute(() -> {
                    try (site; Progress progress = new Progress(site.level)) {
                        for (MobKind kind : KINDS) {
                            R196Cube cube = cube(site, kind);
                            try {
                                progress.time(6_000L);
                                helper.assertFalse(anyNaturalQualification(cube, site), "A bright ordinary day cannot bypass hostile lighting: " + kind);
                                progress.time(18_000L);
                                helper.assertTrue(anyNaturalQualification(cube, site), "A dark ordinary night permits this surface slime without a moon event: " + kind);
                            } finally { cube.discard(); }
                        }
                    }
                }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void darkDirtCavesPermitAllFourSlimesByDayButTorchLightStillPreventsThem(GameTestHelper helper) {
        Site site = new Site(helper.getLevel(), 100, true);
        site.keepLoaded(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    site.requireLightingReady(helper);
                    helper.assertValueEqual(site.level.getMaxLocalRawBrightness(site.pos), 0, "The enclosed dirt cave becomes truly dark");
                })
                .thenExecute(() -> {
                    try (Progress progress = new Progress(site.level)) {
                        progress.time(6_000L);
                        for (MobKind kind : KINDS) {
                            R196Cube cube = cube(site, kind);
                            try { helper.assertTrue(anyNaturalQualification(cube, site), "A dark dirt cave on day1 admits this slime without a stone enclosure: " + kind); }
                            finally { cube.discard(); }
                        }
                    }
                    site.set(site.pos.above(2), Blocks.GLOWSTONE.defaultBlockState());
                    site.queueLightingBarrier();
                })
                .thenWaitUntil(() -> {
                    site.requireLightingReady(helper);
                    helper.assertTrue(site.level.getBrightness(LightLayer.BLOCK, site.pos) >= 8, "The actual luminous block has propagated");
                })
                .thenExecute(() -> {
                    try (site; Progress progress = new Progress(site.level)) {
                        progress.time(6_000L);
                        for (MobKind kind : KINDS) {
                            R196Cube cube = cube(site, kind);
                            try { helper.assertFalse(anyNaturalQualification(cube, site), "Ordinary hostile lighting still prevents this slime in a lit cave: " + kind); }
                            finally { cube.discard(); }
                        }
                    }
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void underworldOozeAndPuddingKeepTheirOriginalStoneGroundRequirement(GameTestHelper helper) {
        ServerLevel underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        try (Site site = new Site(underworld, 100, true)) {
            for (MobKind kind : List.of(MobKind.OOZE, MobKind.PUDDING)) {
                R196Cube cube = cube(site, kind);
                try {
                    helper.assertFalse(R196SpawnQualification.qualifies(cube, site.level, false, false), "The old underworld stone-floor condition still rejects dirt: " + kind);
                    site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
                    helper.assertTrue(R196SpawnQualification.qualifies(cube, site.level, false, false), "Original underworld stone ground remains allowed: " + kind);
                    site.set(site.pos.below(), Blocks.DIRT.defaultBlockState());
                } finally { cube.discard(); }
            }
        }
        helper.succeed();
    }

    private static boolean anyNaturalQualification(R196Cube cube, Site site) {
        for (int seed = 0; seed < 64; seed++) {
            cube.getRandom().setSeed(seed);
            if (R196SpawnQualification.qualifies(cube, site.level, true, false)) return true;
        }
        return false;
    }

    private static R196Cube cube(Site site, MobKind kind) {
        R196Cube cube = (R196Cube)MiteMobs.type(kind).create(site.level);
        cube.setSize(1, true); cube.setNoAi(true); cube.setNoGravity(true);
        cube.moveTo(site.pos.getX() + .5D, site.pos.getY() + .005D, site.pos.getZ() + .5D, 0F, 0F);
        return cube;
    }

    private static Entry named(List<Entry> pool, String species) {
        return pool.stream().filter(entry -> species.equals(entry.sourceClass())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Entry> actualPool(ServerLevel level, BlockPos pos) throws ReflectiveOperationException {
        Method method = R196MobSpawning.class.getDeclaredMethod("pool", ServerLevel.class, BlockPos.class, Category.class, boolean.class);
        method.setAccessible(true);
        return (List<Entry>)method.invoke(null, level, pos, Category.MONSTER, false);
    }

    private static final class TicketRandom extends LegacyRandomSource {
        private final int total, ticket;
        TicketRandom(int total, int ticket) { super(0L); this.total = total; this.ticket = ticket; }
        @Override public int nextInt(int bound) { return bound == total ? ticket : bound - 1; }
        @Override public float nextFloat() { return .75F; }
    }

    private static final class Progress implements AutoCloseable {
        final ServerLevel level;
        private final BloodMoonInvasionData data;
        private final long time, highestDay;
        private final boolean dirty;
        Progress(ServerLevel level) {
            this.level = level.getServer().overworld(); data = BloodMoonInvasionData.get(this.level);
            time = this.level.getDayTime(); highestDay = data.highestDay(); dirty = data.isDirty();
            data.restoreProgress(0L); time(0L);
        }
        void time(long value) { level.setDayTime(value); level.updateSkyBrightness(); }
        @Override public void close() { data.restoreProgress(highestDay); data.setDirty(dirty); time(time); BloodMoonInvasionData.get(level); }
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        private final Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        private CompletableFuture<?> lightBarrier;
        private boolean ticketed, closed;
        Site(ServerLevel level, int y, boolean enclosed) {
            this.level = level; pos = new BlockPos(360_008 + SITES.getAndIncrement() * 32, y, 360_008);
            for (BlockPos cell : BlockPos.betweenClosed(pos.offset(-4, -1, -4), pos.offset(4, 4, 4))) {
                boolean shell = enclosed && (cell.getY() == pos.getY() + 4
                        || Math.abs(cell.getX() - pos.getX()) == 4 || Math.abs(cell.getZ() - pos.getZ()) == 4);
                set(cell, cell.getY() == pos.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : shell ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            if (!enclosed) for (int yAbove = y + 5; yAbove < level.getMaxBuildHeight(); yAbove++) {
                BlockPos above = new BlockPos(pos.getX(), yAbove, pos.getZ());
                if (!level.getBlockState(above).isAir()) set(above, Blocks.AIR.defaultBlockState());
            }
        }
        void set(BlockPos pos, BlockState state) { previous.putIfAbsent(pos.immutable(), level.getBlockState(pos)); level.setBlock(pos, state, 2); }
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
                // The remote habitat is outside the test template's own tickets. Native chunk
                // promotion needs its radius-two FULL neighbors before lighting may advance.
                for (int x = chunk.x - 2; x <= chunk.x + 2; x++) for (int z = chunk.z - 2; z <= chunk.z + 2; z++)
                    level.getChunk(x, z, ChunkStatus.FULL, true);
                queueLightingBarrier();
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void queueLightingBarrier() {
            ChunkPos chunk = new ChunkPos(pos);
            var light = level.getChunkSource().getLightEngine();
            light.checkBlock(pos); light.checkBlock(pos.above(2));
            lightBarrier = light.waitForPendingTasks(chunk.x, chunk.z);
            light.tryScheduleUpdate();
        }
        void requireLightingReady(GameTestHelper helper) {
            level.getChunkSource().getLightEngine().tryScheduleUpdate();
            for (int tasks = 0; tasks < 64 && level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(level.isPositionEntityTicking(pos) && lightBarrier != null && lightBarrier.isDone(),
                    "The isolated habitat reaches native ticking status and completes its real lighting tasks");
            lightBarrier.join();
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try { previous.forEach((pos, state) -> level.setBlock(pos, state, 2)); }
            finally { if (ticketed) level.getChunkSource().removeRegionTicket(LIGHT_TICKET, new ChunkPos(pos), 2, pos.getX()); }
        }
    }
}
