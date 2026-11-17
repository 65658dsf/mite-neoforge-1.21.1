package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.BloodMoonInvasionData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real generation accessors keep spider RNG and uncommitted passengers local. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class SpiderWorldgenGameTests {
    private static final ChunkPos COORDINATE = new ChunkPos(21400, -21600);

    private SpiderWorldgenGameTests() {}

    @GameTest(template = "empty")
    public static void chunkGenerationUsesRegionRandomAndSavesOnlyTheMountedTree(GameTestHelper helper) {
        verify(helper, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void structureTemplateUsesRegionRandomAndSavesOnlyTheMountedTree(GameTestHelper helper) {
        verify(helper, true);
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, boolean templatePlacement) {
        ServerLevel level = helper.getLevel();
        BloodMoonInvasionData progress = BloodMoonInvasionData.get(level);
        long previousDay = progress.highestDay();
        boolean previouslyDirty = progress.isDirty();
        progress.restoreProgress(35L);
        try {
            for (boolean jockey : new boolean[]{false, true}) {
                Fixture fixture = new Fixture(level, jockeySeed(jockey));
                int[] liveJoins = {0};
                Consumer<EntityJoinLevelEvent> intercept = event -> {
                    Entity entity = event.getEntity();
                    if (event.getLevel() == level && entity.chunkPosition().equals(COORDINATE)
                            && (entity instanceof Spider || entity instanceof AbstractSkeleton)) {
                        liveJoins[0]++;
                        // A regressed callback cannot insert test entities or load this remote chunk.
                        event.setCanceled(true);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityJoinLevelEvent.class, intercept);
                try {
                    if (templatePlacement) {
                        level.getRandom().setSeed(jockeySeed(!jockey));
                        boolean placed = spiderTemplate(level).placeInWorld(fixture.region, fixture.origin,
                                fixture.origin, new StructurePlaceSettings().setFinalizeEntities(true),
                                RandomSource.create(196L), 2);
                        helper.assertTrue(placed, "The actual structure template entity placement completes");
                    } else {
                        Spider spider = new Spider(EntityType.SPIDER, level);
                        spider.moveTo(fixture.origin.getX() + .5, fixture.origin.getY(), fixture.origin.getZ() + .5, 0, 0);
                        level.getRandom().setSeed(jockeySeed(!jockey));
                        EventHooks.finalizeMobSpawn(spider, fixture.region,
                                fixture.region.getCurrentDifficultyAt(spider.blockPosition()), MobSpawnType.CHUNK_GENERATION, null);
                        helper.assertValueEqual(spider.getPassengers().size(), jockey ? 1 : 0,
                                "The region one-percent draw controls the source rider before the parent is saved");
                        helper.assertTrue(!spider.isAddedToLevel() && spider.getPassengers().stream().noneMatch(Entity::isAddedToLevel),
                                "Finalization leaves the whole generation tree unregistered in the live server level");
                        fixture.region.addFreshEntityWithPassengers(spider);
                    }
                    helper.assertValueEqual(liveJoins[0], 0, "Generation never invokes live ServerLevel entity insertion");
                    helper.assertValueEqual(level.getRandom().nextInt(100), new Random(jockeySeed(!jockey)).nextInt(100),
                            "Generation leaves the oppositely seeded live-world random source untouched");
                    assertSavedTree(helper, fixture, jockey,
                            templatePlacement ? MobSpawnType.STRUCTURE : MobSpawnType.CHUNK_GENERATION);
                } finally {
                    NeoForge.EVENT_BUS.unregister(intercept);
                    fixture.region.submitted.forEach(Entity::discard);
                }
            }
        } finally {
            progress.restoreProgress(previousDay);
            progress.setDirty(previouslyDirty);
        }
    }

    private static void assertSavedTree(GameTestHelper helper, Fixture fixture, boolean jockey, MobSpawnType reason) {
        helper.assertValueEqual(fixture.chunk.getEntities().size(), 1,
                "ProtoChunk stores exactly one spider root without an independent skeleton duplicate");
        CompoundTag root = fixture.chunk.getEntities().getFirst();
        helper.assertValueEqual(root.getString("id"), "minecraft:spider", "The generated root retains the native spider identity");
        ListTag passengers = root.getList("Passengers", Tag.TAG_COMPOUND);
        helper.assertValueEqual(passengers.size(), jockey ? 1 : 0, "The saved spider contains exactly its selected passenger");
        if (jockey) {
            helper.assertValueEqual(passengers.getCompound(0).getString("id"), "minecraft:skeleton",
                    "The ranged rider is serialized inside its spider parent");
        }
        helper.assertValueEqual(fixture.region.submitted.size(), jockey ? 2 : 1,
                "The normal generation entry point submits every member of the tree once");
        helper.assertTrue(fixture.region.submitted.stream().allMatch(entity -> entity instanceof Mob mob
                        && mob.getSpawnType() == reason && !entity.isAddedToLevel()),
                "The parent and rider retain their real finalization reason without entering the live server level");
    }

    private static long jockeySeed(boolean positive) {
        for (long seed = 0; seed < 10_000; seed++) {
            if ((new Random(seed).nextInt(100) == 0) == positive) return seed;
        }
        throw new AssertionError("Independent JavaRandom fixture must cover both source one-percent outcomes");
    }

    private static StructureTemplate spiderTemplate(ServerLevel level) {
        CompoundTag data = new CompoundTag();
        ListTag size = new ListTag();
        size.add(IntTag.valueOf(2));
        size.add(IntTag.valueOf(3));
        size.add(IntTag.valueOf(2));
        data.put("size", size);
        data.put("palette", new ListTag());
        data.put("blocks", new ListTag());
        CompoundTag entity = new CompoundTag();
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(.5));
        position.add(DoubleTag.valueOf(0));
        position.add(DoubleTag.valueOf(.5));
        entity.put("pos", position);
        ListTag blockPosition = new ListTag();
        blockPosition.add(IntTag.valueOf(0));
        blockPosition.add(IntTag.valueOf(0));
        blockPosition.add(IntTag.valueOf(0));
        entity.put("blockPos", blockPosition);
        CompoundTag spider = new CompoundTag();
        spider.putString("id", "minecraft:spider");
        entity.put("nbt", spider);
        ListTag entities = new ListTag();
        entities.add(entity);
        data.put("entities", entities);
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), data);
        return template;
    }

    private static final class Fixture {
        final ProtoChunk chunk;
        final Region region;
        final BlockPos origin = COORDINATE.getWorldPosition().offset(8, 180, 8);

        Fixture(ServerLevel level, long seed) {
            chunk = new ProtoChunk(COORDINATE, UpgradeData.EMPTY, level,
                    level.registryAccess().registryOrThrow(Registries.BIOME), null);
            chunk.setPersistedStatus(ChunkStatus.CARVERS);
            StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(COORDINATE.x, COORDINATE.z, 0,
                    (x, z) -> new GenerationChunkHolder(chunk.getPos()) {
                        @Override public int getTicketLevel() { return 0; }
                        @Override public int getQueueLevel() { return 0; }
                        @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                            return chunk.getPersistedStatus().isOrAfter(status) ? chunk : null;
                        }
                    });
            region = new Region(level, cache, chunk, seed);
        }
    }

    private static final class Region extends WorldGenRegion {
        final List<Entity> submitted = new ArrayList<>();
        private final RandomSource fixtureRandom;

        Region(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess center, long seed) {
            super(level, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
            fixtureRandom = RandomSource.create(seed);
        }

        @Override public RandomSource getRandom() { return fixtureRandom; }

        @Override public boolean addFreshEntity(Entity entity) {
            submitted.add(entity);
            return super.addFreshEntity(entity);
        }
    }
}
