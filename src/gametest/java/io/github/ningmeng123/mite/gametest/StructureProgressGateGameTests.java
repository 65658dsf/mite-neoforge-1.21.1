package io.github.ningmeng123.mite.gametest;

import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
import net.minecraft.world.level.Level;
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
    private static final List<String> RUINED_PORTALS = List.of("ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
            "ruined_portal_swamp", "ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether");
    private static final long DAY_THIRTY_START = 690000L;
    private static final long DAY_FORTY_FIVE_START = 1050000L;
    private static final ChunkPos COORDINATE = new ChunkPos(18400, -18600);

    private StructureProgressGateGameTests() {}

    @GameTest(template = "empty")
    public static void portalAndOutpostGatesRequireTheirOwnBitsAtTheExactDisplayedDate(GameTestHelper helper) {
        long[] dates = {-1L, 0L, DAY_THIRTY_START - 1L, DAY_THIRTY_START, DAY_THIRTY_START + 1L,
                DAY_FORTY_FIVE_START - 1L, DAY_FORTY_FIVE_START, DAY_FORTY_FIVE_START + 1L, 3000000L};
        helper.assertValueEqual(R196StructureGeneration.displayDay(DAY_THIRTY_START - 1L), 29L,
                "The final locked portal tick displays day 29");
        helper.assertValueEqual(R196StructureGeneration.displayDay(DAY_THIRTY_START), 30L,
                "The portal unlock tick displays day 30 rather than waiting for another midnight");
        helper.assertValueEqual(R196StructureGeneration.displayDay(DAY_FORTY_FIVE_START - 1L), 44L,
                "The final locked outpost tick displays day 44");
        helper.assertValueEqual(R196StructureGeneration.displayDay(DAY_FORTY_FIVE_START), 45L,
                "The outpost unlock tick displays day 45");
        for (long date : dates) {
            for (int conditions = 0; conditions < 256; conditions++) {
                boolean portalExpected = date >= DAY_THIRTY_START
                        && (conditions & StructureProgressData.UNDERWORLD_VISITED) != 0;
                int outpostBits = StructureProgressData.BOW_CRAFTED | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
                boolean outpostExpected = date >= DAY_FORTY_FIVE_START && (conditions & outpostBits) == outpostBits;
                helper.assertTrue(R196StructureGeneration.ruinedPortalUnlocked(date, conditions) == portalExpected,
                        "Only the portal date and actual underworld visit unlock conditions " + conditions + " at tick " + date);
                helper.assertTrue(R196StructureGeneration.pillagerOutpostUnlocked(date, conditions) == outpostExpected,
                        "The outpost requires both completed crafting bits at tick " + date + " with conditions " + conditions);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void newSharedProgressBitsCannotReplaceTheVillageToolPrerequisite(GameTestHelper helper) {
        for (int conditions = 0; conditions < 256; conditions++) {
            helper.assertFalse(R196StructureGeneration.villageUnlocked(1409999L, conditions),
                    "Village date 60 is still required with every combination of old and new progress");
            helper.assertTrue(R196StructureGeneration.villageUnlocked(1410000L, conditions)
                            == ((conditions & StructureProgressData.OBSIDIAN_TOOL) != 0),
                    "Only the original crafted obsidian tool bit unlocks village conditions " + conditions);
        }
        try (GateState state = new GateState(helper.getLevel())) {
            state.set(1410000L, StructureProgressData.UNDERWORLD_VISITED | StructureProgressData.BOW_CRAFTED
                    | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            for (String name : VILLAGES) {
                helper.assertFalse(R196StructureGeneration.unlocked(state.level, structure(state.level, name)),
                        "The published snapshot cannot mistake unrelated new bits for a crafted village tool");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allPortalVariantsAndOutpostsRejectNativeStartCreationWhileLocked(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (GateState state = new GateState(helper.getLevel())) {
            int outpostBits = StructureProgressData.BOW_CRAFTED | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
            state.set(0L, StructureProgressData.UNDERWORLD_VISITED | outpostBits);
            for (String name : RUINED_PORTALS) assertNativeStartRejected(helper, state.level, name);
            assertNativeStartRejected(helper, state.level, "pillager_outpost");

            state.set(DAY_FORTY_FIVE_START, 31 | outpostBits);
            for (String name : RUINED_PORTALS) assertNativeStartRejected(helper, state.level, name);
            for (int incomplete : new int[]{0, StructureProgressData.BOW_CRAFTED, StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED}) {
                state.set(DAY_FORTY_FIVE_START, 31 | StructureProgressData.UNDERWORLD_VISITED | incomplete);
                assertNativeStartRejected(helper, state.level, "pillager_outpost");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void portalAndOutpostLocateRejectsTheWholeLockedRequestBeforeCandidateScanning(GameTestHelper helper) {
        try (GateState state = new GateState(helper.getLevel())) {
            state.set(DAY_FORTY_FIVE_START, StructureProgressData.OBSIDIAN_TOOL);
            List<Holder<Structure>> locked = new ArrayList<>();
            RUINED_PORTALS.forEach(name -> locked.add(structure(state.level, name)));
            locked.add(structure(state.level, "pillager_outpost"));
            Pair<BlockPos, Holder<Structure>> found = state.level.getChunkSource().getGenerator().findNearestMapStructure(
                    state.level, HolderSet.direct(locked), COORDINATE.getWorldPosition(), 100, false);
            helper.assertTrue(found == null, "The real locate entry filters every locked portal variant and outpost before the large scan");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bothNewDisplayDateBoundariesReleaseTheSameCachedPresentCandidate(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (GateState state = new GateState(helper.getLevel())) {
            int complete = StructureProgressData.UNDERWORLD_VISITED | StructureProgressData.BOW_CRAFTED
                    | StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED;
            for (String name : RUINED_PORTALS) {
                assertDateBoundaryRecoversCandidate(helper, state, name, DAY_THIRTY_START, complete);
            }
            assertDateBoundaryRecoversCandidate(helper, state, "pillager_outpost", DAY_FORTY_FIVE_START, complete);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void laterWorldProgressImmediatelyReleasesCandidatesWithoutCachingLockedFailures(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (GateState state = new GateState(helper.getLevel())) {
            state.set(DAY_FORTY_FIVE_START, 0);
            Holder<Structure> portal = structure(state.level, "ruined_portal");
            Holder<Structure> outpost = structure(state.level, "pillager_outpost");
            CachedPresentManager manager = new CachedPresentManager(state.level);
            helper.assertTrue(candidate(state.level, manager, Set.of(portal, outpost)) == null,
                    "Date alone cannot reuse either cached-present structure");
            helper.assertValueEqual(manager.checks, 0, "Missing progress is rejected before presence caching");
            state.data.fulfill(StructureProgressData.UNDERWORLD_VISITED);
            Pair<BlockPos, Holder<Structure>> portalResult = candidate(state.level, manager, Set.of(portal));
            helper.assertTrue(portalResult != null && portalResult.getSecond().equals(portal),
                    "A world underworld visit immediately publishes the newly eligible portal snapshot");
            helper.assertTrue(candidate(state.level, manager, Set.of(outpost)) == null,
                    "Underworld visitation cannot replace either outpost craft");
            state.data.fulfill(StructureProgressData.BOW_CRAFTED);
            helper.assertTrue(candidate(state.level, manager, Set.of(outpost)) == null,
                    "A completed bow still requires completed mithril equipment");
            helper.assertValueEqual(manager.checks, 1, "Neither incomplete outpost request reaches the native cache");
            state.data.fulfill(StructureProgressData.MITHRIL_EQUIPMENT_CRAFTED);
            Pair<BlockPos, Holder<Structure>> outpostResult = candidate(state.level, manager, Set.of(outpost));
            helper.assertTrue(outpostResult != null && outpostResult.getSecond().equals(outpost),
                    "Completing the final world prerequisite restores the same cached native outpost candidate");
            helper.assertValueEqual(manager.checks, 2, "Only the two unlocked requests reach presence checking");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netherPortalVariantUsesTheSharedOverworldDateAndUnderworldVisit(GameTestHelper helper)
            throws ReflectiveOperationException {
        try (GateState state = new GateState(helper.getLevel())) {
            ServerLevel nether = state.level.getServer().getLevel(Level.NETHER);
            ServerLevel underworld = state.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            helper.assertTrue(nether != null && underworld != null, "Both actual dimensions are registered");
            Holder<Structure> portal = structure(nether, "ruined_portal_nether");
            CachedPresentManager manager = new CachedPresentManager(nether);
            state.set(DAY_THIRTY_START, 0);
            helper.assertFalse(R196StructureGeneration.unlocked(nether, portal),
                    "The Nether portal variant cannot bypass the shared underworld visit requirement");
            helper.assertTrue(candidate(nether, manager, Set.of(portal)) == null,
                    "Native Nether locate rejects its previously cached-present portal while locked");
            helper.assertValueEqual(manager.checks, 0, "Locked Nether lookup does not touch presence caching");
            StructureProgressData.get(underworld).fulfill(StructureProgressData.UNDERWORLD_VISITED);
            Pair<BlockPos, Holder<Structure>> found = candidate(nether, manager, Set.of(portal));
            helper.assertTrue(found != null && found.getSecond().equals(portal),
                    "Progress from the underworld unlocks the Nether variant using the overworld date");
            helper.assertValueEqual(manager.checks, 1, "The newly unlocked Nether candidate uses native presence checking");
        }
        helper.succeed();
    }

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
            Holder<Structure> igloo = structure(level, "igloo");
            helper.assertTrue(R196StructureGeneration.unlocked(level, igloo)
                    && R196StructureGeneration.canGenerate(level, igloo, COORDINATE), "The independent modern igloo remains available");
            Set<Holder<Structure>> mixed = new LinkedHashSet<>();
            mixed.add(structure(level, "village_plains"));
            mixed.add(structure(level, "ruined_portal"));
            mixed.add(structure(level, "pillager_outpost"));
            mixed.add(igloo);
            CachedPresentManager manager = new CachedPresentManager(level);
            Pair<BlockPos, Holder<Structure>> found = candidate(level, manager, mixed);
            helper.assertTrue(found != null && found.getSecond().equals(igloo), "Native locate returns the permitted modern structure");
            helper.assertTrue(found.getFirst().equals(placement().getLocatePos(COORDINATE)), "Native placement retains the exact original locate position");
            helper.assertValueEqual(manager.checks, 1, "The locked village, portal and outpost never reach the cache while the igloo uses the native path once");
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

    private static void assertNativeStartRejected(GameTestHelper helper, ServerLevel level, String name)
            throws ReflectiveOperationException {
        Holder<Structure> structure = structure(level, name);
        helper.assertFalse(R196StructureGeneration.unlocked(level, structure), "The snapshot keeps " + name + " locked");
        helper.assertFalse(R196StructureGeneration.canGenerate(level, structure, COORDINATE), "The new chunk gate rejects " + name);
        ProtoChunk chunk = new ProtoChunk(COORDINATE, UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        helper.assertFalse(tryGenerate(level, structure, chunk), "The actual native start method rejects locked " + name);
        helper.assertTrue(chunk.getAllStarts().isEmpty(), "Locked " + name + " never writes an invalid start into a new chunk");
    }

    private static void assertDateBoundaryRecoversCandidate(GameTestHelper helper, GateState state, String name,
            long unlockTime, int conditions) throws ReflectiveOperationException {
        Holder<Structure> structure = structure(state.level, name);
        CachedPresentManager manager = new CachedPresentManager(state.level);
        state.set(unlockTime - 1L, conditions);
        helper.assertTrue(candidate(state.level, manager, Set.of(structure)) == null,
                "The last locked display tick rejects cached-present " + name);
        helper.assertValueEqual(manager.checks, 0, "The dynamic date rejection precedes native presence caching for " + name);
        state.set(unlockTime, conditions);
        helper.assertTrue(R196StructureGeneration.canGenerate(state.level, structure, COORDINATE),
                "Exactly the displayed unlock date permits a new " + name + " chunk");
        Pair<BlockPos, Holder<Structure>> found = candidate(state.level, manager, Set.of(structure));
        helper.assertTrue(found != null && found.getSecond().equals(structure),
                "The same cached-present " + name + " recovers on its exact unlock tick");
        helper.assertTrue(found.getFirst().equals(placement().getLocatePos(COORDINATE)),
                "The unlocked " + name + " keeps its native locate position");
        helper.assertValueEqual(manager.checks, 1, "Only the unlocked " + name + " request reaches the native cache");
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

    private static final class GateState implements AutoCloseable {
        private final ServerLevel level;
        private final StructureProgressData data;
        private final long previousTime;
        private final int previousConditions;
        private final boolean previousDirty;

        GateState(ServerLevel level) {
            this.level = level.getServer().overworld();
            data = StructureProgressData.get(this.level);
            previousTime = this.level.getDayTime();
            previousConditions = data.conditions();
            previousDirty = data.isDirty();
            set(0L, 0);
        }

        void set(long time, int conditions) {
            data.restoreConditions(conditions);
            level.setDayTime(time);
            R196StructureGeneration.refresh(level);
        }

        @Override public void close() {
            data.restoreConditions(previousConditions);
            data.setDirty(previousDirty);
            level.setDayTime(previousTime);
            R196StructureGeneration.refresh(level);
        }
    }
}
