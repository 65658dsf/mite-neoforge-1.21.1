package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.mixin.ChunkMapAccess;
import io.github.ningmeng123.mite.worldgen.StrongholdRecovery;
import io.github.ningmeng123.mite.worldgen.StrongholdRecoveryData;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise consumed real chunk stages, native locating, persistence and immutable publication. @author NingMeng123 */
@GameTestHolder("mite_structure_exploration")
@PrefixGameTestTemplate(false)
public final class StrongholdRecoveryGameTests {
    private StrongholdRecoveryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "stronghold_recovery")
    public static void threePreviouslyGeneratedEmptyCandidatesRecoverOnlyAfterBothUnlockRequirements(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        var dataStorage = level.getDataStorage();
        StrongholdRecoveryData previous = dataStorage.computeIfAbsent(StrongholdRecoveryData.FACTORY, StrongholdRecoveryData.NAME);
        StructureUnlockTestState progress = new StructureUnlockTestState(level);
        StructuresEnabled structuresEnabled = new StructuresEnabled(level);
        AtomicBoolean closed = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!closed.compareAndSet(false, true)) return;
            try {
                structuresEnabled.close();
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot restore stronghold test world options", failure);
            } finally {
                progress.close();
                dataStorage.set(StrongholdRecoveryData.NAME, previous);
                reload(level);
            }
        };
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) {}
            @Override public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) {}
            @Override public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
            @Override public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup.run(); }
        });
        try {
            dataStorage.set(StrongholdRecoveryData.NAME, new StrongholdRecoveryData());
            reload(level);
            var generator = level.getChunkSource().getGeneratorState();
            var set = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                    .get(ResourceLocation.withDefaultNamespace("strongholds"));
            var placement = (ConcentricRingsStructurePlacement)set.placement();
            Holder<Structure> stronghold = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getHolder(ResourceLocation.withDefaultNamespace("stronghold")).orElseThrow();
            List<ChunkPos> originals = List.copyOf(generator.getRingPositionsFor(placement));
            helper.assertValueEqual(originals.size(), 3, "The original finite source ring remains three positions");
            for (ChunkPos coordinate : originals) {
                var chunk = level.getChunk(coordinate.x, coordinate.z, ChunkStatus.STRUCTURE_STARTS, true);
                helper.assertTrue(chunk.getStartForStructure(stronghold.value()) == null,
                        "The locked real STRUCTURE_STARTS lifecycle leaves each old candidate empty");
                chunk.setBlockState(coordinate.getWorldPosition().atY(90), Blocks.DIAMOND_BLOCK.defaultBlockState(), false);
            }
            helper.startSequence().thenWaitUntil(() -> {
                for (ChunkPos coordinate : originals) {
                    helper.assertTrue(level.structureManager().checkStructurePresence(coordinate, stronghold.value(), placement, false)
                                    == StructureCheckResult.START_NOT_PRESENT,
                            "The queued native presence-cache update has observed every completed empty candidate");
                }
            }).thenExecute(() -> {
                try {
                    progress.at(StructureUnlockTestState.DAY_32, 255);
                    StrongholdRecovery.recover(level);
                    helper.assertValueEqual(generator.getRingPositionsFor(placement), originals,
                            "Day 32 without a completed crafted eye cannot move or generate a stronghold");
                    progress.values(Map.of(Metric.EYE_CRAFTED, 1L));
                    progress.at(StructureUnlockTestState.DAY_32 - 1L, 255);
                    StrongholdRecovery.recover(level);
                    helper.assertValueEqual(generator.getRingPositionsFor(placement), originals,
                            "A completed eye still cannot recover before the date threshold");

                    progress.at(StructureUnlockTestState.DAY_32, 255);
                    StrongholdRecovery.recover(level);
                    List<ChunkPos> recovered = List.copyOf(generator.getRingPositionsFor(placement));
                    helper.assertTrue(recoveryPending(level).get(), "Publishing a replacement schedules a later confirmation even if an earlier notification was missed");
                    helper.assertValueEqual(recovered.size(), 3, "Recovery replaces slots without increasing the finite structure count");
                    helper.assertTrue(recovered.stream().noneMatch(originals::contains), "All three consumed slots move to new chunks");
                    for (ChunkPos coordinate : recovered) {
                        helper.assertTrue(level.getChunkSource().chunkMap.read(coordinate).join().isEmpty(),
                                "Every selected replacement has no saved terrain or player construction");
                        helper.assertTrue(placement.isStructureChunk(generator, coordinate.x, coordinate.z),
                                "The actual native concentric placement uses each published replacement");
                        helper.assertTrue(level.structureManager().checkStructurePresence(coordinate, stronghold.value(), placement, false)
                                        == StructureCheckResult.CHUNK_LOAD_NEEDED,
                                "An untouched candidate remains a potential start rather than an exhausted slot");
                    }
                    var found = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                            HolderSet.direct(List.of(stronghold)), recovered.getFirst().getWorldPosition(), 100, false);
                    helper.assertTrue(found != null && found.getSecond().equals(stronghold),
                            "The real locate and ender-eye entry can recover an actual naturally created stronghold start");
                    for (ChunkPos coordinate : originals) {
                        var chunk = level.getChunk(coordinate.x, coordinate.z, ChunkStatus.STRUCTURE_STARTS, true);
                        helper.assertTrue(chunk.getBlockState(coordinate.getWorldPosition().atY(90)).is(Blocks.DIAMOND_BLOCK),
                                "Recovery preserves blocks inside every previously generated candidate");
                        helper.assertTrue(chunk.getStartForStructure(stronghold.value()) == null,
                                "Recovery never backfills a structure into a consumed player region");
                    }

                    var current = dataStorage.computeIfAbsent(StrongholdRecoveryData.FACTORY, StrongholdRecoveryData.NAME);
                    var file = Files.createTempFile("mite-stronghold-recovery", ".nbt");
                    try {
                        NbtIo.writeCompressed(current.save(new CompoundTag(), level.registryAccess()), file);
                        var restored = StrongholdRecoveryData.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), level.registryAccess());
                        dataStorage.set(StrongholdRecoveryData.NAME, restored);
                        reload(level);
                        helper.assertValueEqual(generator.getRingPositionsFor(placement), recovered,
                                "Compressed saved data and a fresh runtime publish identical recovery slots after restart");
                        StrongholdRecovery.recover(level);
                        helper.assertValueEqual(generator.getRingPositionsFor(placement), recovered,
                                "An existing recovered stronghold and ungenerated slots survive subsequent checks unchanged");
                    } finally {
                        Files.deleteIfExists(file);
                    }
                } catch (RuntimeException failure) {
                    throw failure;
                } catch (Exception failure) {
                    throw new IllegalStateException("Cannot verify stronghold recovery persistence", failure);
                }
            }).thenSucceed();
        } catch (Exception failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(template = "empty")
    public static void unpublishedAndPendingUnloadChunksCannotBecomeRecoveryCandidates(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        var chunks = level.getChunkSource().chunkMap;
        var access = (ChunkMapAccess)chunks;
        var coordinate = new ChunkPos(1_840_031, -1_860_017);
        long packed = coordinate.toLong();
        var untouched = StrongholdRecovery.class.getDeclaredMethod("untouched", ServerLevel.class, ChunkPos.class);
        untouched.setAccessible(true);
        helper.assertTrue((boolean)untouched.invoke(null, level, coordinate), "The fixture starts with a genuinely absent saved and in-memory chunk");
        helper.assertTrue(chunks.getVisibleChunkIfPresent(packed) == null, "The fixture has no published holder");
        var holder = access.mite$updatingChunks().values().iterator().next();
        access.mite$updatingChunks().put(packed, holder);
        try {
            helper.assertFalse((boolean)untouched.invoke(null, level, coordinate), "An updating-only generation holder excludes the coordinate before its visible-map publication");
        } finally {
            access.mite$updatingChunks().remove(packed);
        }
        access.mite$pendingUnloads().put(packed, holder);
        try {
            helper.assertFalse((boolean)untouched.invoke(null, level, coordinate), "A pending unload excludes unsaved terrain even while its disk entry is still absent");
        } finally {
            access.mite$pendingUnloads().remove(packed);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void workerStructureNotificationsWaitForTheNativeMainThreadCacheUpdate(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        StrongholdRecovery.load(new LevelEvent.Load(level));
        var placement = (ConcentricRingsStructurePlacement)level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.withDefaultNamespace("strongholds")).placement();
        ChunkPos coordinate = level.getChunkSource().getGeneratorState().getRingPositionsFor(placement).getFirst();
        var chunk = level.getChunk(coordinate.x, coordinate.z, ChunkStatus.STRUCTURE_STARTS, true);
        AtomicBoolean pending = recoveryPending(level);
        boolean previous = pending.getAndSet(false);
        try {
            CompletableFuture.runAsync(() -> level.onStructureStartsAvailable(chunk)).join();
            helper.assertFalse(pending.get(), "A worker cannot publish completion before the queued native presence-cache update executes");
        } finally {
            pending.set(previous);
        }
        helper.succeed();
    }

    private static AtomicBoolean recoveryPending(ServerLevel level) throws ReflectiveOperationException {
        Field statesField = StrongholdRecovery.class.getDeclaredField("STATES");
        statesField.setAccessible(true);
        Object state = ((Map<?, ?>)statesField.get(null)).get(level);
        Field pendingField = state.getClass().getDeclaredField("needsCheck");
        pendingField.setAccessible(true);
        return (AtomicBoolean)pendingField.get(state);
    }

    @GameTest(template = "empty")
    public static void savedRecoverySlotsKeepSearchProgressAndNeverEnableAZeroCountPlacement(GameTestHelper helper) {
        var data = new StrongholdRecoveryData();
        List<ChunkPos> originals = List.of(new ChunkPos(700, -700), new ChunkPos(-800, 800), new ChunkPos(-900, -900));
        var visited = new HashSet<ChunkPos>();
        for (int attempt = 1; attempt <= 1024; attempt++) {
            ChunkPos candidate = StrongholdRecovery.candidate(196L, 1, attempt, originals.get(1));
            helper.assertTrue(visited.add(candidate) && !candidate.equals(originals.get(1)),
                    "The deterministic recovery spiral never repeats or rewrites its consumed origin");
            helper.assertValueEqual(candidate, StrongholdRecovery.candidate(196L, 1, attempt, originals.get(1)),
                    "Candidate selection is stable across independent runtime reconstruction");
        }
        data.nextAttempt(1);
        ChunkPos replacement = StrongholdRecovery.candidate(196L, 1, data.nextAttempt(1), originals.get(1));
        data.replace(1, replacement);
        var restored = StrongholdRecoveryData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertValueEqual(restored.positions(originals), List.of(originals.get(0), replacement, originals.get(2)),
                "Persistence retains the intact source slots and changes only the consumed slot");
        helper.assertValueEqual(restored.nextAttempt(1), 3, "An interrupted search resumes after its persisted attempts");
        var registered = (ConcentricRingsStructurePlacement)helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET)
                .get(ResourceLocation.withDefaultNamespace("strongholds")).placement();
        var disabled = new ConcentricRingsStructurePlacement(registered.distance(), registered.spread(), 0, registered.preferredBiomes());
        helper.assertTrue(StrongholdRecovery.positions(helper.getLevel().getChunkSource().getGeneratorState(), disabled, List.of()).isEmpty(),
                "A disabled zero-count ring cannot inherit any published recovery positions");
        helper.succeed();
    }

    private static void reload(ServerLevel level) {
        StrongholdRecovery.unload(new LevelEvent.Unload(level));
        StrongholdRecovery.load(new LevelEvent.Load(level));
    }

    private static final class StructuresEnabled implements AutoCloseable {
        private final Object data;
        private final StructureManager manager;
        private final Field worldOptions, managerOptions;
        private final WorldOptions previousWorld, previousManager;
        StructuresEnabled(ServerLevel level) throws ReflectiveOperationException {
            data = level.getServer().getWorldData();
            manager = level.structureManager();
            worldOptions = PrimaryLevelData.class.getDeclaredField("worldOptions");
            managerOptions = StructureManager.class.getDeclaredField("worldOptions");
            worldOptions.setAccessible(true); managerOptions.setAccessible(true);
            previousWorld = (WorldOptions)worldOptions.get(data);
            previousManager = (WorldOptions)managerOptions.get(manager);
            worldOptions.set(data, previousWorld.withStructures(true));
            managerOptions.set(manager, previousManager.withStructures(true));
        }
        @Override public void close() throws IllegalAccessException {
            worldOptions.set(data, previousWorld);
            managerOptions.set(manager, previousManager);
        }
    }
}
