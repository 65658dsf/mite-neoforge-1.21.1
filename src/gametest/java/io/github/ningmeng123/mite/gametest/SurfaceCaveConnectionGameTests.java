package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Plan;
import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import io.github.ningmeng123.mite.worldgen.SurfaceCaveConnectionFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Production shallow cave links, walkable connectivity, and protected terrain. @author NingMeng123 */
@GameTestHolder("mite_cave_links")
@PrefixGameTestTemplate(false)
public final class SurfaceCaveConnectionGameTests {
    private static final int MIN_FOOT_Y = 8;
    private static final int MAX_FOOT_Y = 160;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private SurfaceCaveConnectionGameTests() {}

    @GameTest(template = "empty", batch = "mite_cave_links")
    public static void connectorIsRegisteredOnceAtLocalModificationsInOverworldBiomesOnly(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.parse("mite:surface_cave_connections");
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).containsKey(id),
                "The real surface cave connection placed feature is loaded");
        int step = GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal();
        int overworld = 0;
        for (Holder.Reference<Biome> biome : helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            var features = biome.value().getGenerationSettings().features();
            long total = features.stream().flatMap(set -> set.stream())
                    .filter(feature -> feature.unwrapKey().map(key -> key.location().equals(id)).orElse(false)).count();
            long local = features.size() > step ? features.get(step).stream()
                    .filter(feature -> feature.unwrapKey().map(key -> key.location().equals(id)).orElse(false)).count() : 0L;
            long expected = biome.is(BiomeTags.IS_OVERWORLD) ? 1L : 0L;
            helper.assertValueEqual(local, expected, "LOCAL_MODIFICATIONS cave links match the overworld biome tag: " + biome.key().location());
            helper.assertValueEqual(total, expected, "No duplicate cave links occur at other decoration stages: " + biome.key().location());
            if (expected == 1L) overworld++;
        }
        helper.assertTrue(overworld > 0, "The loaded biome modification covers actual overworld biomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "mite_cave_links")
    public static void productionConnectorJoinsCoveredDryChambersWithWalkableHeadroom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int rise : new int[] {0, 1}) {
            ProtoChunk chunk = chamberFixture(level, new ChunkPos(-13, 7), rise, 100);
            PatchImage before = image(List.of(chunk));
            Connectivity oldWalk = connectivity(before);
            helper.assertValueEqual(oldWalk.substantialComponents(), 2,
                    "The fixture starts with two separate cave floors, including rise=" + rise);
            Plan plan = SurfaceCaveConnectionFeature.connect(chunk, 196L);
            PatchImage after = image(List.of(chunk));
            Connectivity newWalk = connectivity(after);
            helper.assertTrue(!plan.links().isEmpty(), "Production worldgen makes a dry cave link, rise=" + rise);
            helper.assertTrue(mergedComponents(oldWalk, newWalk) > 0,
                    "The link joins original walkable chambers rather than merely adding air, rise=" + rise);
            helper.assertValueEqual(newWalk.substantialComponents(), 1,
                    "Both covered cave floors can be reached with two-block headroom, rise=" + rise);
            verifyChanges(helper, before, after, Map.of(chunk.getPos().toLong(), plan));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "mite_cave_links")
    public static void fluidsBlockEntitiesArtificialWallsAndThinRoofsAreProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (BlockState barrier : List.of(Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(), Blocks.BEDROCK.defaultBlockState())) {
            ProtoChunk chunk = chamberFixture(level, new ChunkPos(3, -5), 0, 100);
            // A complete plane blocks every route between these two otherwise connectable chambers.
            for (int z = 0; z < 16; z++) for (int y = 0; y < 100; y++) set(chunk, 7, y, z, barrier);
            prime(chunk);
            PatchImage before = image(List.of(chunk));
            Plan plan = SurfaceCaveConnectionFeature.connect(chunk, 196L);
            helper.assertTrue(plan.links().isEmpty(), "Unsafe separating plane is preserved: " + barrier);
            helper.assertTrue(identical(before, image(List.of(chunk))), "No protected barrier or surrounding terrain changes: " + barrier);
        }
        ProtoChunk wet = chamberFixture(level, new ChunkPos(4, -5), 0, 100);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) set(wet, x, 100, z, Blocks.WATER.defaultBlockState());
        prime(wet);
        PatchImage wetBefore = image(List.of(wet));
        helper.assertTrue(SurfaceCaveConnectionFeature.connect(wet, 196L).links().isEmpty(), "Submerged columns do not receive cave links");
        helper.assertTrue(identical(wetBefore, image(List.of(wet))), "Surface water and wet terrain stay byte-for-byte unchanged");

        ProtoChunk thin = chamberFixture(level, new ChunkPos(5, -5), 0, 67);
        PatchImage thinBefore = image(List.of(thin));
        helper.assertTrue(SurfaceCaveConnectionFeature.connect(thin, 196L).links().isEmpty(), "Insufficient roof cover prevents linking");
        helper.assertTrue(identical(thinBefore, image(List.of(thin))), "Thin roofs are preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "mite_cave_links")
    public static void chunkBoundariesAndNeighborExecutionOrderAreStable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ProtoChunk firstA = chamberFixture(level, new ChunkPos(-1, -1), 0, 100);
        ProtoChunk firstB = chamberFixture(level, new ChunkPos(0, -1), 1, 100);
        ProtoChunk reverseA = chamberFixture(level, firstA.getPos(), 0, 100);
        ProtoChunk reverseB = chamberFixture(level, firstB.getPos(), 1, 100);
        PatchImage before = image(List.of(firstA, firstB));
        Plan forwardA = SurfaceCaveConnectionFeature.connect(firstA, 196L);
        Plan forwardB = SurfaceCaveConnectionFeature.connect(firstB, 196L);
        Plan backwardB = SurfaceCaveConnectionFeature.connect(reverseB, 196L);
        Plan backwardA = SurfaceCaveConnectionFeature.connect(reverseA, 196L);
        helper.assertTrue(forwardA.equals(backwardA) && forwardB.equals(backwardB),
                "The same seed and chunk snapshots produce the same plans in either neighbor order");
        helper.assertTrue(identical(image(List.of(firstA, firstB)), image(List.of(reverseA, reverseB))),
                "Neighbor execution order produces identical final block states, including negative chunk coordinates");
        helper.assertTrue(!forwardA.links().isEmpty() && !forwardB.links().isEmpty(), "Both order cases execute genuine carving");
        PatchImage after = image(List.of(firstA, firstB));
        verifyChanges(helper, before, after, Map.of(firstA.getPos().toLong(), forwardA, firstB.getPos().toLong(), forwardB));
        long boundaryChanges = 0;
        for (var entry : before.chunks.entrySet()) {
            ChunkImage original = entry.getValue();
            ChunkImage linked = after.chunks.get(entry.getKey());
            for (int y = original.minY; y < original.maxY; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if ((x == 0 || x == 15 || z == 0 || z == 15) && !original.state(x, y, z).equals(linked.state(x, y, z))) boundaryChanges++;
            }
        }
        helper.assertValueEqual(boundaryChanges, 0L, "Chunk boundary blocks are untouched; links are intentionally local to each chunk");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "mite_cave_links")
    public static void nativeNoiseAndActualCarversShowMeasuredWalkableConnections(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel().getServer().overworld();
        NoiseGeneratorSettings settings = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        JsonObject report = new JsonObject();
        report.addProperty("fixture", "Current overworld noise, current aquifer reduction, actual registered AIR carvers; no structures or surface decoration");
        report.addProperty("measurement", "Two-block air headroom, natural rock floor, cardinal movement with at most one-block steps; only covered dry Y8..160 floors at depth8..48");
        report.addProperty("scope", "Three independent seeds, each a 3x3 chunk patch; links are local to a chunk and do not guarantee cross-chunk connections");
        JsonArray samples = new JsonArray();
        JsonArray sections = new JsonArray();
        long totalLinks = 0;
        long totalCarved = 0;
        long totalMerged = 0;
        long totalSurfaceChanges = 0;
        long totalFluidChanges = 0;
        long totalOutsideChanges = 0;
        long totalRemovedWalkNodes = 0;
        long totalFragmentedComponents = 0;
        long totalConnectNanos = 0;
        long slowestConnectNanos = 0;
        ChunkPos slowestChunk = null;
        long slowestSeed = 0;
        long started = System.nanoTime();
        long[] seeds = {196L, 20261022L, 1L};
        ChunkPos[] centers = {new ChunkPos(0, 0), new ChunkPos(-17, 23), new ChunkPos(31, -29)};
        for (int sample = 0; sample < seeds.length; sample++) {
            long seed = seeds[sample];
            ChunkPos center = centers[sample];
            List<ProtoChunk> chunks = naturalPatch(level, settings, seed, center);
            PatchImage before = image(chunks);
            Connectivity oldWalk = connectivity(before);
            Map<Long, Plan> plans = new LinkedHashMap<>();
            long sampleConnectNanos = 0;
            for (ProtoChunk chunk : chunks) {
                long connectStart = System.nanoTime();
                Plan plan = SurfaceCaveConnectionFeature.connect(chunk, seed);
                long elapsed = System.nanoTime() - connectStart;
                sampleConnectNanos += elapsed;
                if (elapsed > slowestConnectNanos) {
                    slowestConnectNanos = elapsed;
                    slowestChunk = chunk.getPos();
                    slowestSeed = seed;
                }
                plans.put(chunk.getPos().toLong(), plan);
            }
            totalConnectNanos += sampleConnectNanos;
            PatchImage after = image(chunks);
            Connectivity newWalk = connectivity(after);
            ChangeCounts changes = verifyChanges(helper, before, after, plans);
            long links = plans.values().stream().mapToLong(plan -> plan.links().size()).sum();
            long merged = mergedComponents(oldWalk, newWalk);
            totalLinks += links;
            totalCarved += changes.carved;
            totalMerged += merged;
            totalSurfaceChanges += changes.surface;
            totalFluidChanges += changes.fluids;
            totalOutsideChanges += changes.outside;
            totalRemovedWalkNodes += changes.removedWalkNodes;
            totalFragmentedComponents += changes.fragmentedComponents;
            JsonObject row = new JsonObject();
            row.addProperty("seed", seed);
            row.addProperty("centerChunkX", center.x);
            row.addProperty("centerChunkZ", center.z);
            row.addProperty("chunks", chunks.size());
            row.addProperty("links", links);
            row.addProperty("carvedBlocks", changes.carved);
            row.addProperty("originalWalkableComponentsMerged", merged);
            row.addProperty("walkableFloorPositionsBefore", oldWalk.nodes);
            row.addProperty("walkableFloorPositionsAfter", newWalk.nodes);
            row.addProperty("substantialComponentsBefore", oldWalk.substantialComponents());
            row.addProperty("substantialComponentsAfter", newWalk.substantialComponents());
            row.addProperty("largestWalkableComponentBefore", oldWalk.largest());
            row.addProperty("largestWalkableComponentAfter", newWalk.largest());
            row.addProperty("fluidStateChanges", changes.fluids);
            row.addProperty("surfaceChanges", changes.surface);
            row.addProperty("outsideTargetChanges", changes.outside);
            row.addProperty("removedOriginalWalkableNodes", changes.removedWalkNodes);
            row.addProperty("fragmentedOriginalComponents", changes.fragmentedComponents);
            row.addProperty("connectMilliseconds", sampleConnectNanos / 1_000_000.0);
            samples.add(row);
            addSections(sections, before, after, plans, seed, center);
        }
        report.add("samples", samples);
        report.addProperty("totalLinks", totalLinks);
        report.addProperty("totalCarvedBlocks", totalCarved);
        report.addProperty("totalOriginalWalkableComponentsMerged", totalMerged);
        report.addProperty("totalSurfaceChanges", totalSurfaceChanges);
        report.addProperty("totalFluidStateChanges", totalFluidChanges);
        report.addProperty("totalOutsideTargetChanges", totalOutsideChanges);
        report.addProperty("totalRemovedOriginalWalkableNodes", totalRemovedWalkNodes);
        report.addProperty("totalFragmentedOriginalComponents", totalFragmentedComponents);
        report.addProperty("totalConnectMilliseconds", totalConnectNanos / 1_000_000.0);
        report.addProperty("slowestChunkConnectMilliseconds", slowestConnectNanos / 1_000_000.0);
        if (slowestChunk != null) {
            report.addProperty("slowestChunkX", slowestChunk.x);
            report.addProperty("slowestChunkZ", slowestChunk.z);
            report.addProperty("slowestChunkSeed", slowestSeed);
        }
        report.addProperty("elapsedMilliseconds", (System.nanoTime() - started) / 1_000_000L);
        JsonObject visualization = new JsonObject();
        visualization.addProperty("legend", "0=solid, 1=air, 2=water, 3=lava, 4=newly carved; rows run maxY down to minY, columns run minX to maxX");
        visualization.add("sections", sections);
        Path directory = projectRoot().resolve("build/reports");
        Files.createDirectories(directory);
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Files.writeString(directory.resolve("surface-cave-links.json"), gson.toJson(report), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("surface-cave-links-sections.json"), gson.toJson(visualization), StandardCharsets.UTF_8);
        helper.assertTrue(totalLinks > 0, "Actual noise plus registered carvers must contain successful cave links; inspect surface-cave-links.json if zero");
        helper.assertTrue(totalMerged > 0, "Natural generation must demonstrate original walkable components joining, not just added air");
        helper.succeed();
    }

    private static ProtoChunk chamberFixture(ServerLevel level, ChunkPos position, int rise, int surfaceY) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        ProtoChunk chunk = new ProtoChunk(position, UpgradeData.EMPTY, level, biomes, null);
        chunk.setPersistedStatus(ChunkStatus.CARVERS);
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) for (int y = level.getMinBuildHeight(); y < surfaceY; y++) {
            set(chunk, x, y, z, y == level.getMinBuildHeight() ? Blocks.BEDROCK.defaultBlockState() : Blocks.STONE.defaultBlockState());
        }
        for (int z = 5; z <= 10; z++) for (int y = 60; y <= 65; y++) {
            for (int x = 2; x <= 5; x++) set(chunk, x, y, z, Blocks.CAVE_AIR.defaultBlockState());
            for (int x = 10; x <= 13; x++) set(chunk, x, y + rise, z, Blocks.CAVE_AIR.defaultBlockState());
        }
        prime(chunk);
        return chunk;
    }

    /** Real terrain plus the native AIR carver pass; source chunks only supply carver biome/settings. */
    private static List<ProtoChunk> naturalPatch(ServerLevel level, NoiseGeneratorSettings settings, long seed, ChunkPos center) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        Holder.Reference<Biome> biome = biomes.getHolderOrThrow(Biomes.PLAINS);
        FixedBiomeSource source = new FixedBiomeSource(biome);
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(source, Holder.direct(settings));
        RandomState random = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        ((OverworldAquiferAccess) (Object) random).mite$setUndergroundWaterReduction(true);
        EmptyStructures structures = new EmptyStructures(level, seed);
        StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(center.x, center.z, 9, (x, z) -> {
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level, biomes, null);
            chunk.setPersistedStatus(ChunkStatus.SURFACE);
            chunk.fillBiomesFromNoise((qx, qy, qz, sampler) -> biome, random.sampler());
            chunk.carverBiome(() -> biome.value().getGenerationSettings());
            return new GenerationChunkHolder(chunk.getPos()) {
                @Override public int getTicketLevel() { return 0; }
                @Override public int getQueueLevel() { return 0; }
                @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
            };
        });
        List<ProtoChunk> chunks = new ArrayList<>();
        for (int z = center.z - 1; z <= center.z + 1; z++) for (int x = center.x - 1; x <= center.x + 1; x++) {
            ProtoChunk chunk = (ProtoChunk) cache.get(x, z).getChunkIfPresentUnchecked(ChunkStatus.SURFACE);
            generator.createBiomes(random, Blender.empty(), structures, chunk).join();
            generator.fillFromNoise(Blender.empty(), random, structures, chunk).join();
            chunks.add(chunk);
        }
        for (ProtoChunk chunk : chunks) {
            WorldGenRegion region = new WorldGenRegion(level, cache,
                    ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.CARVERS), chunk);
            generator.applyCarvers(region, seed, random, new BiomeManager(source, BiomeManager.obfuscateSeed(seed)),
                    structures, chunk, GenerationStep.Carving.AIR);
            chunk.setPersistedStatus(ChunkStatus.CARVERS);
            prime(chunk);
        }
        return chunks;
    }

    private static void set(ProtoChunk chunk, int x, int y, int z, BlockState state) {
        chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z), state, false);
    }

    private static void prime(ProtoChunk chunk) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    private static PatchImage image(List<ProtoChunk> chunks) {
        Map<Long, ChunkImage> result = new LinkedHashMap<>();
        for (ProtoChunk chunk : chunks) result.put(chunk.getPos().toLong(), new ChunkImage(chunk));
        return new PatchImage(result);
    }

    private static boolean identical(PatchImage before, PatchImage after) {
        if (!before.chunks.keySet().equals(after.chunks.keySet())) return false;
        for (long key : before.chunks.keySet()) {
            ChunkImage a = before.chunks.get(key), b = after.chunks.get(key);
            if (!Arrays.equals(a.states, b.states) || !Arrays.equals(a.surfaces, b.surfaces)) return false;
        }
        return true;
    }

    /** Counts every changed block, including the entire modern -64..319 vertical range. */
    private static ChangeCounts verifyChanges(GameTestHelper helper, PatchImage before, PatchImage after, Map<Long, Plan> plans) {
        long changed = 0, fluids = 0, surface = 0, outside = 0, protectedBlocks = 0;
        for (var entry : before.chunks.entrySet()) {
            ChunkImage original = entry.getValue(), linked = after.chunks.get(entry.getKey());
            Plan plan = plans.get(entry.getKey());
            Set<Integer> planned = new HashSet<>();
            for (var pos : plan.carved()) {
                helper.assertTrue(pos.x() >= 0 && pos.x() < 16 && pos.z() >= 0 && pos.z() < 16,
                        "Production plans stay within their own chunk");
                planned.add(original.index(pos.x(), pos.y(), pos.z()));
            }
            helper.assertValueEqual(planned.size(), plan.carved().size(), "Plans contain no duplicate carving positions");
            helper.assertTrue(plan.links().size() <= 3 && plan.carved().size() <= 240, "Each chunk keeps its link and block budget");
            long chunkChanges = 0;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int column = z * 16 + x;
                if (original.surfaces[column] != linked.surfaces[column]) surface++;
                for (int y = original.minY; y < original.maxY; y++) {
                    BlockState a = original.state(x, y, z), b = linked.state(x, y, z);
                    if (a.equals(b)) continue;
                    changed++;
                    chunkChanges++;
                    if (!a.getFluidState().equals(b.getFluidState())) fluids++;
                    if (y >= original.surfaces[column] - 5) surface++;
                    if (y < MIN_FOOT_Y || y > MAX_FOOT_Y + 2 || original.surfaces[column] - y > 48) outside++;
                    if (!naturalRock(a) || !b.isAir()) protectedBlocks++;
                    helper.assertTrue(planned.contains(original.index(x, y, z)), "Every actual block change belongs to the returned production plan");
                }
            }
            helper.assertValueEqual(chunkChanges, (long) plan.carved().size(), "Every planned carving position changes exactly one natural rock block");
        }
        helper.assertValueEqual(fluids, 0L, "No fluid block states change anywhere in the sampled chunks");
        helper.assertValueEqual(surface, 0L, "Surface heights and the protective roof remain unchanged");
        helper.assertValueEqual(outside, 0L, "No blocks outside the shallow target depth/height change");
        helper.assertValueEqual(protectedBlocks, 0L, "Only the explicit natural rock allowlist changes to air");
        Connectivity originalWalk = connectivity(before), linkedWalk = connectivity(after);
        Preservation preservation = preservation(originalWalk, linkedWalk);
        helper.assertValueEqual(preservation.removedNodes, 0L, "Every original eligible standing position stays walkable");
        helper.assertValueEqual(preservation.fragmentedComponents, 0L, "Every original walkable component stays in one after-component");
        return new ChangeCounts(changed, fluids, surface, outside, preservation.removedNodes, preservation.fragmentedComponents);
    }

    private static boolean naturalRock(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE);
    }

    private static Connectivity connectivity(PatchImage image) {
        int width = image.maxX - image.minX + 1, depth = image.maxZ - image.minZ + 1;
        int[] labels = new int[width * depth * (MAX_FOOT_Y - MIN_FOOT_Y + 1)];
        Arrays.fill(labels, -1);
        Connectivity result = new Connectivity(image, width, depth, labels);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = MIN_FOOT_Y; y <= MAX_FOOT_Y; y++) for (int z = image.minZ; z <= image.maxZ; z++) for (int x = image.minX; x <= image.maxX; x++) {
            int start = result.index(x, y, z);
            if (labels[start] >= 0 || !walkable(image, x, y, z)) continue;
            int component = result.sizes.size(), count = 0;
            labels[start] = component;
            queue.add(start);
            while (!queue.isEmpty()) {
                int index = queue.removeFirst();
                int localX = index % width;
                int localZ = (index / width) % depth;
                int footY = MIN_FOOT_Y + index / (width * depth);
                int worldX = image.minX + localX, worldZ = image.minZ + localZ;
                count++;
                for (int direction = 0; direction < 4; direction++) for (int step = -1; step <= 1; step++) {
                    int nx = worldX + DX[direction], nz = worldZ + DZ[direction], ny = footY + step;
                    if (nx < image.minX || nx > image.maxX || nz < image.minZ || nz > image.maxZ || ny < MIN_FOOT_Y || ny > MAX_FOOT_Y) continue;
                    int neighbor = result.index(nx, ny, nz);
                    if (labels[neighbor] >= 0 || !walkable(image, nx, ny, nz)) continue;
                    int upper = Math.max(footY, ny) + 1;
                    if (!image.state(worldX, upper, worldZ).isAir() || !image.state(nx, upper, nz).isAir()) continue;
                    labels[neighbor] = component;
                    queue.add(neighbor);
                }
            }
            result.sizes.add(count);
            result.nodes += count;
        }
        return result;
    }

    private static boolean walkable(PatchImage image, int x, int y, int z) {
        ChunkImage chunk = image.chunk(x, z);
        if (chunk == null) return false;
        int surface = chunk.surfaces[(z & 15) * 16 + (x & 15)];
        return surface - y >= 8 && surface - y <= 48
                && image.state(x, surface - 1, z).getFluidState().isEmpty()
                && image.state(x, y, z).isAir() && image.state(x, y + 1, z).isAir()
                && naturalRock(image.state(x, y - 1, z));
    }

    /** Only intact original components wholly assigned to the same after-label count as a merge. */
    private static long mergedComponents(Connectivity before, Connectivity after) {
        int[] targets = new int[before.sizes.size()];
        boolean[] invalid = new boolean[targets.length];
        Arrays.fill(targets, -1);
        for (int i = 0; i < before.labels.length; i++) {
            int original = before.labels[i], linked = after.labels[i];
            if (original < 0) continue;
            if (linked < 0) invalid[original] = true;
            else if (targets[original] == -1) targets[original] = linked;
            else if (targets[original] != linked) invalid[original] = true;
        }
        Map<Integer, Integer> groups = new HashMap<>();
        for (int original = 0; original < targets.length; original++) {
            if (!invalid[original] && targets[original] >= 0) groups.merge(targets[original], 1, Integer::sum);
        }
        long merged = 0;
        for (int count : groups.values()) merged += count - 1L;
        return merged;
    }

    private static Preservation preservation(Connectivity before, Connectivity after) {
        int[] targets = new int[before.sizes.size()];
        boolean[] fragmented = new boolean[targets.length];
        Arrays.fill(targets, -1);
        long removed = 0;
        for (int i = 0; i < before.labels.length; i++) {
            int original = before.labels[i], linked = after.labels[i];
            if (original < 0) continue;
            if (linked < 0) { removed++; continue; }
            if (targets[original] == -1) targets[original] = linked;
            else if (targets[original] != linked) fragmented[original] = true;
        }
        long count = 0;
        for (boolean split : fragmented) if (split) count++;
        return new Preservation(removed, count);
    }

    private static void addSections(JsonArray output, PatchImage before, PatchImage after, Map<Long, Plan> plans, long seed, ChunkPos center) {
        boolean alongX = true;
        int fixedCoordinate = center.getMiddleBlockZ();
        int longest = -1;
        JsonObject selectedLink = null;
        for (var entry : plans.entrySet()) for (var link : entry.getValue().links()) {
            int dx = link.end().x() - link.start().x(), dz = link.end().z() - link.start().z();
            int length = dx * dx + dz * dz;
            if (length <= longest) continue;
            longest = length;
            ChunkPos position = new ChunkPos(entry.getKey());
            alongX = Math.abs(dx) >= Math.abs(dz);
            fixedCoordinate = alongX
                    ? position.getMinBlockZ() + (link.start().z() + link.end().z()) / 2
                    : position.getMinBlockX() + (link.start().x() + link.end().x()) / 2;
            selectedLink = new JsonObject();
            selectedLink.addProperty("startX", position.getMinBlockX() + link.start().x());
            selectedLink.addProperty("startY", link.start().y());
            selectedLink.addProperty("startZ", position.getMinBlockZ() + link.start().z());
            selectedLink.addProperty("endX", position.getMinBlockX() + link.end().x());
            selectedLink.addProperty("endY", link.end().y());
            selectedLink.addProperty("endZ", position.getMinBlockZ() + link.end().z());
        }
        int minHorizontal = alongX ? before.minX : before.minZ;
        int maxHorizontal = alongX ? before.maxX : before.maxZ;
        JsonObject section = new JsonObject();
        section.addProperty("seed", seed);
        section.addProperty("axis", alongX ? "x" : "z");
        section.addProperty("fixedCoordinate", fixedCoordinate);
        section.addProperty("minHorizontal", minHorizontal);
        section.addProperty("maxHorizontal", maxHorizontal);
        section.addProperty("minX", before.minX);
        section.addProperty("maxX", before.maxX);
        section.addProperty("minZ", before.minZ);
        section.addProperty("maxZ", before.maxZ);
        section.addProperty("minY", MIN_FOOT_Y);
        section.addProperty("maxY", MAX_FOOT_Y);
        section.addProperty("width", maxHorizontal - minHorizontal + 1);
        section.addProperty("sliceLimitation", "A two-dimensional vertical slice through the selected link midpoint; bends and other three-dimensional connections may leave the slice");
        if (selectedLink != null) section.add("selectedLink", selectedLink);
        JsonArray originalRows = new JsonArray(), linkedRows = new JsonArray();
        for (int y = MAX_FOOT_Y; y >= MIN_FOOT_Y; y--) {
            StringBuilder original = new StringBuilder(), linked = new StringBuilder();
            for (int horizontal = minHorizontal; horizontal <= maxHorizontal; horizontal++) {
                int x = alongX ? horizontal : fixedCoordinate;
                int z = alongX ? fixedCoordinate : horizontal;
                BlockState a = before.state(x, y, z), b = after.state(x, y, z);
                original.append(pixel(a));
                linked.append(!a.equals(b) ? '4' : pixel(b));
            }
            originalRows.add(original.toString());
            linkedRows.add(linked.toString());
        }
        section.add("beforeRows", originalRows);
        section.add("afterRows", linkedRows);
        output.add(section);
    }

    private static char pixel(BlockState state) {
        if (state.isAir()) return '1';
        if (state.is(Blocks.WATER)) return '2';
        if (state.is(Blocks.LAVA)) return '3';
        return '0';
    }

    private static Path projectRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("build.gradle")) && Files.isDirectory(directory.resolve("src/main"))) return directory;
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate project root for cave connection evidence");
    }

    private static final class ChunkImage {
        final ChunkPos position;
        final int minY, maxY;
        final BlockState[] states;
        final int[] surfaces = new int[256];

        ChunkImage(ChunkAccess chunk) {
            position = chunk.getPos();
            minY = chunk.getMinBuildHeight();
            maxY = chunk.getMaxBuildHeight();
            states = new BlockState[(maxY - minY) * 256];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                surfaces[z * 16 + x] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1;
                for (int y = minY; y < maxY; y++) {
                    pos.set(position.getMinBlockX() + x, y, position.getMinBlockZ() + z);
                    states[index(x, y, z)] = chunk.getBlockState(pos);
                }
            }
        }

        int index(int x, int y, int z) { return (y - minY) * 256 + z * 16 + x; }
        BlockState state(int x, int y, int z) {
            return y < minY || y >= maxY ? Blocks.BEDROCK.defaultBlockState() : states[index(x, y, z)];
        }
    }

    private static final class PatchImage {
        final Map<Long, ChunkImage> chunks;
        final int minX, maxX, minZ, maxZ;

        PatchImage(Map<Long, ChunkImage> chunks) {
            this.chunks = chunks;
            minX = chunks.values().stream().mapToInt(c -> c.position.getMinBlockX()).min().orElseThrow();
            maxX = chunks.values().stream().mapToInt(c -> c.position.getMaxBlockX()).max().orElseThrow();
            minZ = chunks.values().stream().mapToInt(c -> c.position.getMinBlockZ()).min().orElseThrow();
            maxZ = chunks.values().stream().mapToInt(c -> c.position.getMaxBlockZ()).max().orElseThrow();
        }

        ChunkImage chunk(int x, int z) { return chunks.get(ChunkPos.asLong(x >> 4, z >> 4)); }
        BlockState state(int x, int y, int z) {
            ChunkImage chunk = chunk(x, z);
            return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.state(x & 15, y, z & 15);
        }
    }

    private static final class Connectivity {
        final PatchImage image;
        final int width, depth;
        final int[] labels;
        final List<Integer> sizes = new ArrayList<>();
        int nodes;

        Connectivity(PatchImage image, int width, int depth, int[] labels) {
            this.image = image;
            this.width = width;
            this.depth = depth;
            this.labels = labels;
        }

        int index(int x, int y, int z) { return ((y - MIN_FOOT_Y) * depth + z - image.minZ) * width + x - image.minX; }
        int substantialComponents() { return (int) sizes.stream().filter(size -> size >= 16).count(); }
        int largest() { return sizes.stream().mapToInt(Integer::intValue).max().orElse(0); }
    }

    private record Preservation(long removedNodes, long fragmentedComponents) {}
    private record ChangeCounts(long carved, long fluids, long surface, long outside, long removedWalkNodes, long fragmentedComponents) {}

    private static final class EmptyStructures extends StructureManager {
        EmptyStructures(ServerLevel level, long seed) { super(level, new WorldOptions(seed, false, false), null); }
        @Override public List<StructureStart> startsForStructure(ChunkPos position, Predicate<Structure> predicate) { return List.of(); }
    }
}
