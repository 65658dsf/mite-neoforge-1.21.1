package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native modern hostile habitats remain candidates in the source scheduler. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class ModernHostileSpawningGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();

    private ModernHostileSpawningGameTests() {}

    @GameTest(template = "empty")
    public static void referenceDesertSnowAndSwampRetainTheirNativeHostileVariants(GameTestHelper helper) {
        try (var preset = new SpawnPreset(true)) {
            assertHabitat(helper, helper.getLevel(), Biomes.DESERT, Set.of(EntityType.HUSK));
            assertHabitat(helper, helper.getLevel(), Biomes.SNOWY_PLAINS, Set.of(EntityType.STRAY));
            assertHabitat(helper, helper.getLevel(), Biomes.SWAMP, Set.of(EntityType.BOGGED));
            assertHabitat(helper, helper.getLevel(), Biomes.MANGROVE_SWAMP, Set.of(EntityType.BOGGED));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void referenceNetherRetainsNativePiglinAndHoglinHabitats(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The native Nether dimension is available");
        try (var preset = new SpawnPreset(true)) {
            assertHabitat(helper, nether, Biomes.NETHER_WASTES, Set.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE));
            assertHabitat(helper, nether, Biomes.CRIMSON_FOREST, Set.of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN));
            assertHabitat(helper, nether, Biomes.WARPED_FOREST, Set.of());
            try (var warped = new Habitat(nether, Biomes.WARPED_FOREST)) {
                Set<EntityType<?>> selected = warped.selected(helper);
                helper.assertFalse(selected.contains(EntityType.PIGLIN), "Warped forest has no native piglin candidate");
                helper.assertFalse(selected.contains(EntityType.PIGLIN_BRUTE), "Warped forest has no native piglin brute candidate");
                helper.assertFalse(selected.contains(EntityType.HOGLIN), "Warped forest has no native hoglin candidate");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void naturalOverworldBrutesAreRareAndRetainTheirIdentityAfterSaving(GameTestHelper helper) {
        var site = new DaylightUndeadSpawnGameTests.Site(helper.getLevel(), true, 100);
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            try (site; var progress = new DaylightUndeadSpawnGameTests.Progress(site.level);
                    var plains = new Habitat(site.level, site.pos, Biomes.PLAINS);
                    var mushrooms = new Habitat(site.level, Biomes.MUSHROOM_FIELDS)) {
                progress.time(223L * 24_000L + 18_000L);
                progress.data.restoreProgress(224L);
                var bruteEntries = plains.nativePool().stream().filter(entry -> entry.type == EntityType.PIGLIN_BRUTE).toList();
                helper.assertValueEqual(bruteEntries.size(), 1, "Plains have exactly one native brute spawn entry");
                var bruteEntry = bruteEntries.getFirst();
                helper.assertValueEqual(bruteEntry.getWeight().asInt(), 2, "The default Overworld weight matches the zombie lord's weight");
                helper.assertTrue(bruteEntry.minCount == 1 && bruteEntry.maxCount == 1, "The Overworld brute has a singleton spawn group");
                helper.assertFalse(mushrooms.nativeCandidates().contains(EntityType.PIGLIN_BRUTE),
                        "Mushroom fields retain their existing exclusion from ordinary brute spawns");

                var selected = findBrute(plains);
                helper.assertTrue(selected != null && selected.modern() && selected.minimum() == 1 && selected.maximum() == 1
                                && selected.pack() == 1,
                        "The actual natural scheduler can select a solitary Overworld brute");
                var brute = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                helper.assertTrue(qualifies(brute, site.level), "The natural brute passes real dark ground, collision, and position checks");
                var result = R196MobSpawning.finalizeNaturalMonster(site.level, brute, null, 1);
                helper.assertTrue(result.monsters() == 1 && brute.isAddedToLevel() && brute.getSpawnType() == MobSpawnType.NATURAL,
                        "A selected Overworld brute enters the real world as one natural monster");
                var saved = brute.saveWithoutId(new CompoundTag());
                helper.assertFalse(saved.getBoolean("IsImmuneToZombification"), "The natural spawn does not need a permanent immunity flag");
                saved.putInt("TimeInOverworld", 300);
                var restored = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                restored.load(saved);
                restored.setNoAi(false);
                helper.assertFalse(restored.isConverting(), "An Overworld brute remains protected after loading without an immunity flag");
                restored.tick();
                helper.assertTrue(restored.isAlive() && restored.saveWithoutId(new CompoundTag()).getInt("TimeInOverworld") == 0,
                        "The Overworld brute clears the old conversion timer during its real server tick");

                var summoned = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                EventHooks.finalizeMobSpawn(summoned, site.level, site.level.getCurrentDifficultyAt(site.pos), MobSpawnType.COMMAND, null);
                summoned.setNoAi(false);
                helper.assertFalse(summoned.isConverting(), "A command-spawned brute also remains protected in the Overworld");

                var piglin = (Piglin)site.mob(EntityType.PIGLIN);
                piglin.setNoAi(false);
                helper.assertTrue(piglin.isConverting(), "An ordinary piglin still starts converting in the Overworld");

                var endBrute = EntityType.PIGLIN_BRUTE.create(site.level.getServer().getLevel(Level.END));
                helper.assertTrue(endBrute != null && endBrute.isConverting(),
                        "A brute in another unsafe dimension still follows native conversion rules");
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void naturalNetherBrutesUsePiglinGroundRulesAndStayBrutesInTheOverworld(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var site = new DaylightUndeadSpawnGameTests.Site(nether, true, 88);
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            try (site; var wastes = new Habitat(nether, site.pos, Biomes.NETHER_WASTES)) {
                var selected = findBrute(wastes);
                helper.assertTrue(selected != null && selected.pack() == 1, "The real Nether wastes natural scheduler selects a solitary brute");
                var brute = (PiglinBrute)site.mob(EntityType.PIGLIN_BRUTE);
                site.set(site.pos.below(), Blocks.NETHER_WART_BLOCK.defaultBlockState());
                helper.assertFalse(qualifies(brute, nether), "A natural brute rejects the same wart floor as an ordinary piglin");
                site.set(site.pos.below(), Blocks.NETHERRACK.defaultBlockState());
                helper.assertTrue(qualifies(brute, nether), "A real netherrack floor admits the natural brute");
                var result = R196MobSpawning.finalizeNaturalMonster(nether, brute, null, 1);
                helper.assertTrue(result.monsters() == 1 && brute.isAddedToLevel() && brute.getSpawnType() == MobSpawnType.NATURAL,
                        "The selected Nether brute joins the real world through natural finalization");
                brute.setNoAi(false);
                helper.assertFalse(brute.isConverting(), "The Nether still protects brutes through its native dimension rules");
                var destination = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));
                var moved = brute.changeDimension(new DimensionTransition(helper.getLevel(), destination, Vec3.ZERO,
                        0F, 0F, DimensionTransition.DO_NOTHING));
                try {
                    helper.assertTrue(moved instanceof PiglinBrute && moved != brute, "The real dimension transfer restores a brute in the Overworld");
                    var transferred = (PiglinBrute)moved;
                    transferred.setNoAi(false);
                    helper.assertTrue(!transferred.saveWithoutId(new CompoundTag()).getBoolean("IsImmuneToZombification")
                                    && !transferred.isConverting(),
                            "A Nether brute is protected as soon as it arrives in the Overworld");
                } finally {
                    if (moved != null) moved.discard();
                }
            }
        }).thenSucceed();
    }

    private static R196MobSpawning.Entry findBrute(Habitat site) {
        RandomSource random = RandomSource.create(604_732L);
        for (int draw = 0; draw < 4_096; draw++) {
            var selected = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, random, false, true);
            if (selected != null && selected.type() == EntityType.PIGLIN_BRUTE) return selected;
        }
        return null;
    }

    @GameTest(template = "empty")
    public static void referenceOutpostUsesItsNativePillagerStructureOverride(GameTestHelper helper) {
        try (var preset = new SpawnPreset(true); var site = new Habitat(helper.getLevel(), Biomes.PLAINS)) {
            Set<EntityType<?>> ordinary = site.selected(helper);
            helper.assertFalse(ordinary.contains(EntityType.PILLAGER), "An ordinary plains biome has no outpost pillager candidate");
            try (var outpost = new StructureOverride(site, BuiltinStructures.PILLAGER_OUTPOST)) {
                helper.assertTrue(site.level.structureManager().getStructureWithPieceAt(site.pos, outpost.structure).isValid(),
                        "The real structure manager resolves the registered outpost start and piece");
                helper.assertTrue(site.nativeCandidates().contains(EntityType.PILLAGER),
                        "The native chunk generator supplies the outpost pillager override");
                helper.assertTrue(site.selected(helper, 16_384).contains(EntityType.PILLAGER),
                        "The reference natural selection entrypoint retains the outpost pillager candidate");
                var isolated = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, RandomSource.create(604_732L), true);
                helper.assertTrue(isolated != null && isolated.type() == EntityType.PILLAGER,
                        "The modern-only selection path also resolves the native structure's sole candidate");
            }
            helper.assertFalse(site.selected(helper).contains(EntityType.PILLAGER),
                    "Removing the outpost restores the ordinary biome pool");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeEventOnlyMobsNeverGainOrdinaryBiomeCandidates(GameTestHelper helper) {
        try (var preset = new SpawnPreset(true)) {
            for (var biome : List.of(Biomes.PLAINS, Biomes.SWAMP, Biomes.DEEP_DARK)) {
                try (var site = new Habitat(helper.getLevel(), biome)) {
                    Set<EntityType<?>> selected = site.selected(helper);
                    for (var type : List.of(EntityType.WARDEN, EntityType.BREEZE, EntityType.ZOGLIN,
                            EntityType.ELDER_GUARDIAN, EntityType.WITHER, EntityType.ENDER_DRAGON)) {
                        helper.assertFalse(site.nativeCandidates().contains(type), "The native biome has no ordinary candidate for " + type);
                        helper.assertFalse(selected.contains(type), "The source scheduler creates no extra biome candidate for " + type);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void selectedModernVariantsKeepNativePlacementLightAndSkyChecksAndActuallyJoin(GameTestHelper helper) {
        var fixture = new VariantFixture(helper);
        var site = fixture.site;
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            try (var preset = new SpawnPreset(true)) {
                fixture.time(18_000L);
                helper.assertTrue(site.level.canSeeSky(site.pos), "The nighttime habitat retains its real open sky");
                helper.assertValueEqual(site.level.getBrightness(LightLayer.BLOCK, site.pos), 0,
                        "The real nighttime habitat has no artificial block light");
                for (var type : List.of(EntityType.HUSK, EntityType.STRAY, EntityType.BOGGED)) {
                    Mob mob = site.mob(type);
                    helper.assertTrue(SpawnPlacements.isSpawnPositionOk(type, site.level, site.pos),
                            "The modern variant has valid native ground placement");
                    helper.assertTrue(qualifies(mob, site.level), "The native light, sky and positioned event checks admit " + type);
                    site.set(site.pos.below(), Blocks.AIR.defaultBlockState());
                    helper.assertFalse(qualifies(mob, site.level), "Removing ground rejects the same modern variant " + type);
                    site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
                }
                site.set(site.pos.above(3), Blocks.STONE.defaultBlockState());
                fixture.updateLighting();
            }
        }).thenWaitUntil(() -> {
            site.requireLightingReady(helper);
            helper.assertFalse(site.level.canSeeSky(site.pos), "The added roof hides the real sky after native lighting propagation");
        }).thenExecute(() -> {
            try (var preset = new SpawnPreset(true)) {
                fixture.time(18_000L);
                for (var type : List.of(EntityType.HUSK, EntityType.STRAY, EntityType.BOGGED)) {
                    Mob mob = site.mob(type);
                    helper.assertValueEqual(qualifies(mob, site.level), type == EntityType.BOGGED,
                            "Husk and stray retain their native sky requirement while bogged accepts a dark roof");
                }
                site.set(site.pos.above(3), Blocks.AIR.defaultBlockState());
                fixture.updateLighting();
            }
        }).thenWaitUntil(() -> {
            site.requireLightingReady(helper);
            helper.assertTrue(site.level.canSeeSky(site.pos), "Removing the roof restores native sky light before daylight admission");
        }).thenExecute(() -> {
            try (var preset = new SpawnPreset(true)) {
                fixture.time(6_000L);
                for (var type : List.of(EntityType.HUSK, EntityType.STRAY, EntityType.BOGGED))
                    helper.assertFalse(qualifies(site.mob(type), site.level), "Daytime surface admission still rejects " + type);
                fixture.time(18_000L);
                try (var habitat = new Habitat(site.level, site.pos, Biomes.SWAMP)) {
                    RandomSource random = RandomSource.create(604_732L);
                    R196MobSpawning.Entry selected = null;
                    for (int draw = 0; draw < 1_024; draw++) {
                        var candidate = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, random, false);
                        if (candidate != null && candidate.type() == EntityType.BOGGED) { selected = candidate; break; }
                    }
                    helper.assertTrue(selected != null && selected.modern(), "The real swamp natural selection path supplies a modern bogged");
                    Mob bogged = site.mob(selected.type());
                    helper.assertTrue(qualifies(bogged, site.level), "The selected bogged passes actual pre-finalization qualification");
                    var result = R196MobSpawning.finalizeNaturalMonster(site.level, bogged, null, 1);
                    helper.assertValueEqual(result.monsters(), 1, "The selected modern hostile consumes exactly one natural allowance");
                    helper.assertTrue(bogged.isAddedToLevel() && !bogged.isRemoved(), "The selected modern hostile is registered in the actual world");
                    helper.assertTrue(bogged.getSpawnType() == MobSpawnType.NATURAL, "The actual finalized entity retains its natural reason");
                }
            }
        }).thenExecute(fixture::close).thenSucceed();
    }

    private static boolean qualifies(Mob mob, ServerLevel level) {
        mob.getRandom().setSeed(7_000L);
        for (int attempt = 0; attempt < 128; attempt++)
            if (R196MobSpawning.qualifiesWithEvent(mob, level, MobSpawnType.NATURAL, true, true)) return true;
        return false;
    }

    private static void assertHabitat(GameTestHelper helper, ServerLevel level, ResourceKey<Biome> biome,
            Set<EntityType<?>> expected) {
        try (var site = new Habitat(level, biome)) {
            helper.assertTrue(level.getBiome(site.pos).is(biome), "The fuzzy native biome lookup resolves the fixture's intended habitat");
            helper.assertTrue(site.nativeCandidates().containsAll(expected), "The native biome supplies the expected modern variants in " + biome);
            Set<EntityType<?>> selected = site.selected(helper);
            helper.assertTrue(selected.containsAll(expected), "The reference natural selection entrypoint retains those variants in " + biome);
            selected.removeIf(R196MobSpawning::sourceType);
            helper.assertValueEqual(selected, site.nativeCandidates(), "Selected modern candidates match the full native habitat pool in " + biome);
        }
    }

    private static final class VariantFixture implements AutoCloseable {
        final DaylightUndeadSpawnGameTests.Site site;
        final long previousDayTime;
        boolean closed;

        VariantFixture(GameTestHelper helper) {
            site = new DaylightUndeadSpawnGameTests.Site(helper.getLevel(), false, 100);
            previousDayTime = site.level.getDayTime();
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
            site.keepLoaded(helper);
        }

        void time(long value) {
            site.level.setDayTime(value);
            site.level.updateSkyBrightness();
        }

        void updateLighting() {
            var light = site.level.getChunkSource().getLightEngine();
            light.checkBlock(site.pos);
            light.checkBlock(site.pos.above(3));
            ChunkPos chunk = new ChunkPos(site.pos);
            site.lightBarrier = light.waitForPendingTasks(chunk.x, chunk.z);
            light.tryScheduleUpdate();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { site.close(); }
            finally { time(previousDayTime); }
        }
    }

    private static final class Habitat implements AutoCloseable {
        final ServerLevel level;
        final LevelChunk chunk;
        final BlockPos pos;
        final LevelChunkSection section;
        final PalettedContainerRO<Holder<Biome>> previous;
        final Map<Structure, LongSet> references = new HashMap<>();
        final int quartX, quartY, quartZ;

        Habitat(ServerLevel level, ResourceKey<Biome> biome) {
            this(level, new BlockPos((40_000 + SITES.getAndIncrement() * 4) * 16 + 8, 88, 640_008), biome);
        }

        Habitat(ServerLevel level, BlockPos position, ResourceKey<Biome> biome) {
            this.level = level;
            ChunkPos coordinate = new ChunkPos(position);
            chunk = level.getChunk(coordinate.x, coordinate.z);
            pos = position;
            chunk.getAllReferences().forEach((structure, locations) -> references.put(structure, new LongOpenHashSet(locations)));
            chunk.setAllReferences(Map.of());
            section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
            previous = section.getBiomes();
            quartX = QuartPos.fromBlock(coordinate.getMinBlockX());
            quartY = QuartPos.fromBlock(pos.getY()) & ~3;
            quartZ = QuartPos.fromBlock(coordinate.getMinBlockZ());
            Holder<Biome> selected = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
            section.fillBiomesFromNoise((x, y, z, climate) -> selected,
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }

        Set<EntityType<?>> nativeCandidates() {
            Set<EntityType<?>> result = new HashSet<>();
            for (var entry : nativePool()) if (!R196MobSpawning.sourceType(entry.type)) result.add(entry.type);
            return result;
        }

        List<MobSpawnSettings.SpawnerData> nativePool() {
            return level.getChunkSource().getGenerator().getMobsAt(level.getBiome(pos),
                    level.structureManager(), MobCategory.MONSTER, pos).unwrap();
        }

        Set<EntityType<?>> selected(GameTestHelper helper) {
            return selected(helper, 1_024);
        }

        Set<EntityType<?>> selected(GameTestHelper helper, int draws) {
            Set<EntityType<?>> nativeTypes = nativeCandidates();
            Set<EntityType<?>> result = new HashSet<>();
            RandomSource random = RandomSource.create(604_732L);
            for (int draw = 0; draw < draws; draw++) {
                var entry = R196MobSpawning.select(level, pos, Category.MONSTER, random, false);
                if (entry == null) continue;
                result.add(entry.type());
                if (entry.modern()) helper.assertTrue(nativeTypes.contains(entry.type()),
                        "Every selected modern hostile originates in the actual biome or structure pool");
            }
            return result;
        }

        @Override public void close() {
            section.fillBiomesFromNoise((x, y, z, climate) -> previous.get(x & 3, y & 3, z & 3),
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
            chunk.setAllReferences(references);
        }
    }

    private static final class StructureOverride implements AutoCloseable {
        final Habitat site;
        final Structure structure;
        final Map<Structure, StructureStart> starts;
        final Map<Structure, LongSet> references = new HashMap<>();

        StructureOverride(Habitat site, ResourceKey<Structure> key) {
            this.site = site;
            structure = site.level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
            starts = new HashMap<>(site.chunk.getAllStarts());
            site.chunk.getAllReferences().forEach((entry, locations) -> references.put(entry, new LongOpenHashSet(locations)));
            BoundingBox bounds = new BoundingBox(site.pos.getX() - 3, site.pos.getY() - 3, site.pos.getZ() - 3,
                    site.pos.getX() + 3, site.pos.getY() + 3, site.pos.getZ() + 3);
            var start = new StructureStart(structure, site.chunk.getPos(), 0, new PiecesContainer(List.of(new TestPiece(bounds))));
            site.chunk.setStartForStructure(structure, start);
            site.chunk.addReferenceForStructure(structure, site.chunk.getPos().toLong());
        }

        @Override public void close() {
            site.chunk.setAllStarts(starts);
            site.chunk.setAllReferences(references);
        }
    }

    private static final class TestPiece extends StructurePiece {
        TestPiece(BoundingBox bounds) { super(StructurePieceType.SWAMPLAND_HUT, 0, bounds); }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos origin) {}
    }
}
