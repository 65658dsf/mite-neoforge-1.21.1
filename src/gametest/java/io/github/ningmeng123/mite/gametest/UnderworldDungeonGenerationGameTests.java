package io.github.ningmeng123.mite.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.ModernUnderworldTerrain;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import io.github.ningmeng123.mite.worldgen.R196UnderworldTerrain;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual structure-start lifecycle, placed trial blocks, and vanilla cave-room acceptance. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class UnderworldDungeonGenerationGameTests {
    private static final ResourceLocation TRIAL = ResourceLocation.parse("mite:underworld_trial_chambers");
    private static final ResourceLocation ROOM = ResourceLocation.parse("mite:underworld_monster_room");

    private UnderworldDungeonGenerationGameTests() {}

    @GameTest(template = "empty")
    public static void underworldStructureLifecycleIncludesOnlyItsDedicatedTrialSet(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        var sets = level.getChunkSource().getGeneratorState().possibleStructureSets();
        helper.assertValueEqual(sets.size(), 1, "Only the dedicated trial set is biome-compatible in the underworld");
        helper.assertTrue(sets.getFirst().is(ResourceKey.create(Registries.STRUCTURE_SET, TRIAL)),
                "The real generator state must retain the dedicated underworld trial structure set");
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure adapted = structures.get(TRIAL);
        Structure vanilla = structures.get(ResourceLocation.withDefaultNamespace("trial_chambers"));
        helper.assertTrue(adapted instanceof JigsawStructure && vanilla instanceof JigsawStructure,
                "Both trial variants use the original 1.21.1 jigsaw structure implementation");
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        JsonObject actual = Structure.DIRECT_CODEC.encodeStart(ops, adapted).getOrThrow().getAsJsonObject();
        JsonObject original = Structure.DIRECT_CODEC.encodeStart(ops, vanilla).getOrThrow().getAsJsonObject();
        for (String field : new String[]{"start_pool", "pool_aliases", "size", "max_distance_from_center", "liquid_settings", "spawn_overrides"}) {
            helper.assertTrue(actual.get(field).equals(original.get(field)), "Preserve vanilla trial configuration: " + field);
        }
        helper.assertTrue(!vanilla.biomes().contains(level.getBiome(BlockPos.ZERO)),
                "The unmodified overworld trial chamber cannot enter the underworld biome");
        helper.assertValueEqual(actual.getAsJsonObject("start_height").getAsJsonObject("min_inclusive").get("absolute").getAsInt(), 168,
                "Trial starts use the accessible cavern instead of vanilla negative solid strata");
        helper.assertValueEqual(actual.getAsJsonObject("dimension_padding").get("bottom").getAsInt(), 180,
                "Jigsaw children stay above absolute Y116");
        helper.assertValueEqual(actual.getAsJsonObject("dimension_padding").get("top").getAsInt(), 12,
                "Jigsaw children stay below absolute Y308");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void naturalTrialStartPlacesRealRoomsAndTrialSpawnerWithinTheExistingCavern(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = underworld(helper);
        // Vanilla GameTest deliberately defaults structures off. Temporarily enable only the test
        // world's native option and its underworld manager, restoring both even when an assertion fails.
        try (StructureGeneration ignored = new StructureGeneration(level)) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(TRIAL);
        StructureSet set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(TRIAL);
        helper.assertTrue(structure != null && set != null && set.placement() instanceof RandomSpreadStructurePlacement,
                "Natural trial placement comes from registered structures, not a command-built fixture");
        RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement)set.placement();
        ChunkPos candidate = placement.getPotentialStructureChunk(level.getSeed(), 8400, -8400);
        ChunkAccess startChunk = level.getChunk(candidate.x, candidate.z, ChunkStatus.STRUCTURE_STARTS, true);
        StructureStart start = startChunk.getStartForStructure(structure);
        helper.assertTrue(start != null && start.isValid() && start.getPieces().size() > 5,
                "STRUCTURE_STARTS must naturally assemble a complete vanilla trial chamber");
        for (var piece : start.getPieces()) {
            helper.assertTrue(piece.getBoundingBox().minY() >= ModernUnderworldTerrain.CAVERN_OFFSET
                            && piece.getBoundingBox().maxY() < ModernUnderworldTerrain.CAVERN_TOP_EXCLUSIVE,
                    "Every naturally assembled trial piece stays inside the accessible existing cavern");
        }
        Set<Long> visited = new HashSet<>();
        BlockPos foundSpawner = null;
        outer: for (var piece : start.getPieces()) {
            var box = piece.getBoundingBox();
            for (int x = Math.floorDiv(box.minX(), 16); x <= Math.floorDiv(box.maxX(), 16); x++) {
                for (int z = Math.floorDiv(box.minZ(), 16); z <= Math.floorDiv(box.maxZ(), 16); z++) {
                    if (!visited.add(ChunkPos.asLong(x, z))) continue;
                    ChunkAccess chunk = level.getChunk(x, z);
                    for (BlockPos position : chunk.getBlockEntitiesPos()) {
                        if (start.getBoundingBox().isInside(position) && chunk.getBlockState(position).is(Blocks.TRIAL_SPAWNER)) {
                            foundSpawner = position;
                            break outer;
                        }
                    }
                }
            }
        }
        helper.assertTrue(foundSpawner != null, "FEATURES must place an actual trial spawner from the natural structure templates");
        helper.assertTrue(level.getBlockEntity(foundSpawner) != null,
                "The generated trial spawner must have its real functional block entity");
        helper.assertTrue(NaturalTrialBlocks.isNatural(level, foundSpawner),
                "Natural FULL chunk generation must retain the trial template provenance after proto promotion");
        BlockPos sample = new BlockPos(foundSpawner.getX(), -64, foundSpawner.getZ());
        helper.assertTrue(level.getBlockState(sample).is(MiteWorldBlocks.MANTLE.get()), "Trial generation must preserve the natural mantle floor");
        helper.assertTrue(level.getBlockState(sample.atY(319)).is(Blocks.BEDROCK), "Trial generation must preserve the protected roof");
        helper.assertFalse(level.getBlockState(sample.atY(64)).isAir(), "The original solid lower layers remain intact");
        System.out.println("[MITE underworld natural trial] seed=" + level.getSeed() + " trialSpawner="
                + foundSpawner.toShortString() + " generatedInspectionChunks=" + visited.size());
        helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void monsterRoomFeatureIsAddedOnlyToUnderworldAndKeepsVanillaGeometry(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        for (Holder.Reference<Biome> biome : biomes.holders().toList()) {
            long count = biome.value().getGenerationSettings().features().stream().flatMap(set -> set.stream())
                    .filter(feature -> feature.is(ResourceKey.create(Registries.PLACED_FEATURE, ROOM))).count();
            helper.assertValueEqual(count, biome.is(ResourceKey.create(Registries.BIOME, MiteWorldDimensions.UNDERWORLD_ID)) ? 1L : 0L,
                    "The dungeon modifier must not change modern overworld/nether biome features: " + biome.key().location());
        }
        PlacedFeature feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(ROOM);
        helper.assertTrue(feature != null && feature.feature().value().feature() == Feature.MONSTER_ROOM,
                "Use the actual vanilla monster-room implementation and its simple-dungeon loot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryDungeonUsesCaveOpeningAndSolidFloorCeilingChecks(GameTestHelper helper) {
        RoomFixture fixture = new RoomFixture(underworld(helper), new ChunkPos(8600, -8600));
        helper.assertFalse(fixture.place(), "A fully sealed solid room cannot be forced into the existing terrain");
        fixture.openCave();
        helper.assertTrue(fixture.place(), "The vanilla room feature accepts an actual two-high cave entrance with solid floor and roof");
        helper.assertTrue(fixture.region.getBlockState(fixture.origin).is(Blocks.SPAWNER)
                        && fixture.region.getBlockEntity(fixture.origin) instanceof SpawnerBlockEntity,
                "Successful vanilla room generation places a functional ordinary spawner");
        helper.assertTrue(fixture.region.getBlockState(fixture.origin.below()).is(Blocks.COBBLESTONE)
                        || fixture.region.getBlockState(fixture.origin.below()).is(Blocks.MOSSY_COBBLESTONE),
                "The floor comes from the native dungeon template rather than an artificial cavity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void naturalUnderworldChunksContainAnOrdinaryDungeonWithSpawnerAndLootChest(GameTestHelper helper) {
        ServerLevel level = underworld(helper);
        int inspectedChunks = 0;
        List<ChunkPos> candidates = naturalRoomCandidates(level);
        helper.assertFalse(candidates.isEmpty(), "At least one of 512 native-placement candidate chunks must have suitable original terrain geometry");
        for (ChunkPos coordinate : candidates) {
            // This separate region is intentionally untouched by all artificial room fixtures and
            // trial-chamber sampling. FULL executes the real biome-decoration placement modifiers.
            ChunkAccess chunk = level.getChunk(coordinate.x, coordinate.z);
            inspectedChunks++;
            for (BlockPos position : chunk.getBlockEntitiesPos()) {
                if (!(level.getBlockEntity(position) instanceof SpawnerBlockEntity spawner)) continue;
                helper.assertTrue(position.getY() >= 154 && position.getY() <= 300,
                        "A naturally placed ordinary dungeon stays in the accessible cave band");
                CompoundTag saved = spawner.getSpawner().save(new CompoundTag());
                String mob = saved.getCompound("SpawnData").getCompound("entity").getString("id");
                helper.assertTrue(Set.of("mite:revenant", "mite:longdead", "mite:longdead_guardian", "mite:demon_spider",
                                "mite:phase_spider", "mite:ghoul").contains(mob),
                        "The naturally generated ordinary spawner chooses a non-lord from its underworld roster: " + mob);
                helper.assertTrue(saved.getBoolean(io.github.ningmeng123.mite.worldgen.UnderworldDungeonSpawns.CONFIGURED_TAG),
                        "Natural dungeon generation stores its permanent roster selection");
                boolean chest = false;
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                    BlockPos near = position.offset(x, 0, z);
                    if (level.getBlockState(near).is(Blocks.CHEST) && level.getBlockEntity(near) != null) chest = true;
                }
                if (!chest) continue;
                helper.assertTrue(level.getBlockState(position.below()).is(Blocks.COBBLESTONE)
                                || level.getBlockState(position.below()).is(Blocks.MOSSY_COBBLESTONE),
                        "The naturally generated ordinary spawner sits on the native dungeon stone floor");
                System.out.println("[MITE underworld natural dungeon] seed=" + level.getSeed() + " spawner="
                        + position.toShortString() + " mob=" + mob + " inspectedChunks=" + inspectedChunks);
                helper.succeed();
                return;
            }
        }
        helper.fail("No natural ordinary dungeon with both spawner and chest was found in the geometry-screened FULL chunks near chunk (9000,-9000); "
                + "the candidate density/terrain compatibility needs review instead of substituting an artificial room");
    }

    private static List<ChunkPos> naturalRoomCandidates(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        PlacedFeature feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(ROOM);
        int step = GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal();
        var features = FeatureSorter.buildFeaturesPerStep(List.copyOf(generator.getBiomeSource().possibleBiomes()),
                biome -> generator.getBiomeGenerationSettings(biome).features(), true);
        int featureIndex = features.get(step).indexMapping().applyAsInt(feature);
        PlacementContext context = new PlacementContext(level, generator, Optional.of(feature));
        NaturalRoomTerrain terrain = new NaturalRoomTerrain(level.getSeed());
        List<ChunkPos> selected = new ArrayList<>();
        for (int index = 0; index < 512 && selected.size() < 6; index++) {
            ChunkPos chunk = new ChunkPos(9000 + index % 32, -9000 + index / 32);
            WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0));
            long decoration = random.setDecorationSeed(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
            random.setFeatureSeed(decoration, featureIndex, step);
            Stream<BlockPos> positions = Stream.of(chunk.getWorldPosition());
            for (var modifier : feature.placement()) {
                // This fixed-biome generator has no cross-biome candidates. Skipping only the biome
                // lookup avoids loading chunks during screening; FULL later executes the real filter.
                if (!(modifier instanceof BiomeFilter)) positions = positions.flatMap(at -> modifier.getPositions(context, random, at));
            }
            Stream<BlockPos> candidates = positions;
            try (candidates) {
                var iterator = candidates.iterator();
                while (iterator.hasNext()) {
                    BlockPos position = iterator.next();
                    int halfX = random.nextInt(2) + 2;
                    int halfZ = random.nextInt(2) + 2;
                    if (terrain.accepts(position, halfX, halfZ)) {
                        // Stop before the successful feature would consume its floor/chest randomness.
                        // Only the first success in each chunk is predicted, exactly matching the failed
                        // predecessors' two size draws. No blocks or random seeds are changed in-world.
                        selected.add(chunk);
                        break;
                    }
                }
            }
        }
        return selected;
    }

    private static final class NaturalRoomTerrain {
        private final ModernUnderworldTerrain terrain;
        private final Map<Long, byte[]> chunks = new LinkedHashMap<>(32, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) { return size() > 32; }
        };

        NaturalRoomTerrain(long seed) { terrain = new ModernUnderworldTerrain(seed); }

        private byte block(int x, int y, int z) {
            int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
            byte[] data = chunks.computeIfAbsent(ChunkPos.asLong(cx, cz), ignored -> terrain.generate(cx, cz));
            return data[ModernUnderworldTerrain.index(Math.floorMod(x, 16), y, Math.floorMod(z, 16))];
        }

        boolean accepts(BlockPos position, int halfX, int halfZ) {
            int openings = 0;
            for (int x = -halfX - 1; x <= halfX + 1; x++) for (int y = -1; y <= 4; y++) {
                for (int z = -halfZ - 1; z <= halfZ + 1; z++) {
                    byte state = block(position.getX() + x, position.getY() + y, position.getZ() + z);
                    boolean solid = state == R196UnderworldTerrain.STONE || state == R196UnderworldTerrain.BEDROCK
                            || state == R196UnderworldTerrain.MANTLE;
                    if ((y == -1 || y == 4) && !solid) return false;
                    if ((x == -halfX - 1 || x == halfX + 1 || z == -halfZ - 1 || z == halfZ + 1) && y == 0
                            && state == R196UnderworldTerrain.AIR
                            && block(position.getX() + x, position.getY() + 1, position.getZ() + z) == R196UnderworldTerrain.AIR) openings++;
                }
            }
            return openings >= 1 && openings <= 5;
        }
    }

    private static final class StructureGeneration implements AutoCloseable {
        private final Object data;
        private final StructureManager manager;
        private final Field worldOptions;
        private final Field managerOptions;
        private final WorldOptions originalWorld;
        private final WorldOptions originalManager;

        StructureGeneration(ServerLevel level) throws ReflectiveOperationException {
            data = level.getServer().getWorldData();
            manager = level.structureManager();
            worldOptions = PrimaryLevelData.class.getDeclaredField("worldOptions");
            managerOptions = StructureManager.class.getDeclaredField("worldOptions");
            worldOptions.setAccessible(true);
            managerOptions.setAccessible(true);
            originalWorld = (WorldOptions)worldOptions.get(data);
            originalManager = (WorldOptions)managerOptions.get(manager);
            worldOptions.set(data, originalWorld.withStructures(true));
            managerOptions.set(manager, originalManager.withStructures(true));
        }

        @Override public void close() throws IllegalAccessException {
            worldOptions.set(data, originalWorld);
            managerOptions.set(manager, originalManager);
        }
    }

    private static ServerLevel underworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The real underworld dimension must load");
        return level;
    }

    private static final class RoomFixture {
        private final ServerLevel level;
        private final BlockPos origin;
        private final WorldGenRegion region;

        RoomFixture(ServerLevel level, ChunkPos center) {
            this.level = level;
            origin = center.getWorldPosition().offset(8, 180, 8);
            var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            var biome = biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, MiteWorldDimensions.UNDERWORLD_ID));
            StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(center.x, center.z, 1, (x, z) -> {
                ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level, biomes, null);
                chunk.setPersistedStatus(ChunkStatus.CARVERS);
                chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, level.getChunkSource().randomState().sampler());
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                // The floor itself is Y179. Its support at Y178 must also be solid because the
                // vanilla room feature removes unsupported boundary blocks while lining the room.
                for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) for (int y = 178; y <= 184; y++) {
                    chunk.setBlockState(position.set(x * 16 + dx, y, z * 16 + dz), Blocks.STONE.defaultBlockState(), false);
                }
                return new GenerationChunkHolder(chunk.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                        return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null;
                    }
                };
            });
            ChunkAccess chunk = chunks.get(center.x, center.z).getChunkIfPresentUnchecked(ChunkStatus.CARVERS);
            region = new WorldGenRegion(level, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), chunk);
        }

        void openCave() {
            for (int x = 3; x <= 6; x++) for (int y = 0; y <= 1; y++) {
                region.setBlock(origin.offset(x, y, 0), Blocks.CAVE_AIR.defaultBlockState(), 2);
            }
        }

        boolean place() {
            PlacedFeature feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(ROOM);
            return feature.feature().value().place(region, level.getChunkSource().getGenerator(), RandomSource.create(196), origin);
        }
    }
}
