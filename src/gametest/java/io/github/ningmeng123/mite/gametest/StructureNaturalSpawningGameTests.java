package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.api.MiteExtensions;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.common.world.ModifiableStructureInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real structure references constrain source, native and addon natural pools. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class StructureNaturalSpawningGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final List<Category> LAND_CATEGORIES = List.of(Category.MONSTER, Category.AMBIENT, Category.ANIMAL);
    private static final List<MobCategory> NATIVE_CATEGORIES = List.of(MobCategory.MONSTER, MobCategory.AMBIENT, MobCategory.CREATURE);

    private StructureNaturalSpawningGameTests() {}

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void bothTrialVariantsBlockNaturalPoolsOnlyWithinTheirActualPieces(GameTestHelper helper)
            throws ReflectiveOperationException {
        ServerLevel underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(underworld != null, "The actual underworld dimension is available");
        try (var preset = new SpawnPreset(true)) {
            for (ServerLevel level : List.of(helper.getLevel(), underworld)) {
                String id = level == underworld ? "mite:underworld_trial_chambers" : "minecraft:trial_chambers";
                try (var site = new Site(level, Biomes.PLAINS)) {
                    Structure structure = site.add(id);
                    helper.assertTrue(level.structureManager().getStructureWithPieceAt(site.piece(), structure).isValid(),
                            "The registered trial resolves through actual structure starts and references");
                    helper.assertTrue(!level.structureManager().structureHasPieceAt(site.pos, site.start(structure))
                                    && site.start(structure).getBoundingBox().isInside(site.pos),
                            "The center gap belongs to the whole structure but neither piece");
                    for (int index = 0; index < LAND_CATEGORIES.size(); index++) {
                        Category category = LAND_CATEGORIES.get(index);
                        helper.assertTrue(site.nativePool(site.piece(), NATIVE_CATEGORIES.get(index)).isEmpty(),
                                "The native trial piece declares an empty category override: " + category);
                        helper.assertTrue(pool(level, site.piece(), category, false).isEmpty(),
                                "Source and modern candidates respect the trial piece's empty override: " + category);
                        helper.assertTrue(selected(level, site.piece(), category) == null,
                                "The normal selection entry cannot refill the trial piece: " + category);
                        helper.assertTrue(selected(level, site.pos, category) != null,
                                "The piece-only override leaves the real gap available: " + category);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void ancientCityBlocksTheWholeBoundingBoxIncludingGapsBetweenPieces(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var preset = new SpawnPreset(true); var site = new Site(helper.getLevel(), Biomes.PLAINS)) {
            Structure city = site.add("minecraft:ancient_city");
            helper.assertFalse(site.level.structureManager().structureHasPieceAt(site.pos, site.start(city)),
                    "The test point lies between the actual ancient-city pieces");
            helper.assertTrue(site.start(city).getBoundingBox().isInside(site.pos), "The city whole bounding box contains its gap");
            for (int index = 0; index < LAND_CATEGORIES.size(); index++) {
                Category category = LAND_CATEGORIES.get(index);
                helper.assertTrue(site.nativePool(site.pos, NATIVE_CATEGORIES.get(index)).isEmpty(),
                        "The native ancient-city whole-box override suppresses " + category);
                helper.assertTrue(pool(site.level, site.pos, category, false).isEmpty()
                                && selected(site.level, site.pos, category) == null,
                        "Source and modern natural selection respect the full city boundary: " + category);
                BlockPos outside = new BlockPos(site.start(city).getBoundingBox().maxX() + 2, site.pos.getY(), site.pos.getZ());
                helper.assertTrue(selected(site.level, outside, category) != null,
                        "Moving outside the city bounding box releases the natural pool: " + category);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void anEmptyBiomeTableWithoutAStructureOverrideKeepsSourceSpawning(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var preset = new SpawnPreset(true); var site = new Site(helper.getLevel(), Biomes.DEEP_DARK)) {
            helper.assertTrue(site.nativePool(site.pos, MobCategory.MONSTER).isEmpty(),
                    "The actual deep-dark biome has no native natural monster entries");
            helper.assertTrue(pool(site.level, site.pos, Category.MONSTER, false).stream().anyMatch(entry -> !entry.modern()),
                    "An empty biome table by itself does not cancel the source biome adaptation");
            helper.assertTrue(selected(site.level, site.pos, Category.MONSTER) != null,
                    "A point without any actual structure override retains a source natural candidate");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void extensionPoolAdditionsCannotRefillTrialCategories(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var preset = new SpawnPreset(true); var site = new Site(helper.getLevel(), Biomes.PLAINS);
             var addon = new AddedCandidate()) {
            site.add("minecraft:trial_chambers");
            var types = List.<EntityType<? extends Mob>>of(EntityType.ZOMBIE, EntityType.BAT, EntityType.DONKEY);
            for (int index = 0; index < LAND_CATEGORIES.size(); index++) {
                addon.type = types.get(index);
                Category category = LAND_CATEGORIES.get(index);
                helper.assertTrue(selected(site.level, site.piece(), category) == null,
                        "The extension's actual appended entity is filtered inside the trial piece: " + category);
                helper.assertTrue(selected(site.level, site.pos, category) != null,
                        "The same extension addition remains available outside the actual pieces: " + category);
            }
            helper.assertTrue(addon.calls >= 6, "The final suppression runs after the extension callback really adds its candidates");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void finalFilteringUsesTheActualEntityCategoryOfAnAddonCandidate(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var preset = new SpawnPreset(true); var site = new Site(helper.getLevel(), Biomes.PLAINS);
             var addon = new AddedCandidate()) {
            site.add("minecraft:monument");
            addon.type = EntityType.AXOLOTL;
            for (int draw = 0; draw < 32; draw++) {
                var entry = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, RandomSource.create(7000L + draw), true);
                helper.assertTrue(entry == null || entry.type() != EntityType.AXOLOTL,
                        "The monument's empty AXOLOTLS override filters an axolotl appended to the monster list");
            }
            addon.type = EntityType.BAT;
            boolean bat = false;
            for (int draw = 0; draw < 32 && !bat; draw++) {
                var entry = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, RandomSource.create(7000L + draw), true);
                bat = entry != null && entry.type() == EntityType.BAT;
            }
            helper.assertTrue(bat, "The monument has no empty AMBIENT override, so an appended bat retains its native category");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void overlappingStructureOverridesUseTheSameFirstMatchAsTheNativeGenerator(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (var preset = new SpawnPreset(true); var site = new Site(helper.getLevel(), Biomes.PLAINS);
             var addon = new AddedCandidate()) {
            site.add("minecraft:trial_chambers");
            site.add("minecraft:monument");
            var order = new ArrayList<>(site.level.structureManager().getAllStructuresAt(site.piece()).keySet());
            helper.assertValueEqual(order.size(), 2, "Exactly two real registered structures overlap the test piece");
            var empty = new StructureSpawnOverride(StructureSpawnOverride.BoundingBoxType.PIECE, WeightedRandomList.create());
            var populated = new StructureSpawnOverride(StructureSpawnOverride.BoundingBoxType.PIECE,
                    WeightedRandomList.create(new MobSpawnSettings.SpawnerData(EntityType.PIGLIN, 1, 1, 1)));
            try (var first = new OverrideChange(order.get(0), empty); var second = new OverrideChange(order.get(1), populated)) {
                helper.assertTrue(site.nativePool(site.piece(), MobCategory.MONSTER).isEmpty(),
                        "The actual native generator selects the first empty matching override");
                helper.assertTrue(selected(site.level, site.piece(), Category.MONSTER) == null,
                        "A later populated override cannot undo the first native empty override");
            }
            try (var first = new OverrideChange(order.get(0), populated); var second = new OverrideChange(order.get(1), empty)) {
                helper.assertFalse(site.nativePool(site.piece(), MobCategory.MONSTER).isEmpty(),
                        "The actual native generator now selects the first populated matching override");
                helper.assertTrue(selected(site.level, site.piece(), Category.MONSTER) != null,
                        "A later empty override cannot suppress the first populated native override");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "structure_natural_spawning")
    public static void fortressBrickFloorKeepsTheNativeWholeBoxExceptionAndHonorsAnEmptyOverride(GameTestHelper helper)
            throws ReflectiveOperationException {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "The real Nether dimension is available");
        try (var preset = new SpawnPreset(true); var site = new Site(nether, Biomes.CRIMSON_FOREST)) {
            Structure fortress = site.add("minecraft:fortress");
            helper.assertTrue(site.nativePool(site.piece(), MobCategory.MONSTER).unwrap().stream()
                            .anyMatch(entry -> entry.type == EntityType.BLAZE),
                    "The registered fortress keeps its nonempty native piece pool");
            helper.assertTrue(R196MobSpawning.sourcePool(nether, site.piece(), Category.MONSTER).stream()
                            .anyMatch(entry -> entry.type() == EntityType.BLAZE),
                    "The source fortress pool remains connected inside the pieces");
            helper.assertFalse(nether.structureManager().structureHasPieceAt(site.pos, site.start(fortress)),
                    "The center point belongs to the fortress whole box, outside individual pieces");
            site.floor(Blocks.NETHER_BRICKS.defaultBlockState());
            helper.assertTrue(NaturalSpawner.isInNetherFortressBounds(site.pos, nether, MobCategory.MONSTER, nether.structureManager()),
                    "The real native brick-floor exception admits this whole-box point");
            helper.assertTrue(site.nativePool(site.pos, MobCategory.MONSTER).unwrap().stream().anyMatch(entry -> entry.type == EntityType.PIGLIN),
                    "The generator alone would return the surrounding crimson-forest piglin pool outside pieces");
            helper.assertTrue(pool(nether, site.pos, Category.MONSTER, true).stream()
                            .noneMatch(entry -> entry.type() == EntityType.PIGLIN || entry.type() == EntityType.HOGLIN),
                    "The full natural entry applies the fortress exception before the biome-native pool");
            helper.assertTrue(selected(nether, site.pos, Category.MONSTER) != null,
                    "The brick-floor exception retains a source fortress candidate");
            site.floor(Blocks.NETHERRACK.defaultBlockState());
            helper.assertTrue(pool(nether, site.pos, Category.MONSTER, true).stream().anyMatch(entry -> entry.type() == EntityType.PIGLIN),
                    "Removing the brick floor restores the actual surrounding biome candidates in the gap");
            try (var changed = new OverrideChange(fortress,
                    new StructureSpawnOverride(StructureSpawnOverride.BoundingBoxType.PIECE, WeightedRandomList.create()));
                 var addon = new AddedCandidate()) {
                site.floor(Blocks.NETHER_BRICKS.defaultBlockState());
                helper.assertTrue(pool(nether, site.pos, Category.MONSTER, false).isEmpty()
                                && selected(nether, site.pos, Category.MONSTER) == null,
                        "An authored empty fortress override also suppresses source and extension monsters at the whole-box brick floor");
                site.floor(Blocks.NETHERRACK.defaultBlockState());
                helper.assertTrue(selected(nether, site.pos, Category.MONSTER) != null,
                        "The empty piece override does not leak into the non-brick gap outside all fortress pieces");
            }
        }
        helper.succeed();
    }

    private static R196MobSpawning.Entry selected(ServerLevel level, BlockPos pos, Category category) {
        RandomSource random = RandomSource.create(196_112L);
        for (int attempt = 0; attempt < 128; attempt++) {
            var entry = R196MobSpawning.select(level, pos, category, random, false);
            if (entry != null) return entry;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<R196MobSpawning.Entry> pool(ServerLevel level, BlockPos pos, Category category, boolean modernOnly)
            throws ReflectiveOperationException {
        Method method = R196MobSpawning.class.getDeclaredMethod("pool", ServerLevel.class, BlockPos.class, Category.class, boolean.class);
        method.setAccessible(true);
        return (List<R196MobSpawning.Entry>)method.invoke(null, level, pos, category, modernOnly);
    }

    private static final class AddedCandidate implements AutoCloseable {
        final Field field = MiteExtensions.class.getDeclaredField("policy");
        final MiteExtensions.Policy previous = MiteExtensions.policy();
        EntityType<? extends Mob> type = EntityType.ZOMBIE;
        int calls;

        AddedCandidate() throws ReflectiveOperationException {
            field.setAccessible(true);
            field.set(null, new MiteExtensions.Policy() {
                @Override public List<R196MobSpawning.Entry> spawnPool(MiteExtensions.SpawnContext context, BlockPos pos,
                        List<R196MobSpawning.Entry> original) {
                    calls++;
                    var result = new ArrayList<>(original);
                    result.add(new R196MobSpawning.Entry(type, null, context.category(), 100, 1, 1, 4, true));
                    return List.copyOf(result);
                }
            });
        }

        @Override public void close() throws IllegalAccessException { field.set(null, previous); }
    }

    /** Apply and restore the same settings view used by NeoForge structure modifiers. */
    private static final class OverrideChange implements AutoCloseable {
        final ModifiableStructureInfo info;
        final Field field = ModifiableStructureInfo.class.getDeclaredField("modifiedStructureInfo");
        final Object previous;

        OverrideChange(Structure structure, StructureSpawnOverride monsters) throws ReflectiveOperationException {
            info = structure.modifiableStructureInfo();
            field.setAccessible(true);
            previous = field.get(info);
            var settings = info.get().structureSettings();
            var replacement = new Structure.StructureSettings(settings.biomes(), Map.of(MobCategory.MONSTER, monsters),
                    settings.step(), settings.terrainAdaptation());
            field.set(info, new ModifiableStructureInfo.StructureInfo(replacement));
        }

        @Override public void close() throws IllegalAccessException { field.set(info, previous); }
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final LevelChunk chunk;
        final BlockPos pos;
        final Map<Structure, StructureStart> starts;
        final Map<Structure, LongSet> references = new HashMap<>();
        final LevelChunkSection section;
        final PalettedContainerRO<Holder<Biome>> previousBiome;
        final BlockState previousFloor;
        final int quartX, quartY, quartZ;

        Site(ServerLevel level, ResourceKey<Biome> biome) {
            this.level = level;
            chunk = level.getChunk(46_000 + SITES.getAndIncrement() * 4, 46_000);
            pos = new BlockPos(chunk.getPos().getMiddleBlockX(), level.dimension().equals(MiteWorldDimensions.UNDERWORLD) ? 200 : 88,
                    chunk.getPos().getMiddleBlockZ());
            starts = new HashMap<>(chunk.getAllStarts());
            chunk.getAllReferences().forEach((structure, locations) -> references.put(structure, new LongOpenHashSet(locations)));
            chunk.setAllStarts(Map.of());
            chunk.setAllReferences(Map.of());
            section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
            previousBiome = section.getBiomes();
            quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            quartY = QuartPos.fromBlock(pos.getY()) & ~3;
            quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            Holder<Biome> selected = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(biome);
            section.fillBiomesFromNoise((x, y, z, climate) -> selected, level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
            previousFloor = level.getBlockState(pos.below());
        }

        Structure add(String id) {
            Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse(id));
            if (structure == null) throw new AssertionError("Registered structure exists: " + id);
            var first = new BoundingBox(pos.getX() - 5, pos.getY() - 2, pos.getZ() - 2,
                    pos.getX() - 3, pos.getY() + 2, pos.getZ() + 2);
            var second = new BoundingBox(pos.getX() + 3, pos.getY() - 2, pos.getZ() - 2,
                    pos.getX() + 5, pos.getY() + 2, pos.getZ() + 2);
            var start = new StructureStart(structure, chunk.getPos(), 0,
                    new PiecesContainer(List.of(new TestPiece(first), new TestPiece(second))));
            chunk.setStartForStructure(structure, start);
            chunk.addReferenceForStructure(structure, chunk.getPos().toLong());
            return structure;
        }

        StructureStart start(Structure structure) { return chunk.getStartForStructure(structure); }
        BlockPos piece() { return pos.west(4); }
        void floor(BlockState block) { level.setBlock(pos.below(), block, 2); }

        WeightedRandomList<MobSpawnSettings.SpawnerData> nativePool(BlockPos point, MobCategory category) {
            return level.getChunkSource().getGenerator().getMobsAt(level.getBiome(point), level.structureManager(), category, point);
        }

        @Override public void close() {
            level.setBlock(pos.below(), previousFloor, 2);
            section.fillBiomesFromNoise((x, y, z, climate) -> previousBiome.get(x & 3, y & 3, z & 3),
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
            chunk.setAllStarts(starts);
            chunk.setAllReferences(references);
        }
    }

    private static final class TestPiece extends StructurePiece {
        TestPiece(BoundingBox bounds) { super(StructurePieceType.SWAMPLAND_HUT, 0, bounds); }
        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}
        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox bounds, ChunkPos chunk, BlockPos origin) {}
    }
}
