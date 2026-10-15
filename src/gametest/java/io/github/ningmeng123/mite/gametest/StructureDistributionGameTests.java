package io.github.ningmeng123.mite.gametest;

import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verify registered distribution resources and the transformed asynchronous engine, not just helpers. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class StructureDistributionGameTests {
    private StructureDistributionGameTests() {}

    @GameTest(template = "empty")
    public static void sourceDistributionIsPresentInTheActualStructureRegistry(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for (String name : List.of("villages", "desert_pyramids", "jungle_temples", "swamp_huts")) {
            var set = registry.get(ResourceLocation.withDefaultNamespace(name));
            h.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement, "Native random-spread placement retained: " + name);
            var placement = (RandomSpreadStructurePlacement) set.placement();
            h.assertValueEqual(placement.spacing(), 40, "Actual spacing: " + name);
            h.assertValueEqual(placement.separation(), 20, "Actual separation: " + name);
            int salt = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement).getOrThrow()
                    .getAsJsonObject().get("salt").getAsInt();
            h.assertValueEqual(salt, name.equals("villages") ? 10387312 : 14357617, "Actual source salt: " + name);
        }
        h.assertValueEqual(registry.get(ResourceLocation.withDefaultNamespace("villages")).structures().size(), 5,
                "All five modern village styles remain registered");
        h.assertTrue(registry.get(ResourceLocation.withDefaultNamespace("trial_chambers")) != null,
                "Modern independent structures remain available");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void actualStrongholdFutureUsesThreeSourceRadiusCandidates(GameTestHelper h) throws ReflectiveOperationException {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        var strongholds = registry.getHolder(ResourceLocation.withDefaultNamespace("strongholds")).orElseThrow();
        var placement = (ConcentricRingsStructurePlacement) strongholds.value().placement();
        var state = h.getLevel().getChunkSource().getGeneratorState();
        List<ChunkPos> first = generate(state, strongholds, placement);
        List<ChunkPos> second = generate(state, strongholds, placement);
        h.assertValueEqual(first.size(), 3, "The transformed engine produces exactly three candidates");
        h.assertTrue(first.equals(second), "Asynchronous recomputation remains deterministic for this save");
        for (ChunkPos coordinate : first) {
            double radius = Math.hypot(coordinate.x * 16D, coordinate.z * 16D);
            h.assertTrue(radius > 10000D && radius < 18700D,
                    "Source 10240..18432 radius plus local biome search, not the modern 22528..43008 range");
        }
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static List<ChunkPos> generate(ChunkGeneratorStructureState state, Holder<StructureSet> structure,
            ConcentricRingsStructurePlacement placement) throws ReflectiveOperationException {
        Method method = ChunkGeneratorStructureState.class.getDeclaredMethod("generateRingPositions", Holder.class,
                ConcentricRingsStructurePlacement.class);
        method.setAccessible(true);
        return ((CompletableFuture<List<ChunkPos>>) method.invoke(state, structure, placement)).join();
    }
}
