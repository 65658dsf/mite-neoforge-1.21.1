package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.config.MonsterSpawnCatalog;
import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import io.github.ningmeng123.mite.worldgen.R196OutdoorChecks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native aquatic scheduling and modern underwater hostile habitats. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class AquaticSpawningGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();

    private AquaticSpawningGameTests() {}

    @GameTest(template = "empty")
    public static void spawnCatalogIncludesMonumentGuardianWeightAndKeepsElderGuardianWithoutNaturalEntry(GameTestHelper helper) {
        var entries = MonsterSpawnCatalog.entries("mite", helper.getLevel().registryAccess());
        var guardian = entries.stream().filter(entry -> entry.type() == EntityType.GUARDIAN).findFirst().orElseThrow();
        var elder = entries.stream().filter(entry -> entry.type() == EntityType.ELDER_GUARDIAN).findFirst().orElseThrow();
        helper.assertTrue(guardian.baseWeights().contains(1),
                "The guardian's independent ocean-monument spawn override contributes its native weight of one");
        helper.assertTrue(elder.baseWeights().isEmpty(),
                "The monument's elder guardians have no independent natural-spawn candidate to edit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFishPassRunsBetweenPeriodicTicksAfterSquidReachTheirOwnCap(GameTestHelper helper) {
        try (FishPool pool = new FishPool(helper); var preset = new SpawnPreset(true)) {
            for (int index = 0; index < 5; index++) pool.occupant(EntityType.SQUID);
            var state = pool.state();
            helper.assertValueEqual(state.getMobCategoryCounts().getInt(MobCategory.WATER_CREATURE), 5,
                    "The independent native squid population is already at its cap");
            pool.clock.setGameTime(401L);
            pool.level.getRandom().setSeed(59473812L);
            for (int attempt = 0; attempt < 512 && pool.fishJoins() == 0; attempt++)
                NaturalSpawner.spawnForChunk(pool.level, pool.chunk, state, true, false, false);
            helper.assertTrue(pool.fishJoins() > 0,
                    "The real chunk spawning entrypoint creates fish at a non-400-tick game time despite five squid");
            helper.assertValueEqual(state.getMobCategoryCounts().getInt(MobCategory.WATER_AMBIENT), pool.fishJoins(),
                    "Native after-spawn callbacks debit the fish category for the actual new entities");
            helper.assertTrue(pool.joined.stream().allMatch(mob -> mob.getType().getCategory() == MobCategory.WATER_AMBIENT),
                    "The full squid category and source-owned terrestrial categories add no extra entities");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFishCapAndFriendlySpawnFlagStillStopTheWholeChunkPass(GameTestHelper helper) {
        try (FishPool pool = new FishPool(helper)) {
            pool.level.getRandom().setSeed(85764321L);
            var empty = pool.state();
            for (int attempt = 0; attempt < 64; attempt++)
                NaturalSpawner.spawnForChunk(pool.level, pool.chunk, empty, false, false, false);
            helper.assertTrue(pool.joined.isEmpty(), "Disabling friendly natural spawns also disables fish");
            for (int index = 0; index < 5; index++) pool.occupant(EntityType.SQUID);
            for (int index = 0; index < 20; index++) pool.occupant(EntityType.SALMON);
            var full = pool.state();
            helper.assertValueEqual(full.getMobCategoryCounts().getInt(MobCategory.WATER_AMBIENT), 20,
                    "The native fish allowance retains its independent twenty-entity baseline");
            for (int attempt = 0; attempt < 64; attempt++)
                NaturalSpawner.spawnForChunk(pool.level, pool.chunk, full, true, false, false);
            helper.assertTrue(pool.joined.isEmpty(), "The native fish and squid caps prevent any further river population");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void referenceDrownedRetainNativeUnderwaterLightChecksAndLandKeepsItsDaylightGate(GameTestHelper helper) {
        var site = new DaylightUndeadSpawnGameTests.Site(helper.getLevel(), false, 54);
        site.fill(site.pos.offset(-3, -1, -3), site.pos.offset(3, 9, 3), Blocks.WATER.defaultBlockState());
        var biome = new BiomeChange(site.level, site.level.getChunkAt(site.pos), site.pos.getY(), Biomes.RIVER);
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { biome.close(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { biome.close(); }
        });
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLightingReady(helper)).thenExecute(() -> {
            long time = site.level.getDayTime();
            try (site; biome; var preset = new SpawnPreset(true)) {
                site.level.setDayTime(6000L);
                site.level.updateSkyBrightness();
                helper.assertTrue(R196OutdoorChecks.daytimeSurfaceHabitat(site.level, site.pos),
                        "The water column is classified as exposed by the existing daytime surface search");
                Mob drowned = site.mob(EntityType.DROWNED);
                helper.assertTrue(R196MobSpawning.naturalDaylightAllows(drowned, site.level),
                        "An underwater native hostile reaches its own placement and light checks");
                Mob zombie = site.mob(EntityType.ZOMBIE);
                zombie.setPos(zombie.getX(), 64D, zombie.getZ());
                helper.assertFalse(R196MobSpawning.naturalDaylightAllows(zombie, site.level),
                        "The existing daytime restriction continues to protect land above the river");
                var random = RandomSource.create(247649L);
                for (int draw = 0; draw < 32; draw++) {
                    var selected = R196MobSpawning.select(site.level, site.pos, Category.MONSTER, random, true);
                    helper.assertTrue(selected != null && selected.type() == EntityType.DROWNED,
                            "The reference river pool retains its underwater native candidate while filtering land variants");
                }
                helper.assertTrue(site.level.getMaxLocalRawBrightness(site.pos) > 0,
                        "The daytime water fixture has real positive skylight");
                helper.assertFalse(qualifiesInSeededAttempts(drowned, site.level),
                        "The modern underwater exemption still rejects native light that is too bright");
                site.level.setDayTime(18000L);
                site.level.updateSkyBrightness();
                helper.assertValueEqual(site.level.getMaxLocalRawBrightness(site.pos), 0,
                        "The same submerged position becomes dark enough during the night");
                helper.assertTrue(site.level.noCollision(drowned), "The drowned's water habitat has no physical collision");
                helper.assertTrue(SpawnPlacements.isSpawnPositionOk(drowned.getType(), site.level, drowned.blockPosition()),
                        "The drowned has a valid native water placement");
                helper.assertTrue(drowned.checkSpawnRules(site.level, MobSpawnType.NATURAL),
                        "The drowned's native path-weight check accepts the dark habitat");
                helper.assertTrue(drowned.checkSpawnObstruction(site.level), "The drowned's water habitat is unobstructed");
                helper.assertTrue(qualifiesInSeededAttempts(drowned, site.level),
                        "A real drowned passes native water, geometry, biome lottery and light checks at night");
            } finally {
                site.level.setDayTime(time);
                site.level.updateSkyBrightness();
            }
        }).thenSucceed();
    }

    private static boolean qualifiesInSeededAttempts(Mob mob, ServerLevel level) {
        mob.getRandom().setSeed(7000L);
        for (int attempt = 0; attempt < 256; attempt++) {
            if (R196MobSpawning.qualifiesWithEvent(mob, level, MobSpawnType.NATURAL, true, true)) return true;
        }
        return false;
    }

    private static final class BiomeChange implements AutoCloseable {
        final ServerLevel level;
        final LevelChunkSection section;
        final PalettedContainerRO<Holder<Biome>> previous;
        final int quartX, quartY, quartZ;

        BiomeChange(ServerLevel level, LevelChunk chunk, int y, ResourceKey<Biome> key) {
            this.level = level;
            section = chunk.getSection(chunk.getSectionIndex(y));
            previous = section.getBiomes();
            quartX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
            quartY = QuartPos.fromBlock(y) & ~3;
            quartZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
            Holder<Biome> selected = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(key);
            section.fillBiomesFromNoise((x, qy, z, climate) -> selected,
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }

        @Override public void close() {
            section.fillBiomesFromNoise((x, y, z, climate) -> previous.get(x & 3, y & 3, z & 3),
                    level.getChunkSource().randomState().sampler(), quartX, quartY, quartZ);
        }
    }

    private static final class FishPool implements AutoCloseable {
        final ServerLevel level;
        final LevelChunk chunk;
        final ServerPlayer player;
        final ServerLevelData clock;
        final long gameTime;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<Mob> occupants = new ArrayList<>();
        final List<Mob> joined = new ArrayList<>();
        final List<BiomeChange> biomes = new ArrayList<>();
        final Consumer<EntityJoinLevelEvent> capture;

        @SuppressWarnings("removal")
        FishPool(GameTestHelper helper) {
            level = helper.getLevel();
            clock = (ServerLevelData)level.getLevelData();
            gameTime = level.getGameTime();
            ChunkPos position = new ChunkPos(38000 + SITES.getAndIncrement() * 8, 38000);
            chunk = level.getChunk(position.x, position.z);
            for (int sectionY : List.of(48, 64)) biomes.add(new BiomeChange(level, chunk, sectionY, Biomes.RIVER));
            for (BlockPos point : BlockPos.betweenClosed(position.getMinBlockX(), 49, position.getMinBlockZ(),
                    position.getMaxBlockX(), 64, position.getMaxBlockZ())) {
                blocks.put(point.immutable(), level.getBlockState(point));
                level.setBlock(point, Blocks.WATER.defaultBlockState(), 2);
            }
            player = helper.makeMockServerPlayerInLevel();
            player.moveTo(position.getMinBlockX() - 30D, 56D, position.getMiddleBlockZ(), 0F, 0F);
            capture = event -> {
                if (event.getLevel() == level && !event.isCanceled() && event.getEntity() instanceof Mob mob
                        && Math.abs(mob.getX() - position.getMiddleBlockX()) < 32
                        && Math.abs(mob.getZ() - position.getMiddleBlockZ()) < 32 && !occupants.contains(mob)) joined.add(mob);
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, capture);
        }

        void occupant(EntityType<? extends Mob> type) {
            Mob mob = type.create(level);
            if (mob == null) throw new AssertionError("The native aquatic occupant creates");
            mob.moveTo(chunk.getPos().getMiddleBlockX(), 55D, chunk.getPos().getMiddleBlockZ(), 0F, 0F);
            occupants.add(mob);
        }

        NaturalSpawner.SpawnState state() {
            // Isolate the native dimension cap and callbacks from asynchronous player chunk tracking.
            var local = new LocalMobCapCalculator(level.getChunkSource().chunkMap) {
                @Override public boolean canSpawn(MobCategory category, ChunkPos position) { return true; }
                @Override public void addMob(ChunkPos position, MobCategory category) {}
            };
            return NaturalSpawner.createState(289, new ArrayList<>(occupants),
                    (key, consumer) -> consumer.accept(chunk), local);
        }

        int fishJoins() {
            return (int)joined.stream().filter(mob -> mob.getType().getCategory() == MobCategory.WATER_AMBIENT).count();
        }

        @Override public void close() {
            NeoForge.EVENT_BUS.unregister(capture);
            joined.forEach(Mob::discard);
            occupants.forEach(Mob::discard);
            level.getServer().getPlayerList().remove(player);
            blocks.forEach((point, block) -> level.setBlock(point, block, 2));
            biomes.forEach(BiomeChange::close);
            clock.setGameTime(gameTime);
        }
    }
}
