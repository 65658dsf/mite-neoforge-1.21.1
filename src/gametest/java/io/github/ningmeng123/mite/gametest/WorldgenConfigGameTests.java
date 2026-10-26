package io.github.ningmeng123.mite.gametest;

import com.mojang.datafixers.util.Pair;
import io.github.ningmeng123.mite.config.MiteConfig;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.R196StructureGeneration;
import java.lang.reflect.Method;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise live trial configuration through transformed native generation and locate. @author NingMeng123 */
@GameTestHolder("mite_config")
@PrefixGameTestTemplate(false)
public final class WorldgenConfigGameTests {
    private static final ChunkPos COORDINATE = new ChunkPos(19400, -19600);

    private WorldgenConfigGameTests() {}

    @GameTest(template = "empty")
    public static void trialSwitchesControlNativeStartsAndLocateIndependently(GameTestHelper helper)
            throws ReflectiveOperationException {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel underworld = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(underworld != null, "The actual underworld dimension is registered");
        Holder<Structure> surfaceTrial = structure(overworld, "minecraft:trial_chambers");
        Holder<Structure> deepTrial = structure(underworld, "mite:underworld_trial_chambers");
        boolean oldSurface = MiteConfig.OVERWORLD_TRIALS.get();
        boolean oldDeep = MiteConfig.UNDERWORLD_TRIALS.get();
        try (StructureUnlockTestState state = new StructureUnlockTestState(overworld)) {
            state.complete();
            MiteConfig.OVERWORLD_TRIALS.set(true);
            MiteConfig.UNDERWORLD_TRIALS.set(false);
            MiteConfig.refreshEnchantmentRules();
            assertNativeGenerationAndLocateRejected(helper, underworld, deepTrial);
            helper.assertTrue(R196StructureGeneration.unlocked(overworld, surfaceTrial),
                    "Disabling the underworld trial leaves the overworld trial eligible");

            CachedPresentManager cached = new CachedPresentManager(underworld);
            helper.assertTrue(candidate(underworld, cached, deepTrial) == null,
                    "A cached-present trial cannot bypass disabled generation");
            helper.assertValueEqual(cached.checks, 0, "Disabled lookup cannot read or poison the permanent presence cache");

            MiteConfig.UNDERWORLD_TRIALS.set(true);
            MiteConfig.OVERWORLD_TRIALS.set(false);
            MiteConfig.refreshEnchantmentRules();
            assertNativeGenerationAndLocateRejected(helper, overworld, surfaceTrial);
            helper.assertTrue(R196StructureGeneration.unlocked(underworld, deepTrial),
                    "The two trial switches remain independent after live changes");
            var recovered = candidate(underworld, cached, deepTrial);
            helper.assertTrue(recovered != null && recovered.getSecond().equals(deepTrial),
                    "Re-enabling generation immediately recovers the same native candidate");
            helper.assertValueEqual(cached.checks, 1, "Only the enabled lookup reaches native presence checking");
            helper.assertTrue(R196StructureGeneration.unlocked(overworld, structure(overworld, "minecraft:igloo")),
                    "Trial configuration leaves unrelated structures eligible");
        } finally {
            MiteConfig.OVERWORLD_TRIALS.set(oldSurface);
            MiteConfig.UNDERWORLD_TRIALS.set(oldDeep);
            MiteConfig.refreshEnchantmentRules();
        }
        helper.succeed();
    }

    private static Holder<Structure> structure(ServerLevel level, String id) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(
                ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(id)));
    }

    private static void assertNativeGenerationAndLocateRejected(GameTestHelper helper, ServerLevel level,
            Holder<Structure> structure) throws ReflectiveOperationException {
        ProtoChunk chunk = new ProtoChunk(COORDINATE, UpgradeData.EMPTY, level,
                level.registryAccess().registryOrThrow(Registries.BIOME), null);
        Method generate = ChunkGenerator.class.getDeclaredMethod("tryGenerateStructure",
                StructureSet.StructureSelectionEntry.class, StructureManager.class, RegistryAccess.class,
                RandomState.class, StructureTemplateManager.class, long.class, ChunkAccess.class,
                ChunkPos.class, SectionPos.class);
        generate.setAccessible(true);
        boolean result = (boolean)generate.invoke(level.getChunkSource().getGenerator(),
                new StructureSet.StructureSelectionEntry(structure, 1), level.structureManager(),
                level.registryAccess(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), chunk, COORDINATE, SectionPos.bottomOf(chunk));
        helper.assertFalse(result, "The transformed native start method rejects a disabled trial");
        helper.assertTrue(chunk.getAllStarts().isEmpty(), "Disabled generation never writes an invalid start");
        helper.assertTrue(level.getChunkSource().getGenerator().findNearestMapStructure(level,
                        HolderSet.direct(structure), COORDINATE.getWorldPosition(), 100, false) == null,
                "The actual locate entry rejects a disabled trial before scanning candidate regions");
    }

    @SuppressWarnings("unchecked")
    private static Pair<BlockPos, Holder<Structure>> candidate(ServerLevel level, StructureManager manager,
            Holder<Structure> structure) throws ReflectiveOperationException {
        Method locate = ChunkGenerator.class.getDeclaredMethod("getStructureGeneratingAt", Set.class,
                LevelReader.class, StructureManager.class, boolean.class, StructurePlacement.class, ChunkPos.class);
        locate.setAccessible(true);
        return (Pair<BlockPos, Holder<Structure>>)locate.invoke(null, Set.of(structure), level, manager, false,
                new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 10387312), COORDINATE);
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
}
