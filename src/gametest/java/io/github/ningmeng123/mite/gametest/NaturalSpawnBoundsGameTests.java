package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.R196SpawnRules.Category;
import io.github.ningmeng123.mite.mixin.MobSpawnTypeAccessor;
import io.github.ningmeng123.mite.worldgen.ConfiguredMonsterSpawns;
import io.github.ningmeng123.mite.worldgen.R196MobSpawning;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;

/** Visible chunks outside simulation distance must never accumulate a frozen natural population. @author NingMeng123 */
@GameTestHolder("mite_spawning")
@PrefixGameTestTemplate(false)
public final class NaturalSpawnBoundsGameTests {
    private static final AtomicInteger SITES = new AtomicInteger();
    private static final TicketType<Integer> TICKET = TicketType.<Integer>create("mite_spawn_bounds_test", Comparator.naturalOrder());

    private NaturalSpawnBoundsGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void realSpawnPassRejectsVisibleNonTickingChunksAndFinalPositionsOutsideTheBorder(GameTestHelper helper) {
        Site site = new Site(helper.getLevel());
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLoaded(helper)).thenExecute(() -> {
            try (site) {
                helper.assertFalse(site.level.isNaturalSpawningAllowed(site.visible),
                        "The outer FULL chunk is visible while remaining outside the native natural-spawn range");
                helper.assertFalse(positionAllowed(site.level, site.visible.getMiddleBlockPosition(80)),
                        "A jittered candidate cannot enter the visible non-ticking chunk");
                helper.assertTrue(positionAllowed(site.level, site.center),
                        "The ticking center retains ordinary natural-spawn admission");

                long seed = 19620261117L;
                RandomSource control = RandomSource.create(seed);
                control.nextBoolean(); // The pass chooses whether source bats will try to hang before traversing chunks.
                long withoutSampling = control.nextLong();
                site.level.getRandom().setSeed(seed);
                helper.assertValueEqual(R196MobSpawning.spawnPass(site.level, Category.AMBIENT, null, candidates(site, site.visible), true), 0,
                        "The real pass adds no creature to the non-ticking loaded chunk");
                helper.assertValueEqual(site.level.getRandom().nextLong(), withoutSampling,
                        "An ineligible chunk is rejected before sampling a position or querying neighboring terrain");

                site.level.getRandom().setSeed(seed);
                R196MobSpawning.spawnPass(site.level, Category.AMBIENT, null, candidates(site, site.chunk), true);
                helper.assertTrue(site.level.getRandom().nextLong() != withoutSampling,
                        "The same pass still samples positions in the eligible ticking chunk");

                var border = site.level.getWorldBorder();
                var previous = border.createSettings();
                try {
                    border.setCenter(site.chunk.getMinBlockX(), site.chunk.getMiddleBlockZ());
                    border.setSize(16D);
                    BlockPos inside = new BlockPos(site.chunk.getMinBlockX() + 7, 80, site.chunk.getMiddleBlockZ());
                    BlockPos outside = inside.east();
                    helper.assertTrue(site.level.isPositionEntityTicking(inside) && site.level.isPositionEntityTicking(outside),
                            "Both sides of the tested border share the same real ticking chunk");
                    helper.assertTrue(positionAllowed(site.level, inside), "An in-bounds final candidate remains eligible");
                    helper.assertFalse(positionAllowed(site.level, outside),
                            "Horizontal pack jitter or wide-body correction cannot admit a final position beyond the border");
                    site.level.getRandom().setSeed(seed);
                    helper.assertValueEqual(R196MobSpawning.spawnPass(site.level, Category.AMBIENT, null, candidates(site, site.chunk), true), 0,
                            "The real pass rejects a chunk extending beyond the world border");
                    helper.assertValueEqual(site.level.getRandom().nextLong(), withoutSampling,
                            "World-border rejection happens before candidate sampling");
                } finally { border.applySettings(previous); }
            }
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void savedFrozenNaturalMonstersReleaseTheirAllowanceAndResumeCountingWhenActive(GameTestHelper helper) {
        Site site = new Site(helper.getLevel());
        site.keepLoaded(helper);
        helper.startSequence().thenWaitUntil(() -> site.requireLoaded(helper)).thenExecute(() -> {
            BlockPos outer = site.visible.getMiddleBlockPosition(80);
            int before = R196MobSpawning.count(site.level, Category.MONSTER, null);
            site.savedNatural = site.creeper(MobSpawnType.NATURAL, outer);
            helper.assertTrue(site.savedNatural.getSpawnType() == MobSpawnType.NATURAL,
                    "The old natural-spawn reason survives an actual entity NBT save and reload");
            helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), before,
                    "An ordinary saved natural monster outside simulation distance releases its frozen allowance");

            site.creeper(MobSpawnType.NATURAL, site.center);
            helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), before + 1,
                    "An active ordinary natural monster continues using its allowance");
            for (MobSpawnType reason : new MobSpawnType[]{MobSpawnType.SPAWNER, MobSpawnType.TRIAL_SPAWNER, MobSpawnType.COMMAND, null}) {
                int current = R196MobSpawning.count(site.level, Category.MONSTER, null);
                site.creeper(reason, outer);
                helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), current + 1,
                        "A non-ticking monster from an artificial or unknown origin retains its allowance: " + reason);
            }

            int current = R196MobSpawning.count(site.level, Category.MONSTER, null);
            Mob persistent = site.creeper(MobSpawnType.NATURAL, outer);
            persistent.setPersistenceRequired();
            helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), current + 1,
                    "Persistent natural monsters remain counted outside simulation distance");
            Mob named = site.creeper(MobSpawnType.NATURAL, outer);
            named.setCustomName(Component.literal("NingMeng123 preserved natural monster"));
            helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), current + 2,
                    "Naming a natural monster preserves its allowance independently of the persistence flag");
            Mob passenger = site.creeper(MobSpawnType.NATURAL, outer);
            helper.assertTrue(passenger.startRiding(persistent, true) && passenger.requiresCustomPersistence(),
                    "A real mounted natural monster exercises custom persistence");
            helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), current + 3,
                    "Custom-persistent natural monsters retain their existing global-count semantics");

            site.level.getChunkSource().addRegionTicket(TICKET, site.visible, 2, site.ticket);
            site.outerTicket = true;
            for (int x = site.visible.x - 2; x <= site.visible.x + 2; x++)
                for (int z = site.visible.z - 2; z <= site.visible.z + 2; z++)
                    site.level.getChunk(x, z, ChunkStatus.FULL, true);
        }).thenWaitUntil(() -> {
            for (int tasks = 0; tasks < 64 && site.level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(site.level.isPositionEntityTicking(site.visible.getMiddleBlockPosition(80)),
                    "The original outer chunk has genuinely entered entity ticking range");
        }).thenExecute(() -> {
            try (site) {
                helper.assertTrue(!site.savedNatural.isRemoved() && !site.savedNatural.isPersistenceRequired()
                                && !site.savedNatural.requiresCustomPersistence() && !site.savedNatural.hasCustomName(),
                        "The original saved monster resumes without deletion, naming or persistence changes");
                int current = R196MobSpawning.count(site.level, Category.MONSTER, null);
                site.savedNatural.discard();
                helper.assertValueEqual(R196MobSpawning.count(site.level, Category.MONSTER, null), current - 1,
                        "The previously frozen ordinary natural monster is counted immediately when its chunk becomes active");
            }
        }).thenSucceed();
    }

    private static boolean positionAllowed(ServerLevel level, BlockPos pos) {
        try {
            Method method = R196MobSpawning.class.getDeclaredMethod("naturalPositionAllows", ServerLevel.class, BlockPos.class);
            method.setAccessible(true);
            return (boolean)method.invoke(null, level, pos);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Map<Long, Boolean> candidates(Site site, ChunkPos chunk) {
        // Retain one actual candidate while ensuring unrelated tests' ambient entities cannot fill this fixture's cap.
        int area = (R196MobSpawning.count(site.level, Category.AMBIENT, null) + 1) * 256;
        return new AbstractMap<>() {
            @Override public int size() { return area; }
            @Override public Set<Map.Entry<Long, Boolean>> entrySet() { return Set.of(Map.entry(chunk.toLong(), false)); }
        };
    }

    private static final class Site implements AutoCloseable {
        final ServerLevel level;
        final int ticket = SITES.incrementAndGet();
        final ChunkPos chunk = new ChunkPos(55_000 + ticket * 8, 55_000);
        final ChunkPos visible = new ChunkPos(chunk.x + 2, chunk.z);
        final BlockPos center = chunk.getMiddleBlockPosition(80);
        final List<Mob> mobs = new ArrayList<>();
        final Consumer<MobDespawnEvent> keepMobs = event -> {
            if (mobs.contains(event.getEntity())) event.setResult(MobDespawnEvent.Result.DENY);
        };
        Mob savedNatural;
        boolean outerTicket, closed;

        Site(ServerLevel level) {
            this.level = level;
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobDespawnEvent.class, keepMobs);
            level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket);
            try {
                for (int x = chunk.x - 2; x <= chunk.x + 2; x++) for (int z = chunk.z - 2; z <= chunk.z + 2; z++)
                    level.getChunk(x, z, ChunkStatus.FULL, true);
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void keepLoaded(GameTestHelper helper) {
            helper.testInfo.addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) {}
                @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
                @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            });
        }

        void requireLoaded(GameTestHelper helper) {
            for (int tasks = 0; tasks < 64 && level.getChunkSource().pollTask(); tasks++) {}
            helper.assertTrue(level.isPositionEntityTicking(center) && level.areEntitiesLoaded(visible.toLong())
                            && level.getChunkSource().getChunkNow(visible.x, visible.z) != null,
                    "The real ticket exposes a ticking center and a fully loaded outer entity section");
        }

        Mob creeper(MobSpawnType reason, BlockPos pos) {
            Mob original = EntityType.CREEPER.create(level);
            if (original == null) throw new AssertionError("The registered creeper creates");
            ((MobSpawnTypeAccessor)original).mite$setSpawnType(reason);
            original.getPersistentData().putBoolean(ConfiguredMonsterSpawns.ADMITTED, true);
            original.moveTo(pos.getCenter());
            original.setNoAi(true);
            original.setNoGravity(true);
            CompoundTag saved = new CompoundTag();
            original.saveWithoutId(saved);
            Mob restored = EntityType.CREEPER.create(level);
            if (restored == null) throw new AssertionError("The registered saved creeper creates");
            restored.load(saved);
            mobs.add(restored);
            if (!level.addFreshEntity(restored)) throw new AssertionError("The saved monster enters the real visible entity section");
            return restored;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            NeoForge.EVENT_BUS.unregister(keepMobs);
            mobs.forEach(Mob::discard);
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket);
            if (outerTicket) level.getChunkSource().removeRegionTicket(TICKET, visible, 2, ticket);
        }
    }
}
