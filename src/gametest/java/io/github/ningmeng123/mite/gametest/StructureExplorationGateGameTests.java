package io.github.ningmeng123.mite.gametest;

import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.rules.StructureUnlockRules;
import io.github.ningmeng123.mite.rules.StructureUnlockRules.Metric;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import io.github.ningmeng123.mite.worldgen.StructureExplorationData;
import io.github.ningmeng123.mite.worldgen.StructureProgressData;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real generation, locate and configured-feature boundaries use the same live exploration snapshot. @author NingMeng123 */
@GameTestHolder("mite_structure_exploration")
@PrefixGameTestTemplate(false)
public final class StructureExplorationGateGameTests {
    private static final ChunkPos COORDINATE = new ChunkPos(18400, -18600);
    private StructureExplorationGateGameTests() {}

    private static Map<Metric, Long> completed() {
        Map<Metric, Long> result = new EnumMap<>(Metric.class);
        for (Metric metric : Metric.values()) result.put(metric, metric.cap());
        return result;
    }

    @GameTest(template = "empty")
    public static void everyVanillaStructureStartAndWholeLocateRequestRejectBeforeDayThirtyTwo(GameTestHelper h) throws ReflectiveOperationException {
        try (StructureUnlockTestState state = new StructureUnlockTestState(h.getLevel())) {
            state.complete(); state.at(StructureUnlockTestState.DAY_32 - 1L, 255);
            Set<Holder<Structure>> structures = new LinkedHashSet<>();
            for (var holder : state.level.registryAccess().registryOrThrow(Registries.STRUCTURE).holders().toList()) {
                var id = holder.key().location();
                if (!id.getNamespace().equals("minecraft") || id.getPath().equals("trial_chambers")) continue;
                structures.add(holder);
                assertNativeStartRejected(h, state.level, holder);
            }
            h.assertValueEqual(structures.size(), 33, "The independent registry fixture covers every gated vanilla structure and all variants");
            var found = state.level.getChunkSource().getGenerator().findNearestMapStructure(state.level,
                    HolderSet.direct(List.copyOf(structures)), COORDINATE.getWorldPosition(), 100, false);
            h.assertTrue(found == null, "The real locate entry rejects the entire locked request before its large scan");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void allActionsAndEquipmentBitsAreRequiredByActualStartsAndCachedLocateCandidates(GameTestHelper h) throws ReflectiveOperationException {
        try (StructureUnlockTestState state = new StructureUnlockTestState(h.getLevel())) {
            for (var definition : StructureUnlockRules.definitions()) {
                if (definition.path().equals("overworld_fossil")) continue;
                var structure = structure(state.level, "minecraft:" + representative(definition.path()));
                for (var requirement : definition.requirements()) {
                    Map<Metric, Long> values = completed();
                    values.put(requirement.metric(), requirement.target() - 1L);
                    state.values(values); state.at(StructureUnlockTestState.DAY_60, 255);
                    assertNativeStartRejected(h, state.level, structure);
                    CachedPresentManager cache = new CachedPresentManager(state.level);
                    h.assertTrue(candidate(state.level, cache, Set.of(structure)) == null,
                            "A previously present start cannot bypass the missing action: " + definition.path() + "/" + requirement.metric());
                    h.assertValueEqual(cache.checks, 0, "Incomplete progress is rejected before permanent structure presence caching");
                    values.put(requirement.metric(), requirement.target()); state.values(values);
                    h.assertTrue(R196StructureGeneration.unlocked(state.level, structure), "The exact completed action publishes immediately: " + definition.path());
                    if (!definition.path().equals("mineshaft")) {
                        var recovered = candidate(state.level, cache, Set.of(structure));
                        h.assertTrue(recovered != null && recovered.getSecond().equals(structure), "The same native candidate recovers without a stale negative cache: " + definition.path());
                        h.assertValueEqual(cache.checks, 1, "Only the released request reaches native presence checking");
                    }
                }
                for (int bit = 1; bit <= 128; bit <<= 1) if ((definition.flags() & bit) != 0) {
                    state.values(completed()); state.at(StructureUnlockTestState.DAY_60, 255 ^ bit);
                    assertNativeStartRejected(h, state.level, structure);
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exactDisplayedDatesAndAllDimensionsUseTheAuthoritativeOverworldProgress(GameTestHelper h) throws ReflectiveOperationException {
        try (StructureUnlockTestState state = new StructureUnlockTestState(h.getLevel())) {
            state.complete();
            for (var definition : StructureUnlockRules.definitions()) {
                if (definition.path().equals("overworld_fossil")) continue;
                var holder = structure(state.level, "minecraft:" + representative(definition.path()));
                long threshold = definition.path().equals("village") ? 1_410_000L
                        : definition.path().equals("pillager_outpost") ? 1_050_000L : 738_000L;
                state.at(threshold - 1L, 255);
                assertNativeStartRejected(h, state.level, holder);
                state.at(threshold, 255);
                h.assertValueEqual(R196StructureGeneration.displayDay(threshold), definition.minimumDay(), "The displayed day agrees with the configured milestone");
                h.assertTrue(R196StructureGeneration.unlocked(state.level, holder), "The exact first tick of the displayed milestone unlocks " + definition.path());
            }
            ServerLevel nether = state.level.getServer().getLevel(Level.NETHER);
            ServerLevel underworld = state.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            ServerLevel end = state.level.getServer().getLevel(Level.END);
            h.assertTrue(nether != null && underworld != null && end != null, "All real destination dimensions are loaded");
            h.assertTrue(StructureExplorationData.get(nether) == state.exploration && StructureExplorationData.get(underworld) == state.exploration,
                    "Exploration data is shared across actual dimension storage access");
            for (ServerLevel level : List.of(nether, end)) {
                long localTime = level.getDayTime();
                try {
                    level.setDayTime(0L); state.at(StructureUnlockTestState.DAY_32, 255);
                    var holder = structure(level, level == nether ? "minecraft:fortress" : "minecraft:end_city");
                    h.assertTrue(R196StructureGeneration.unlocked(level, holder), "A local day-one clock cannot hide completed overworld progress");
                    state.at(0L, 255); level.setDayTime(StructureUnlockTestState.DAY_60);
                    assertNativeStartRejected(h, level, holder);
                } finally { level.setDayTime(localTime); }
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bothTrialChambersBypassAllNewGatesWhileRetainingTheirIndependentConfiguration(GameTestHelper h) throws ReflectiveOperationException {
        boolean oldSurface = MiteConfig.OVERWORLD_TRIALS.get(), oldDeep = MiteConfig.UNDERWORLD_TRIALS.get();
        try (StructureUnlockTestState state = new StructureUnlockTestState(h.getLevel())) {
            ServerLevel underworld = state.level.getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
            h.assertTrue(underworld != null, "The real underworld dimension is registered");
            MiteConfig.OVERWORLD_TRIALS.set(true); MiteConfig.UNDERWORLD_TRIALS.set(true); MiteConfig.refreshEnchantmentRules();
            for (var entry : Map.of(state.level, "minecraft:trial_chambers", underworld, "mite:underworld_trial_chambers").entrySet()) {
                var holder = structure(entry.getKey(), entry.getValue());
                h.assertTrue(R196StructureGeneration.unlocked(entry.getKey(), holder), "Trial chambers remain exempt on day one with no flags or exploration: " + entry.getValue());
                CachedPresentManager cache = new CachedPresentManager(entry.getKey());
                var found = candidate(entry.getKey(), cache, Set.of(holder));
                h.assertTrue(found != null && found.getSecond().equals(holder), "The real native locate candidate accepts the exempt chamber");
                h.assertValueEqual(cache.checks, 1, "An exempt chamber continues through native structure-presence lookup");
            }
            MiteConfig.OVERWORLD_TRIALS.set(false); MiteConfig.UNDERWORLD_TRIALS.set(false); MiteConfig.refreshEnchantmentRules();
            assertNativeStartRejected(h, state.level, structure(state.level, "minecraft:trial_chambers"));
            assertNativeStartRejected(h, underworld, structure(underworld, "mite:underworld_trial_chambers"));
        } finally {
            MiteConfig.OVERWORLD_TRIALS.set(oldSurface); MiteConfig.UNDERWORLD_TRIALS.set(oldDeep); MiteConfig.refreshEnchantmentRules();
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void naturalFossilConfiguredFeaturesRejectEarlyDatesAndInsufficientNaturalStone(GameTestHelper h) throws ReflectiveOperationException {
        // Natural fossils honor the actual world option, which the native GameTest preset disables.
        Object worldData = h.getLevel().getServer().getWorldData();
        var optionsField = PrimaryLevelData.class.getDeclaredField("worldOptions");
        optionsField.setAccessible(true);
        WorldOptions originalOptions = (WorldOptions)optionsField.get(worldData);
        optionsField.set(worldData, originalOptions.withStructures(true));
        try (StructureUnlockTestState state = new StructureUnlockTestState(h.getLevel())) {
            for (String feature : List.of("fossil_coal", "fossil_diamonds")) {
                var configured = state.level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                        .get(ResourceLocation.withDefaultNamespace(feature));
                h.assertTrue(configured != null, "The real fossil configured feature remains registered: " + feature);
                state.values(Map.of(Metric.NATURAL_STONE_MINED, 256L)); state.at(StructureUnlockTestState.DAY_32 - 1L, 255);
                h.assertFalse(configured.place(state.level, state.level.getChunkSource().getGenerator(), RandomSource.create(196L),
                        h.absolutePos(new BlockPos(2, 8, 2))), "The real fossil feature cannot place before day 32");
                state.values(Map.of(Metric.NATURAL_STONE_MINED, 255L)); state.at(StructureUnlockTestState.DAY_32, 255);
                h.assertFalse(configured.place(state.level, state.level.getChunkSource().getGenerator(), RandomSource.create(196L),
                        h.absolutePos(new BlockPos(2, 8, 2))), "The real fossil feature cannot place with only 255 natural stone mined");
            }
            state.values(Map.of(Metric.NATURAL_STONE_MINED, 256L));
            h.assertTrue(R196StructureGeneration.fossilUnlocked(state.level), "Exactly 256 natural stone and day 32 release fossil generation");
            optionsField.set(worldData, originalOptions.withStructures(false));
            h.assertFalse(R196StructureGeneration.fossilUnlocked(state.level), "Disabled world structure generation still overrides completed fossil progress");
        } finally {
            optionsField.set(worldData, originalOptions);
        }
        h.succeed();
    }

    private static String representative(String path) {
        return path.equals("village") ? "village_plains" : path.equals("ocean_ruin") ? "ocean_ruin_cold" : path;
    }

    private static Holder<Structure> structure(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
    }

    private static void assertNativeStartRejected(GameTestHelper h, ServerLevel level, Holder<Structure> structure) throws ReflectiveOperationException {
        h.assertFalse(R196StructureGeneration.unlocked(level, structure), "The published generation snapshot rejects " + structure.unwrapKey());
        ProtoChunk chunk = new ProtoChunk(COORDINATE, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
        Method method = ChunkGenerator.class.getDeclaredMethod("tryGenerateStructure", StructureSet.StructureSelectionEntry.class,
                StructureManager.class, RegistryAccess.class, RandomState.class, StructureTemplateManager.class, long.class,
                ChunkAccess.class, ChunkPos.class, SectionPos.class);
        method.setAccessible(true);
        boolean generated = (boolean)method.invoke(level.getChunkSource().getGenerator(), new StructureSet.StructureSelectionEntry(structure, 1),
                level.structureManager(), level.registryAccess(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, chunk.getPos(), SectionPos.bottomOf(chunk));
        h.assertFalse(generated, "The transformed native start method rejects the locked structure");
        h.assertTrue(chunk.getAllStarts().isEmpty(), "A dynamic gate cannot insert a permanent invalid structure start");
    }

    @SuppressWarnings("unchecked")
    private static Pair<BlockPos, Holder<Structure>> candidate(ServerLevel level, StructureManager manager, Set<Holder<Structure>> structures) throws ReflectiveOperationException {
        Method method = ChunkGenerator.class.getDeclaredMethod("getStructureGeneratingAt", Set.class, LevelReader.class,
                StructureManager.class, boolean.class, StructurePlacement.class, ChunkPos.class);
        method.setAccessible(true);
        return (Pair<BlockPos, Holder<Structure>>)method.invoke(null, structures, level, manager, false,
                new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 10387312), COORDINATE);
    }

    private static final class CachedPresentManager extends StructureManager {
        int checks;
        CachedPresentManager(ServerLevel level) { super(level, new WorldOptions(level.getSeed(), true, false), null); }
        @Override public StructureCheckResult checkStructurePresence(ChunkPos coordinate, Structure structure, StructurePlacement placement, boolean skipReferenced) {
            checks++; return StructureCheckResult.START_PRESENT;
        }
    }
}
