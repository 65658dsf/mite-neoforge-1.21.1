package io.github.ningmeng123.mite.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.ningmeng123.mite.worldgen.OverworldAquiferAccess;
import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
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
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Paired native terrain and carver generation without loading or saving live world chunks. @author NingMeng123 */
@GameTestHolder("mite_caves")
@PrefixGameTestTemplate(false)
public final class UpperCaveGenerationGameTests {
    private static final ResourceLocation UPPER_CAVES = ResourceLocation.parse("mite:upper_legacy_caves");
    private static final long[] SEEDS = {196L, 20261022L, 1L, -7046029254386353131L};
    private static final ChunkPos[] POSITIONS = {
            new ChunkPos(0, 0), new ChunkPos(-17, 23), new ChunkPos(31, -29), new ChunkPos(-64, -64)};

    private UpperCaveGenerationGameTests() {}

    @GameTest(template = "empty")
    public static void upperCarverIsRegisteredOnceInOverworldBiomesOnly(GameTestHelper helper) {
        var carvers = helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_CARVER);
        helper.assertTrue(carvers.containsKey(UPPER_CAVES), "The configured upper legacy cave carver is loaded");
        int overworld = 0;
        for (Holder.Reference<Biome> biome : helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME).holders().toList()) {
            long occurrences = countUpperCarvers(biome.value().getGenerationSettings());
            if (biome.is(BiomeTags.IS_OVERWORLD)) {
                helper.assertValueEqual(occurrences, 1L, "Each overworld biome receives one upper cave carver: " + biome.key().location());
                overworld++;
            } else {
                helper.assertValueEqual(occurrences, 0L, "Other dimensions never receive the overworld carver: " + biome.key().location());
            }
        }
        helper.assertTrue(overworld > 0, "The resolved biome registry includes real overworld biomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200, batch = "mite_caves")
    public static void nativeNoiseAndCarversProduceSmallerDrierUpperCavesWithoutChangingOtherTerrain(GameTestHelper helper) throws IOException {
        ServerLevel level = helper.getLevel().getServer().overworld();
        var settingsRegistry = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings vanilla = vanillaSettings(level, "overworld");
        NoiseGeneratorSettings adjusted = settingsRegistry.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        JsonObject report = new JsonObject();
        JsonArray samples = new JsonArray();
        JsonArray sections = new JsonArray();
        Counts vanillaTotal = new Counts();
        Counts adjustedTotal = new Counts();
        Counts reducedTotal = new Counts();
        long outsideBandChanges = 0;
        long surfaceWaterChanges = 0;
        long lavaChanges = 0;
        long removedUndergroundWater = 0;
        long wetToDryColumns = 0;
        long borderColumnMismatches = 0;
        long started = System.nanoTime();
        for (long seed : SEEDS) for (ChunkPos position : POSITIONS) {
            ProtoChunk original = generateNoise(level, vanilla, seed, position, false);
            ProtoChunk smaller = generateNoise(level, adjusted, seed, position, false);
            ProtoChunk drier = generateNoise(level, adjusted, seed, position, true);
            if (seed == 196L) borderColumnMismatches += compareBorderColumns(level, adjusted, seed, drier);
            if (seed == 196L && position.x == 0 && position.z == 0)
                report.add("largeCaveDiagnosis", diagnoseLargeCaves(level, vanilla, original));
            Counts originalCounts = measure(original);
            Counts smallerCounts = measure(smaller);
            Counts drierCounts = measure(drier);
            vanillaTotal.add(originalCounts);
            adjustedTotal.add(smallerCounts);
            reducedTotal.add(drierCounts);
            long[] changes = compare(original, smaller, drier);
            outsideBandChanges += changes[0];
            surfaceWaterChanges += changes[1];
            lavaChanges += changes[2];
            removedUndergroundWater += changes[3];
            wetToDryColumns += changes[4];
            JsonObject sample = new JsonObject();
            sample.addProperty("seed", seed);
            sample.addProperty("chunkX", position.x);
            sample.addProperty("chunkZ", position.z);
            sample.add("vanilla", originalCounts.json());
            sample.add("smallCaves", smallerCounts.json());
            sample.add("reducedWater", drierCounts.json());
            sample.addProperty("outsideBandChanges", changes[0]);
            sample.addProperty("surfaceWaterChanges", changes[1]);
            sample.addProperty("lavaChanges", changes[2]);
            sample.addProperty("removedUndergroundWater", changes[3]);
            sample.addProperty("wetToDryColumns", changes[4]);
            samples.add(sample);
            if (sections.size() < 2) sections.add(section(seed, position, original, smaller, drier));
        }
        report.add("samples", samples);
        report.add("sections", sections);
        JsonObject totals = new JsonObject();
        totals.add("vanilla", vanillaTotal.json());
        totals.add("smallCaves", adjustedTotal.json());
        totals.add("reducedWater", reducedTotal.json());
        totals.addProperty("outsideBandChanges", outsideBandChanges);
        totals.addProperty("surfaceWaterChanges", surfaceWaterChanges);
        totals.addProperty("lavaChanges", lavaChanges);
        totals.addProperty("removedUndergroundWater", removedUndergroundWater);
        totals.addProperty("wetToDryColumns", wetToDryColumns);
        report.add("totals", totals);
        JsonObject waterSamples = searchUndergroundWater(level, adjusted);
        report.add("waterSamples", waterSamples);
        long verifiedRemovedUndergroundWater = removedUndergroundWater + waterSamples.get("removedUndergroundWater").getAsLong();
        report.addProperty("verifiedRemovedUndergroundWater", verifiedRemovedUndergroundWater);
        JsonObject carved = compareCarvers(level, adjusted);
        report.add("carvers", carved);
        report.addProperty("borderColumnMismatches", borderColumnMismatches);
        boolean deterministic = sameBlocks(generateNoise(level, adjusted, 196L, new ChunkPos(-17, 23), true),
                generateNoise(level, adjusted, 196L, new ChunkPos(-17, 23), true));
        report.addProperty("deterministic", deterministic);
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000D);
        Path output = projectRoot().resolve("build/verification/caves/cave-generation.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
        helper.assertTrue(vanillaTotal.large > 0, "Fixed native samples must contain large underground cave cores");
        helper.assertTrue(adjustedTotal.large < vanillaTotal.large, "The upper cheese cave bias reduces actual large cave cores: "
                + vanillaTotal.large + " -> " + adjustedTotal.large);
        helper.assertTrue(carved.get("addedAir").getAsLong() > 0 && carved.get("smallAdded").getAsLong() > 0,
                "The registered additional native carver creates real narrow upper tunnels");
        helper.assertValueEqual(outsideBandChanges, 0L, "Noise terrain below -24 and at or above 112 remains bit-for-bit unchanged");
        helper.assertValueEqual(surfaceWaterChanges, 0L, "Surface ocean and river water above its solid floor is retained");
        helper.assertValueEqual(lavaChanges, 0L, "The local underground water reduction retains native lava blocks");
        helper.assertTrue(verifiedRemovedUndergroundWater > 0, "The genuine aquifer path removes local underground water in fixed seed samples");
        helper.assertTrue(waterSamples.get("qualifyingSamples").getAsInt() > 0,
                "The bounded fixed-seed search exercises at least one real underground aquifer reservoir");
        helper.assertValueEqual(waterSamples.get("surfaceWaterChanges").getAsLong(), 0L,
                "Every searched chunk preserves surface-connected water");
        helper.assertValueEqual(waterSamples.get("lavaChanges").getAsLong(), 0L,
                "Every searched chunk preserves native lava blocks");
        helper.assertValueEqual(borderColumnMismatches, 0L, "Independent edge columns agree with native positive and negative chunk generation");
        helper.assertTrue(deterministic, "Separate negative-coordinate generations with the same seed remain identical");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "mite_caves")
    public static void liveDimensionMarkersAndIndependentGenerationKeepNetherAndEndUnchanged(GameTestHelper helper) throws IOException {
        var server = helper.getLevel().getServer();
        helper.assertTrue(((OverworldAquiferAccess) (Object) server.overworld().getChunkSource().randomState()).mite$undergroundWaterReduction(),
                "The actual overworld chunk source enables underground water reduction");
        for (var dimension : List.of(Level.NETHER, Level.END, MiteWorldDimensions.UNDERWORLD)) {
            ServerLevel level = server.getLevel(dimension);
            helper.assertTrue(level != null && !((OverworldAquiferAccess) (Object) level.getChunkSource().randomState()).mite$undergroundWaterReduction(),
                    "Other live dimensions retain their ordinary water behavior: " + dimension.location());
        }
        for (String name : new String[]{"nether", "end"}) {
            ServerLevel level = server.getLevel(name.equals("nether") ? Level.NETHER : Level.END);
            NoiseGeneratorSettings settings = level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS)
                    .getOrThrow(name.equals("nether") ? NoiseGeneratorSettings.NETHER : NoiseGeneratorSettings.END);
            RandomState independent = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), 196L);
            helper.assertFalse(((OverworldAquiferAccess) (Object) independent).mite$undergroundWaterReduction(),
                    "An independently constructed RandomState never opts into overworld water changes");
            ChunkPos coordinate = new ChunkPos(-4, 7);
            helper.assertTrue(sameBlocks(generateNoise(level, vanillaSettings(level, name), 196L, coordinate, false),
                            generateNoise(level, settings, 196L, coordinate, false)),
                    "Actual native " + name + " noise blocks remain identical to the shipped preset");
        }
        helper.succeed();
    }

    private static long countUpperCarvers(BiomeGenerationSettings settings) {
        long count = 0;
        for (Holder<ConfiguredWorldCarver<?>> carver : settings.getCarvers(GenerationStep.Carving.AIR))
            if (carver.unwrapKey().map(key -> key.location().equals(UPPER_CAVES)).orElse(false)) count++;
        return count;
    }

    private static ProtoChunk generateNoise(ServerLevel level, NoiseGeneratorSettings settings, long seed,
            ChunkPos position, boolean reduceWater) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var fixed = new FixedBiomeSource(biomes.getHolderOrThrow(Biomes.PLAINS));
        var generator = new NoiseBasedChunkGenerator(fixed, Holder.direct(settings));
        RandomState random = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        ((OverworldAquiferAccess) (Object) random).mite$setUndergroundWaterReduction(reduceWater);
        ProtoChunk chunk = new ProtoChunk(position, UpgradeData.EMPTY, level, biomes, null);
        StructureManager structures = new EmptyStructures(level, seed);
        generator.createBiomes(random, Blender.empty(), structures, chunk).join();
        generator.fillFromNoise(Blender.empty(), random, structures, chunk).join();
        return chunk;
    }

    private static JsonObject compareCarvers(ServerLevel level, NoiseGeneratorSettings settings) {
        Counts original = new Counts();
        Counts modified = new Counts();
        long added = 0;
        long smallAdded = 0;
        for (long seed : new long[]{196L, 20261022L}) for (ChunkPos position : new ChunkPos[]{POSITIONS[0], POSITIONS[1]}) {
            ProtoChunk before = carveRock(level, settings, seed, position, true);
            ProtoChunk after = carveRock(level, settings, seed, position, false);
            original.add(measure(before));
            modified.add(measure(after));
            BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
            for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = 0; height <= 112; height++) {
                point.set(position.getMinBlockX() + offsetX, height, position.getMinBlockZ() + offsetZ);
                if (!before.getBlockState(point).isAir() && after.getBlockState(point).isAir()) {
                    added++;
                    if (small(after, point)) smallAdded++;
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("baselineAir", original.air);
        result.addProperty("adjustedAir", modified.air);
        result.addProperty("addedAir", added);
        result.addProperty("smallAdded", smallAdded);
        result.add("baseline", original.json());
        result.add("adjusted", modified.json());
        return result;
    }

    private static long compareBorderColumns(ServerLevel level, NoiseGeneratorSettings settings, long seed, ProtoChunk chunk) {
        var fixed = new FixedBiomeSource(level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS));
        var generator = new NoiseBasedChunkGenerator(fixed, Holder.direct(settings));
        RandomState random = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        ((OverworldAquiferAccess) (Object) random).mite$setUndergroundWaterReduction(true);
        long differences = 0;
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX : new int[]{0, 15}) for (int offsetZ : new int[]{0, 15}) {
            int blockX = chunk.getPos().getMinBlockX() + offsetX;
            int blockZ = chunk.getPos().getMinBlockZ() + offsetZ;
            var column = generator.getBaseColumn(blockX, blockZ, level, random);
            for (int height = chunk.getMinBuildHeight(); height < chunk.getMaxBuildHeight(); height++)
                if (!chunk.getBlockState(point.set(blockX, height, blockZ)).equals(column.getBlock(height))) differences++;
        }
        return differences;
    }

    private static JsonObject searchUndergroundWater(ServerLevel level, NoiseGeneratorSettings settings) {
        int[] offsets = {4, -4, 8, -8, 12, -12, 16, -16};
        JsonArray samples = new JsonArray();
        int scanned = 0;
        long removed = 0;
        long surfaceChanges = 0;
        long lavaChanges = 0;
        search:
        for (int chunkX : offsets) for (int chunkZ : offsets) for (long seed : new long[]{196L, 20261022L}) {
            ChunkPos position = new ChunkPos(chunkX, chunkZ);
            ProtoChunk original = generateNoise(level, settings, seed, position, false);
            ProtoChunk reduced = generateNoise(level, settings, seed, position, true);
            scanned++;
            long[] changes = compare(original, original, reduced);
            surfaceChanges += changes[1];
            lavaChanges += changes[2];
            removed += changes[3];
            if (changes[3] > 0) {
                Counts before = measure(original);
                Counts after = measure(reduced);
                JsonObject sample = new JsonObject();
                sample.addProperty("seed", seed);
                sample.addProperty("chunkX", chunkX);
                sample.addProperty("chunkZ", chunkZ);
                sample.addProperty("waterBefore", before.waterUnderground);
                sample.addProperty("waterAfter", after.waterUnderground);
                sample.addProperty("surfaceWaterBefore", before.waterSurface);
                sample.addProperty("surfaceWaterAfter", after.waterSurface);
                sample.addProperty("removedUndergroundWater", changes[3]);
                sample.addProperty("surfaceWaterChanges", changes[1]);
                sample.addProperty("lavaChanges", changes[2]);
                sample.addProperty("wetToDryColumns", changes[4]);
                samples.add(sample);
                if (samples.size() >= 3) break search;
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("maxPairs", offsets.length * offsets.length * 2);
        result.addProperty("scannedPairs", scanned);
        result.addProperty("qualifyingSamples", samples.size());
        result.addProperty("removedUndergroundWater", removed);
        result.addProperty("surfaceWaterChanges", surfaceChanges);
        result.addProperty("lavaChanges", lavaChanges);
        result.add("samples", samples);
        return result;
    }

    private static ProtoChunk carveRock(ServerLevel level, NoiseGeneratorSettings settings, long seed,
            ChunkPos position, boolean originalCarvers) {
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        var biome = biomes.getHolderOrThrow(Biomes.PLAINS);
        var fixed = new FixedBiomeSource(biome);
        var generator = new NoiseBasedChunkGenerator(fixed, Holder.direct(settings));
        RandomState random = RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        StaticCache2D<GenerationChunkHolder> chunks = StaticCache2D.create(position.x, position.z, 8, (chunkX, chunkZ) -> {
            ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, level, biomes, null);
            chunk.setPersistedStatus(ChunkStatus.SURFACE);
            chunk.fillBiomesFromNoise((quartX, quartY, quartZ, sampler) -> biome, random.sampler());
            chunk.carverBiome(() -> originalCarvers
                    ? biome.value().modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings()
                    : biome.value().getGenerationSettings());
            return new GenerationChunkHolder(chunk.getPos()) {
                @Override public int getTicketLevel() { return 0; }
                @Override public int getQueueLevel() { return 0; }
                @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) { return chunk; }
            };
        });
        ProtoChunk center = (ProtoChunk) chunks.get(position.x, position.z).getChunkIfPresentUnchecked(ChunkStatus.SURFACE);
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) for (int height = -64; height <= 128; height++)
            center.setBlockState(point.set(position.getMinBlockX() + offsetX, height, position.getMinBlockZ() + offsetZ),
                    (height == -64 ? Blocks.BEDROCK : Blocks.STONE).defaultBlockState(), false);
        var dry = new NoiseGeneratorSettings(settings.noiseSettings(), settings.defaultBlock(), Blocks.AIR.defaultBlockState(),
                settings.noiseRouter(), settings.surfaceRule(), settings.spawnTarget(), settings.seaLevel(),
                settings.disableMobGeneration(), false, false, settings.useLegacyRandomSource());
        center.getOrCreateNoiseChunk(chunk -> NoiseChunk.forChunk(chunk, random,
                Beardifier.forStructuresInChunk(new EmptyStructures(level, seed), position),
                dry, (blockX, blockY, blockZ) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState()), Blender.empty()));
        WorldGenRegion region = new WorldGenRegion(level, chunks, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.CARVERS), center);
        generator.applyCarvers(region, seed, random, new BiomeManager(fixed, BiomeManager.obfuscateSeed(seed)),
                new EmptyStructures(level, seed), center, GenerationStep.Carving.AIR);
        return center;
    }

    private static Counts measure(ProtoChunk chunk) {
        Counts counts = new Counts();
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) {
            int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, offsetX, offsetZ);
            for (int height = chunk.getMinBuildHeight(); height < chunk.getMaxBuildHeight(); height++) {
                point.set(chunk.getPos().getMinBlockX() + offsetX, height, chunk.getPos().getMinBlockZ() + offsetZ);
                BlockState state = chunk.getBlockState(point);
                if (state.getFluidState().is(FluidTags.LAVA)) counts.lava++;
                if (state.getFluidState().is(FluidTags.WATER)) {
                    if (height > floor) counts.waterSurface++;
                    else counts.waterUnderground++;
                }
                if (height >= 8 && height <= 80 && height <= floor - 8 && state.isAir()) {
                    counts.air++;
                    if (small(chunk, point)) counts.small++;
                    if (span(chunk, point, 1, 0, 0).length >= 11 && span(chunk, point, 0, 1, 0).length >= 11
                            && span(chunk, point, 0, 0, 1).length >= 11) counts.large++;
                }
            }
        }
        return counts;
    }

    private static boolean small(ProtoChunk chunk, BlockPos point) {
        Span vertical = span(chunk, point, 0, 1, 0);
        if (!vertical.closed || vertical.length > 7) return false;
        Span acrossX = span(chunk, point, 1, 0, 0);
        Span acrossZ = span(chunk, point, 0, 0, 1);
        return acrossX.closed && acrossX.length <= 7 || acrossZ.closed && acrossZ.length <= 7;
    }

    private record Span(int length, boolean closed) {}

    private static Span span(ProtoChunk chunk, BlockPos point, int stepX, int stepY, int stepZ) {
        int length = 1;
        boolean closed = true;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int direction : new int[]{-1, 1}) {
            for (int distance = 1; distance <= 12; distance++) {
                probe.set(point.getX() + stepX * distance * direction, point.getY() + stepY * distance * direction,
                        point.getZ() + stepZ * distance * direction);
                if (probe.getX() < chunk.getPos().getMinBlockX() || probe.getX() > chunk.getPos().getMaxBlockX()
                        || probe.getZ() < chunk.getPos().getMinBlockZ() || probe.getZ() > chunk.getPos().getMaxBlockZ()
                        || chunk.isOutsideBuildHeight(probe)) { closed = false; break; }
                if (!chunk.getBlockState(probe).isAir()) break;
                length++;
                if (distance == 12) closed = false;
            }
        }
        return new Span(length, closed);
    }

    private static long[] compare(ProtoChunk original, ProtoChunk adjusted, ProtoChunk reduced) {
        long[] changes = new long[5];
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) {
            boolean wasWet = false;
            boolean remainsWet = false;
            int floor = adjusted.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, offsetX, offsetZ);
            for (int height = original.getMinBuildHeight(); height < original.getMaxBuildHeight(); height++) {
                point.set(original.getPos().getMinBlockX() + offsetX, height, original.getPos().getMinBlockZ() + offsetZ);
                BlockState before = original.getBlockState(point);
                BlockState smaller = adjusted.getBlockState(point);
                BlockState drier = reduced.getBlockState(point);
                if ((height <= -24 || height >= 112) && !before.equals(smaller)) changes[0]++;
                if (height > floor && (smaller.getFluidState().is(FluidTags.WATER) || drier.getFluidState().is(FluidTags.WATER))
                        && !smaller.equals(drier)) changes[1]++;
                if ((smaller.getFluidState().is(FluidTags.LAVA) || drier.getFluidState().is(FluidTags.LAVA)) && !smaller.equals(drier)) changes[2]++;
                if (height <= floor - 8) {
                    boolean waterBefore = smaller.getFluidState().is(FluidTags.WATER);
                    boolean waterAfter = drier.getFluidState().is(FluidTags.WATER);
                    wasWet |= waterBefore;
                    remainsWet |= waterAfter;
                    if (waterBefore && !waterAfter) changes[3]++;
                }
            }
            if (wasWet && !remainsWet) changes[4]++;
        }
        return changes;
    }

    private static boolean sameBlocks(ProtoChunk first, ProtoChunk second) {
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++)
            for (int height = first.getMinBuildHeight(); height < first.getMaxBuildHeight(); height++) {
                point.set(first.getPos().getMinBlockX() + offsetX, height, first.getPos().getMinBlockZ() + offsetZ);
                if (!first.getBlockState(point).equals(second.getBlockState(point))) return false;
            }
        return true;
    }

    private static JsonObject section(long seed, ChunkPos position, ProtoChunk original, ProtoChunk adjusted, ProtoChunk reduced) {
        JsonObject section = new JsonObject();
        section.addProperty("seed", seed);
        section.addProperty("chunkX", position.x);
        section.addProperty("chunkZ", position.z);
        section.addProperty("blockX", position.getMinBlockX());
        section.addProperty("blockZ", position.getMinBlockZ() + 8);
        section.addProperty("minY", -32);
        section.addProperty("maxY", 112);
        section.addProperty("rows", "descending_y");
        section.add("vanilla", slice(original));
        section.add("smallCaves", slice(adjusted));
        section.add("reducedWater", slice(reduced));
        return section;
    }

    private static JsonArray slice(ProtoChunk chunk) {
        JsonArray rows = new JsonArray();
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int height = 112; height >= -32; height--) {
            JsonArray row = new JsonArray();
            for (int offsetX = 0; offsetX < 16; offsetX++) {
                BlockState state = chunk.getBlockState(point.set(chunk.getPos().getMinBlockX() + offsetX, height, chunk.getPos().getMinBlockZ() + 8));
                row.add(state.getFluidState().is(FluidTags.WATER) ? 2 : state.getFluidState().is(FluidTags.LAVA) ? 3 : state.isAir() ? 1 : 0);
            }
            rows.add(row);
        }
        return rows;
    }

    private static NoiseGeneratorSettings vanillaSettings(ServerLevel level, String name) throws IOException {
        return NoiseGeneratorSettings.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()),
                vanillaSettingsJson(name)).getOrThrow();
    }

    private static JsonObject vanillaSettingsJson(String name) throws IOException {
        JsonObject json = vanillaResource("data/minecraft/worldgen/noise_settings/" + name + ".json");
        json.addProperty("ore_veins_enabled", false);
        return json;
    }

    private static JsonObject vanillaResource(String resource) throws IOException {
        Path resourceJar = projectRoot().resolve("build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar");
        try (ZipFile archive = new ZipFile(resourceJar.toFile())) {
            var entry = archive.getEntry(resource);
            try (var input = archive.getInputStream(entry)) {
                return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    private static JsonObject diagnoseLargeCaves(ServerLevel level, NoiseGeneratorSettings vanilla, ProtoChunk chunk) throws IOException {
        JsonObject entrancesJson = vanillaResource("data/minecraft/worldgen/density_function/overworld/caves/entrances.json")
                .getAsJsonObject("argument");
        DensityFunction sloped = diagnosticDensity(level, new JsonPrimitive("minecraft:overworld/sloped_cheese"));
        DensityFunction entrances = diagnosticDensity(level, new JsonPrimitive("minecraft:overworld/caves/entrances"));
        DensityFunction broadEntrance = diagnosticDensity(level, entrancesJson.get("argument1"));
        DensityFunction spaghetti = diagnosticDensity(level, entrancesJson.get("argument2"));
        var router = RandomState.create(vanilla, level.registryAccess().lookupOrThrow(Registries.NOISE), 196L).router();
        long points = 0;
        long shallowBranch = 0;
        long terrainNegative = 0;
        long broadEntranceNegative = 0;
        long spaghettiNegative = 0;
        long broadEntranceControls = 0;
        JsonArray examples = new JsonArray();
        BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
        for (int offsetX = 0; offsetX < 16; offsetX++) for (int offsetZ = 0; offsetZ < 16; offsetZ++) {
            int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, offsetX, offsetZ);
            for (int height = 8; height <= Math.min(80, floor - 8); height++) {
                point.set(chunk.getPos().getMinBlockX() + offsetX, height, chunk.getPos().getMinBlockZ() + offsetZ);
                if (!chunk.getBlockState(point).isAir() || span(chunk, point, 1, 0, 0).length < 11
                        || span(chunk, point, 0, 1, 0).length < 11 || span(chunk, point, 0, 0, 1).length < 11) continue;
                var context = new DensityFunction.SinglePointContext(point.getX(), height, point.getZ());
                double terrain = sloped.compute(context);
                double broad = broadEntrance.compute(context);
                double thin = spaghetti.compute(context);
                points++;
                if (terrain < 1.5625) shallowBranch++;
                if (terrain < 0) terrainNegative++;
                if (broad < 0) broadEntranceNegative++;
                if (thin < 0) spaghettiNegative++;
                if (broad < 0 && broad <= thin) broadEntranceControls++;
                if (examples.size() < 12 && (points == 1 || points % 211 == 0)) {
                    JsonObject example = new JsonObject();
                    example.addProperty("x", point.getX());
                    example.addProperty("y", height);
                    example.addProperty("z", point.getZ());
                    example.addProperty("slopedCheese", terrain);
                    example.addProperty("entrances", entrances.compute(context));
                    example.addProperty("broadEntrance", broad);
                    example.addProperty("spaghetti3d", thin);
                    example.addProperty("initialDensity", router.initialDensityWithoutJaggedness().compute(context));
                    example.addProperty("finalDensity", router.finalDensity().compute(context));
                    examples.add(example);
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("uninterpolatedNoiseDiagnosis", true);
        result.addProperty("largeAirPoints", points);
        result.addProperty("shallowBranchBypassingCheese", shallowBranch);
        result.addProperty("terrainNegative", terrainNegative);
        result.addProperty("broadEntranceNegative", broadEntranceNegative);
        result.addProperty("spaghetti3dNegative", spaghettiNegative);
        result.addProperty("broadEntranceControls", broadEntranceControls);
        result.add("examples", examples);
        return result;
    }

    private static DensityFunction diagnosticDensity(ServerLevel level, JsonElement density) throws IOException {
        JsonObject json = vanillaSettingsJson("overworld");
        json.getAsJsonObject("noise_router").add("final_density", density);
        NoiseGeneratorSettings settings = NoiseGeneratorSettings.DIRECT_CODEC.parse(
                RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), json).getOrThrow();
        return RandomState.create(settings, level.registryAccess().lookupOrThrow(Registries.NOISE), 196L).router().finalDensity();
    }

    private static Path projectRoot() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent())
            if (Files.isRegularFile(current.resolve("build.gradle")) && Files.isDirectory(current.resolve("src/main"))) return current;
        throw new IllegalStateException("Cannot locate the development project for vanilla resources and cave reports");
    }

    private static final class Counts {
        private long air;
        private long small;
        private long large;
        private long waterUnderground;
        private long waterSurface;
        private long lava;

        private void add(Counts other) {
            air += other.air;
            small += other.small;
            large += other.large;
            waterUnderground += other.waterUnderground;
            waterSurface += other.waterSurface;
            lava += other.lava;
        }

        private JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("air", air);
            result.addProperty("small", small);
            result.addProperty("large", large);
            result.addProperty("waterUnderground", waterUnderground);
            result.addProperty("waterSurface", waterSurface);
            result.addProperty("lava", lava);
            return result;
        }
    }

    private static final class EmptyStructures extends StructureManager {
        private EmptyStructures(ServerLevel level, long seed) { super(level, new WorldOptions(seed, false, false), null); }
        @Override public List<StructureStart> startsForStructure(ChunkPos position, Predicate<Structure> predicate) { return List.of(); }
    }
}
