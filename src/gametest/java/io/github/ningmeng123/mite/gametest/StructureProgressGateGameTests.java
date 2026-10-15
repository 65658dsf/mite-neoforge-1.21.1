package io.github.ningmeng123.mite.gametest;

import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.WritableLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise transformed native generation and locate methods, including cached-present starts. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StructureProgressGateGameTests {
    private static final List<String> VILLAGES = List.of("village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
    private static final ChunkPos COORDINATE = new ChunkPos(18400, -18600);

    private StructureProgressGateGameTests() {}

    @GameTest(template = "empty")
    public static void allFiveVillagesRejectNativeStructureStartCreationOnTheFirstDate(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        try (FirstDate ignored = new FirstDate(level)) {
            for (String name : VILLAGES) {
                Holder<Structure> village = structure(level, name);
                helper.assertFalse(R196StructureGeneration.unlocked(level, village), "The first date cannot unlock " + name);
                ProtoChunk chunk = new ProtoChunk(COORDINATE, UpgradeData.EMPTY, level,
                        level.registryAccess().registryOrThrow(Registries.BIOME), null);
                boolean generated = tryGenerate(level, village, chunk);
                helper.assertFalse(generated, "The actual native start method rejects locked " + name);
                helper.assertTrue(chunk.getAllStarts().isEmpty(), "Rejected generation does not write even an invalid cached start");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void villageLocateRejectsTheWholeLockedRequestBeforeScanningCandidates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try (FirstDate ignored = new FirstDate(level)) {
            List<Holder<Structure>> villages = VILLAGES.stream().map(name -> structure(level, name)).toList();
            Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(villages), COORDINATE.getWorldPosition(), 100, false);
            helper.assertTrue(found == null, "The real /locate entry returns no locked village without consuming the 100-region scan");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPreviouslyCachedPresentVillageCannotBypassTheCurrentDateGate(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        try (FirstDate ignored = new FirstDate(level)) {
            CachedPresentManager manager = new CachedPresentManager(level);
            for (String name : VILLAGES) {
                Pair<BlockPos, Holder<Structure>> found = candidate(level, manager, Set.of(structure(level, name)));
                helper.assertTrue(found == null, "The actual candidate path rejects cached-present " + name);
            }
            helper.assertValueEqual(manager.checks, 0, "Dynamic rejection occurs before the permanent native presence cache is read or written");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aMixedCandidateSetKeepsItsNativeModernStructureResult(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        try (FirstDate ignored = new FirstDate(level)) {
            Holder<Structure> portal = structure(level, "ruined_portal");
            helper.assertTrue(R196StructureGeneration.unlocked(level, portal)
                    && R196StructureGeneration.canGenerate(level, portal, COORDINATE), "The independent modern ruined portal remains available");
            Set<Holder<Structure>> mixed = new LinkedHashSet<>();
            mixed.add(structure(level, "village_plains"));
            mixed.add(portal);
            CachedPresentManager manager = new CachedPresentManager(level);
            Pair<BlockPos, Holder<Structure>> found = candidate(level, manager, mixed);
            helper.assertTrue(found != null && found.getSecond().equals(portal), "Native locate returns the permitted modern structure");
            helper.assertTrue(found.getFirst().equals(placement().getLocatePos(COORDINATE)), "Native placement retains the exact original locate position");
            helper.assertValueEqual(manager.checks, 1, "The locked village never reaches the cache while the portal uses the native path once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theSixtiethDisplayDayUnlocksTheSameCandidateWithoutAStaleNegativeCache(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        StructureProgressData progress = StructureProgressData.get(level);
        int previousConditions = progress.conditions();
        try (FirstDate ignored = new FirstDate(level)) {
            progress.fulfill(31);
            Holder<Structure> village = structure(level, "village_plains");
            CachedPresentManager manager = new CachedPresentManager(level);
            level.setDayTime(1409999L);
            R196StructureGeneration.refresh(level);
            helper.assertFalse(R196StructureGeneration.unlocked(level, village), "The tick before display day 60 remains locked even with all five prerequisites");
            helper.assertTrue(candidate(level, manager, Set.of(village)) == null, "The real native candidate path respects the last locked tick");
            helper.assertValueEqual(manager.checks, 0, "Locked lookup cannot insert a negative result into the permanent cache");
            level.setDayTime(1410000L);
            R196StructureGeneration.refresh(level);
            helper.assertTrue(R196StructureGeneration.unlocked(level, village), "Exactly display day 60 unlocks all completed prerequisites");
            Pair<BlockPos, Holder<Structure>> found = candidate(level, manager, Set.of(village));
            helper.assertTrue(found != null && found.getSecond().equals(village), "The same coordinate immediately recovers its native cached-present result");
            helper.assertValueEqual(manager.checks, 1, "Only the unlocked request reaches native presence checking");
        } finally {
            progress.restoreConditions(previousConditions);
            R196StructureGeneration.refresh(level);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void movingTheWorldSpawnRechecksTempleDistanceWithoutPoisoningTheCandidateCache(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        BlockPos previousSpawn = level.getSharedSpawnPos();
        float previousAngle = level.getSharedSpawnAngle();
        WritableLevelData worldData = (WritableLevelData) level.getLevelData();
        try {
            Holder<Structure> temple = structure(level, "desert_pyramid");
            BlockPos candidateCenter = new BlockPos(COORDINATE.getMiddleBlockX(), 64, COORDINATE.getMiddleBlockZ());
            CachedPresentManager manager = new CachedPresentManager(level);
            // The real writable level data changes without installing spawn tickets or loading a new region.
            worldData.setSpawn(candidateCenter, previousAngle);
            R196StructureGeneration.refresh(level);
            helper.assertFalse(R196StructureGeneration.canGenerate(level, temple, COORDINATE), "A temple at world spawn violates the source 2000-block exclusion");
            helper.assertTrue(candidate(level, manager, Set.of(temple)) == null, "The transformed candidate path enforces the current spawn exclusion");
            helper.assertValueEqual(manager.checks, 0, "The excluded candidate never reaches native presence caching");
            worldData.setSpawn(candidateCenter.east(4096), previousAngle);
            R196StructureGeneration.refresh(level);
            helper.assertTrue(R196StructureGeneration.canGenerate(level, temple, COORDINATE), "Moving spawn beyond the exclusion releases the same temple coordinate");
            Pair<BlockPos, Holder<Structure>> found = candidate(level, manager, Set.of(temple));
            helper.assertTrue(found != null && found.getSecond().equals(temple), "Changing spawn immediately restores native candidate lookup");
            helper.assertValueEqual(manager.checks, 1, "Only the newly permitted location reaches the unchanged native cache");
        } finally {
            worldData.setSpawn(previousSpawn, previousAngle);
            R196StructureGeneration.refresh(level);
        }
        helper.succeed();
    }

    private static Holder<Structure> structure(ServerLevel level, String name) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(
                ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace(name)));
    }

    private static RandomSpreadStructurePlacement placement() {
        return new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 10387312);
    }

    private static boolean tryGenerate(ServerLevel level, Holder<Structure> structure, ProtoChunk chunk) throws ReflectiveOperationException {
        Method method = ChunkGenerator.class.getDeclaredMethod("tryGenerateStructure", StructureSet.StructureSelectionEntry.class,
                StructureManager.class, RegistryAccess.class, RandomState.class, StructureTemplateManager.class, long.class,
                ChunkAccess.class, ChunkPos.class, SectionPos.class);
        method.setAccessible(true);
        return (boolean) method.invoke(level.getChunkSource().getGenerator(), new StructureSet.StructureSelectionEntry(structure, 1),
                level.structureManager(), level.registryAccess(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, chunk.getPos(), SectionPos.bottomOf(chunk));
    }

    @SuppressWarnings("unchecked")
    private static Pair<BlockPos, Holder<Structure>> candidate(ServerLevel level, StructureManager manager,
            Set<Holder<Structure>> structures) throws ReflectiveOperationException {
        Method method = ChunkGenerator.class.getDeclaredMethod("getStructureGeneratingAt", Set.class, LevelReader.class,
                StructureManager.class, boolean.class, StructurePlacement.class, ChunkPos.class);
        method.setAccessible(true);
        return (Pair<BlockPos, Holder<Structure>>) method.invoke(null, structures, level, manager, false, placement(), COORDINATE);
    }

    private static final class CachedPresentManager extends StructureManager {
        private int checks;

        CachedPresentManager(ServerLevel level) {
            super(level, new WorldOptions(level.getSeed(), true, false), null);
        }

        @Override public StructureCheckResult checkStructurePresence(ChunkPos coordinate, Structure structure,
                StructurePlacement placement, boolean skipReferenced) {
            checks++;
            return StructureCheckResult.START_PRESENT;
        }
    }

    private static final class FirstDate implements AutoCloseable {
        private final ServerLevel level;
        private final long previous;

        FirstDate(ServerLevel level) {
            this.level = level;
            previous = level.getDayTime();
            level.setDayTime(0L);
            R196StructureGeneration.refresh(level);
        }

        @Override public void close() {
            level.setDayTime(previous);
            R196StructureGeneration.refresh(level);
        }
    }
}
