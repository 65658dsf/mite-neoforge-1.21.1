package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.entity.animal.R196AnimalAi032;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning.Entry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Modern animal habitats, split horse identities and actual initial population. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class ModernAnimalSpawningGameTests {
    private ModernAnimalSpawningGameTests() {}

    private record Habitat(EntityType<? extends Mob> type, ResourceKey<Biome> biome, Category category) {}

    @GameTest(template = "empty")
    public static void modernAnimalsKeepNativeBiomeCandidatesAndSpecialAcquisitionsHaveNoNaturalPool(GameTestHelper helper)
            throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunk(43000, 43000);
        BlockPos position = new BlockPos(chunk.getPos().getMiddleBlockX(), 300, chunk.getPos().getMiddleBlockZ());
        var habitats = List.of(
                new Habitat(EntityType.DONKEY, Biomes.PLAINS, Category.ANIMAL),
                new Habitat(EntityType.DONKEY, Biomes.MEADOW, Category.ANIMAL),
                new Habitat(EntityType.RABBIT, Biomes.DESERT, Category.ANIMAL),
                new Habitat(EntityType.FOX, Biomes.TAIGA, Category.ANIMAL),
                new Habitat(EntityType.GOAT, Biomes.JAGGED_PEAKS, Category.ANIMAL),
                new Habitat(EntityType.FROG, Biomes.MANGROVE_SWAMP, Category.ANIMAL),
                new Habitat(EntityType.ARMADILLO, Biomes.SAVANNA, Category.ANIMAL),
                new Habitat(EntityType.TURTLE, Biomes.BEACH, Category.ANIMAL),
                new Habitat(EntityType.PANDA, Biomes.BAMBOO_JUNGLE, Category.ANIMAL),
                new Habitat(EntityType.PARROT, Biomes.JUNGLE, Category.ANIMAL),
                new Habitat(EntityType.POLAR_BEAR, Biomes.SNOWY_PLAINS, Category.ANIMAL),
                new Habitat(EntityType.LLAMA, Biomes.SAVANNA_PLATEAU, Category.ANIMAL),
                new Habitat(EntityType.AXOLOTL, Biomes.LUSH_CAVES, Category.AQUATIC),
                new Habitat(EntityType.GLOW_SQUID, Biomes.LUSH_CAVES, Category.AQUATIC),
                new Habitat(EntityType.DOLPHIN, Biomes.LUKEWARM_OCEAN, Category.AQUATIC));
        try (var preset = new SpawnPreset(true)) {
            for (Habitat habitat : habitats) {
                try (var biome = new BiomeChange(level, chunk, position.getY(), habitat.biome())) {
                    Entry candidate = pool(level, position, habitat.category(), true).stream()
                            .filter(entry -> entry.type() == habitat.type()).findFirst().orElse(null);
                    helper.assertTrue(candidate != null && candidate.modern() && candidate.weight() > 0,
                            "The real modern candidate survives in its native habitat: " + habitat.type() + " / " + habitat.biome().location());
                }
            }
            for (Holder<Biome> biome : level.registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
                try (var changed = new BiomeChange(level, chunk, position.getY(), biome)) {
                    for (Category category : List.of(Category.ANIMAL, Category.AQUATIC)) {
                        helper.assertTrue(pool(level, position, category, true).stream().noneMatch(entry ->
                                        entry.type() == EntityType.ALLAY || entry.type() == EntityType.SNIFFER),
                                "Allay and sniffer acquisition keeps its own structure or egg entrypoint in " + biome.unwrapKey());
                    }
                }
            }
        }
        helper.assertTrue("rs".equals(R196MobSpawning.sourceClass(EntityType.DONKEY)),
                "Restoring the donkey's candidate preserves its source horse behavior identity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeDonkeyCandidatePassesRealQualificationFinalizationAndJoin(GameTestHelper helper) {
        var site = donkeySite(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            long time = site.level.getDayTime();
            try (site; var preset = new SpawnPreset(true)) {
                site.level.setDayTime(6000L);
                site.level.updateSkyBrightness();
                Mob donkey = site.mob(EntityType.DONKEY);
                helper.assertTrue(R196MobSpawning.qualifiesWithEvent(donkey, site.level, MobSpawnType.NATURAL, true, true),
                        "The actual donkey passes native geometry, light, source placement and position events on grass");
                EventHooks.finalizeMobSpawn(donkey, site.level, site.level.getCurrentDifficultyAt(site.pos), MobSpawnType.NATURAL, null);
                site.level.addFreshEntityWithPassengers(donkey);
                helper.assertTrue(donkey.isAddedToLevel() && !donkey.isSpawnCancelled(),
                        "The restored modern donkey completes normal finalization and server entity insertion");
                helper.assertTrue(R196AnimalAi032.managed(donkey), "The joined donkey retains its source animal AI");
                donkey.discard();
                site.set(site.pos.below(), Blocks.STONE.defaultBlockState());
                Mob unsupported = site.mob(EntityType.DONKEY);
                helper.assertFalse(R196MobSpawning.qualifiesWithEvent(unsupported, site.level, MobSpawnType.NATURAL, true, true),
                        "Restoring donkey candidates still rejects unsupported stone habitat");
            } finally {
                site.level.setDayTime(time);
                site.level.updateSkyBrightness();
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void normalChunkGenerationEntrypointCanPopulateDonkeys(GameTestHelper helper) {
        var site = donkeySite(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            long time = site.level.getDayTime();
            LevelChunk chunk = site.level.getChunkAt(site.pos);
            int initialAnimals = chunk.getData(R196MobSpawning.INITIAL_ANIMALS.get());
            var joined = new ArrayList<Mob>();
            Consumer<EntityJoinLevelEvent> capture = event -> {
                if (event.getLevel() == site.level && event.getEntity() instanceof Mob mob
                        && mob.chunkPosition().equals(chunk.getPos())) joined.add(mob);
            };
            try (site; var preset = new SpawnPreset(true)) {
                site.level.setDayTime(6000L);
                site.level.updateSkyBrightness();
                chunk.setData(R196MobSpawning.INITIAL_ANIMALS.get(), 0);
                var biomeRegistry = site.level.registryAccess().registryOrThrow(Registries.BIOME);
                Holder<Biome> biome = biomeRegistry.getHolderOrThrow(Biomes.MEADOW);
                try (var groundBiome = new BiomeChange(site.level, chunk, site.pos.getY() - 1, biome);
                        var feetBiome = new BiomeChange(site.level, chunk, site.pos.getY(), biome)) {
                    // A live chunk has discarded its generation-only maps. Build them from the
                    // real fixture blocks, as the generation pipeline does before population.
                    Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
                    helper.assertValueEqual(site.level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, site.pos.getX(), site.pos.getZ()),
                            site.pos.getY(), "The primed generation heightmap points to the actual grass habitat");
                    List<Entry> entries = pool(site.level, site.pos, Category.ANIMAL, false);
                    int donkeyDraw = 0;
                    boolean found = false;
                    for (Entry entry : entries) {
                        if (entry.type() == EntityType.DONKEY) { found = true; break; }
                        donkeyDraw += entry.weight();
                    }
                    helper.assertTrue(found, "The actual initial habitat has a weighted donkey entry");
                    Map<ServerLevel, RandomSource> streams = initialWeightedStreams();
                    int selectedDraw = donkeyDraw;
                    RandomSource previous = streams.put(site.level, new LegacyRandomSource(9137L) {
                        @Override public int nextInt(int bound) { return Math.floorMod(selectedDraw, bound); }
                    });
                    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, capture);
                    try {
                        NaturalSpawner.spawnMobsForChunkGeneration(site.level, biome, chunk.getPos(), new LegacyRandomSource(7319L) {
                            private boolean first = true;
                            @Override public float nextFloat() {
                                if (first) { first = false; return 0F; }
                                return .999F;
                            }
                            @Override public int nextInt(int bound) { return bound == 16 ? 8 : 0; }
                        });
                    } finally {
                        NeoForge.EVENT_BUS.unregister(capture);
                        if (previous == null) streams.remove(site.level); else streams.put(site.level, previous);
                    }
                    helper.assertTrue(joined.stream().anyMatch(mob -> mob.getType() == EntityType.DONKEY && mob.isAddedToLevel()),
                            "The intercepted vanilla chunk-generation entrypoint actually inserts a native donkey");
                    helper.assertTrue(joined.stream().allMatch(mob -> mob.getType() == EntityType.DONKEY),
                            "The seeded fixture selects the real donkey candidate without injecting a replacement pool");
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("The source initial weighted stream remains accessible to the fixture", failure);
            } finally {
                joined.forEach(Mob::discard);
                chunk.setData(R196MobSpawning.INITIAL_ANIMALS.get(), initialAnimals);
                site.level.setDayTime(time);
                site.level.updateSkyBrightness();
            }
        }).thenSucceed();
    }

    private static DaylightUndeadSpawnGameTests.Site donkeySite(GameTestHelper helper) {
        var site = new DaylightUndeadSpawnGameTests.Site(helper.getLevel(), false, 80);
        site.set(site.pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        site.keepLoaded(helper);
        return site;
    }

    @SuppressWarnings("unchecked")
    private static List<Entry> pool(ServerLevel level, BlockPos pos, Category category, boolean modernOnly)
            throws ReflectiveOperationException {
        Method method = R196MobSpawning.class.getDeclaredMethod("pool", ServerLevel.class, BlockPos.class, Category.class, boolean.class);
        method.setAccessible(true);
        return (List<Entry>)method.invoke(null, level, pos, category, modernOnly);
    }

    @SuppressWarnings("unchecked")
    private static Map<ServerLevel, RandomSource> initialWeightedStreams() throws ReflectiveOperationException {
        Field field = R196MobSpawning.class.getDeclaredField("INITIAL_WEIGHTED_RANDOM");
        field.setAccessible(true);
        return (Map<ServerLevel, RandomSource>)field.get(null);
    }

    private static final class BiomeChange implements AutoCloseable {
        final ServerLevel level;
        final LevelChunkSection section;
        final PalettedContainerRO<Holder<Biome>> previous;
        final int quartX, quartY, quartZ;

        BiomeChange(ServerLevel level, LevelChunk chunk, int y, ResourceKey<Biome> key) {
            this(level, chunk, y, level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key));
        }

        BiomeChange(ServerLevel level, LevelChunk chunk, int y, Holder<Biome> selected) {
            this.level = level;
            section = chunk.getSection(chunk.getSectionIndex(y));
            previous = section.getBiomes();
            quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            quartY = QuartPos.fromBlock(y) & ~3;
            quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            section.fillBiomesFromNoise((x, qy, z, climate) -> selected,
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }

        @Override public void close() {
            section.fillBiomesFromNoise((x, y, z, climate) -> previous.get(x & 3, y & 3, z & 3),
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }
    }
}
