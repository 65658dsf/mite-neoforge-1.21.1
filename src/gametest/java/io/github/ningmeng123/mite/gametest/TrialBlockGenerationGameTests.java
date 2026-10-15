package io.github.ningmeng123.mite.gametest;

import io.github.ningmeng123.mite.worldgen.MiteWorldDimensions;
import io.github.ningmeng123.mite.worldgen.NaturalTrialBlocks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real registered jigsaw generation exercises the production template provenance entry points. @author NingMeng123 */
@GameTestHolder("mite")
@PrefixGameTestTemplate(false)
public final class TrialBlockGenerationGameTests {
    private static final ResourceLocation SURFACE = ResourceLocation.withDefaultNamespace("trial_chambers");
    private static final ResourceLocation UNDERWORLD = ResourceLocation.parse("mite:underworld_trial_chambers");
    private static final List<String> POT_TEMPLATES = List.of("flow_pot", "guster_pot", "scrape_pot", "undecorated_pot");

    private TrialBlockGenerationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void registeredOverworldTrialTemplatesMarkOnlyActuallyPlacedBlocks(GameTestHelper helper) {
        verifyGeneration(helper, helper.getLevel().getServer().overworld(), SURFACE, new ChunkPos(-16120, 16540));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void registeredUnderworldTrialTemplatesMarkOnlyActuallyPlacedBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(MiteWorldDimensions.UNDERWORLD);
        helper.assertTrue(level != null, "The registered underworld exists for its actual generation entry point");
        verifyGeneration(helper, level, UNDERWORLD, new ChunkPos(-16180, 16540));
        helper.succeed();
    }

    private static void verifyGeneration(GameTestHelper helper, ServerLevel level, ResourceLocation id, ChunkPos origin) {
        Generated generated = generateWithPot(helper, level, id, origin);
        StructureStart start = generated.start();
        ChunkPos primary = new ChunkPos(start.getPieces().getFirst().getBoundingBox().getCenter());
        Map<ChunkPos, Placement> columns = new LinkedHashMap<>();
        columns.put(primary, new Placement(level, primary, start));
        ChunkPos potColumn = new ChunkPos(generated.pot());
        columns.computeIfAbsent(potColumn, coordinate -> new Placement(level, coordinate, start));
        helper.assertTrue(columns.size() <= 2, "Inspect only a room column and a real pot column, never the full chamber area");
        int lowestPiece = start.getPieces().stream().mapToInt(piece -> piece.getBoundingBox().minY()).min().orElseThrow();
        int copper = 0;
        int tuff = 0;
        int total = 0;
        for (Placement placement : columns.values()) {
            BlockPos untouched = placement.chunk.getPos().getWorldPosition().offset(8, lowestPiece - 2, 8);
            helper.assertTrue(!placement.chunk.isOutsideBuildHeight(untouched), "The untouched terrain control fits below every actual template piece");
            placement.region.setBlock(untouched, Blocks.STONE.defaultBlockState(), 2);
            start.placeInChunk(placement.region, level.structureManager(), level.getChunkSource().getGenerator(),
                    RandomSource.create(196L), placement.bounds, placement.chunk.getPos());
            helper.assertTrue(placement.chunk.getBlockState(untouched).is(Blocks.STONE), "Nearby original terrain outside actual template writes remains intact");
            helper.assertFalse(NaturalTrialBlocks.isNatural(placement.chunk, untouched), "Room columns do not blanket-mark their untouched surrounding terrain");
            for (BlockPos at : BlockPos.betweenClosed(placement.bounds.minX(), placement.bounds.minY(), placement.bounds.minZ(),
                    placement.bounds.maxX(), placement.bounds.maxY(), placement.bounds.maxZ())) {
                if (!NaturalTrialBlocks.isNatural(placement.chunk, at)) continue;
                BlockState state = placement.chunk.getBlockState(at);
                helper.assertFalse(state.isAir() || state.is(Blocks.BARRIER) || state.is(Blocks.STRUCTURE_VOID),
                        "Only a real final template block can carry generation provenance");
                total++;
                String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                if (block.contains("copper")) copper++;
                if (block.contains("tuff")) tuff++;
            }
            BlockPos laterPlacement = untouched.above();
            placement.region.setBlock(laterPlacement, Blocks.COPPER_BLOCK.defaultBlockState(), 2);
            helper.assertFalse(NaturalTrialBlocks.isNatural(placement.chunk, laterPlacement),
                    "A later ordinary placement at the same room column cannot inherit the finished generation scope");
        }
        Placement potPlacement = columns.get(potColumn);
        helper.assertTrue(potPlacement.chunk.getBlockState(generated.pot()).is(Blocks.DECORATED_POT),
                "The actual registered jigsaw start places its real decorated pot at its transformed template position");
        helper.assertTrue(NaturalTrialBlocks.isNatural(potPlacement.chunk, generated.pot()),
                "A real template block entity is marked after the temporary barrier, without any manual record fixture");
        helper.assertTrue(total > 50 && copper > 0 && tuff > 0,
                "Actual vanilla chamber templates contribute marked copper and tuff, rather than a synthetic block-only fixture");
        System.out.println("[MITE natural trial provenance] structure=" + id + " placedColumns=" + columns.size()
                + " blocks=" + total + " copper=" + copper + " tuff=" + tuff + " pot=" + generated.pot().toShortString());
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void failedRealPlacementAndNestedUnrelatedStructureRestoreGenerationScope(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().overworld();
        Generated generated = generateWithPot(helper, level, SURFACE, new ChunkPos(-16240, 16540));
        ChunkPos coordinate = new ChunkPos(generated.start().getPieces().getFirst().getBoundingBox().getCenter());
        Placement placement = new Placement(level, coordinate, generated.start());
        placement.region.failNextWrite = true;
        boolean failed = false;
        try {
            generated.start().placeInChunk(placement.region, level.structureManager(), level.getChunkSource().getGenerator(),
                    RandomSource.create(196L), placement.bounds, coordinate);
        } catch (PlacementFailure expected) {
            failed = true;
        }
        helper.assertTrue(failed, "The actual production StructureStart wrapper sees an exception from a template write");
        StructureTemplate singleCopper = singleCopper(level);
        BlockPos base = coordinate.getWorldPosition().offset(2, level.getMaxBuildHeight() - 4, 2);
        write(helper, placement, singleCopper, base);
        helper.assertFalse(NaturalTrialBlocks.isNatural(placement.chunk, base), "Exception unwinding does not leak the failed real structure's scope");
        Structure trial = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(SURFACE);
        Structure other = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.withDefaultNamespace("stronghold"));
        helper.assertTrue(other != null, "An independently registered non-trial structure supplies the nested scope");
        try (var ignored = NaturalTrialBlocks.generation(placement.region, trial)) {
            write(helper, placement, singleCopper, base.east());
            helper.assertTrue(NaturalTrialBlocks.isNatural(placement.chunk, base.east()), "A valid outer trial scope marks the actual template write");
            try (var nested = NaturalTrialBlocks.generation(placement.region, other)) {
                write(helper, placement, singleCopper, base.east(2));
                helper.assertFalse(NaturalTrialBlocks.isNatural(placement.chunk, base.east(2)), "An unrelated nested structure cannot inherit trial provenance");
            }
            write(helper, placement, singleCopper, base.east(3));
            helper.assertTrue(NaturalTrialBlocks.isNatural(placement.chunk, base.east(3)), "Returning from a nested structure restores the outer generation owner");
            try {
                try (var nested = NaturalTrialBlocks.generation(placement.region, other)) { throw new PlacementFailure(); }
            } catch (PlacementFailure expected) {
                // A nested exception must restore the same outer context as a normal return.
            }
            write(helper, placement, singleCopper, base.east(4));
            helper.assertTrue(NaturalTrialBlocks.isNatural(placement.chunk, base.east(4)), "An exceptional nested exit also restores the outer generation owner");
        }
        write(helper, placement, singleCopper, base.east(5));
        helper.assertFalse(NaturalTrialBlocks.isNatural(placement.chunk, base.east(5)), "The completed outer generation cannot mark subsequent ordinary template writes");
        helper.succeed();
    }

    private static Generated generateWithPot(GameTestHelper helper, ServerLevel level, ResourceLocation id, ChunkPos origin) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        helper.assertTrue(structure != null, "Use an actual registered trial chamber structure");
        var generator = level.getChunkSource().getGenerator();
        for (int attempt = 0; attempt < 4; attempt++) {
            // GameTest's flat overworld has no natural trial biome. This explicitly tests the real
            // registered generation/template implementation, not natural placement frequency.
            StructureStart start = structure.generate(level.registryAccess(), generator, generator.getBiomeSource(),
                    level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed() + attempt,
                    origin, 0, level, biome -> true);
            if (!start.isValid()) continue;
            for (var piece : start.getPieces()) {
                if (!(piece instanceof PoolElementStructurePiece pool)) continue;
                String description = pool.getElement().toString();
                for (String pot : POT_TEMPLATES) {
                    String name = "trial_chambers/decor/" + pot;
                    if (!description.contains(name)) continue;
                    StructureTemplate template = level.getStructureManager().getOrCreate(ResourceLocation.withDefaultNamespace(name));
                    var pots = template.filterBlocks(pool.getPosition(), new StructurePlaceSettings().setRotation(pool.getRotation()), Blocks.DECORATED_POT);
                    if (!pots.isEmpty()) return new Generated(start, pots.getFirst().pos());
                }
            }
        }
        helper.fail("Four real registered trial jigsaw assemblies must yield a decorated-pot piece without placing the full structure");
        throw new IllegalStateException("Unreachable after GameTest failure");
    }

    private static void write(GameTestHelper helper, Placement placement, StructureTemplate template, BlockPos pos) {
        boolean written = template.placeInWorld(placement.region, pos, pos,
                new StructurePlaceSettings().setKnownShape(true).setBoundingBox(placement.bounds).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING),
                RandomSource.create(196L), 2);
        helper.assertTrue(written && placement.chunk.getBlockState(pos).is(Blocks.COPPER_BLOCK), "The template control uses a successful native placement call");
    }

    private static StructureTemplate singleCopper(ServerLevel level) {
        CompoundTag source = new CompoundTag();
        source.put("size", integers(1, 1, 1));
        ListTag palette = new ListTag();
        CompoundTag copper = new CompoundTag();
        copper.putString("Name", "minecraft:copper_block");
        palette.add(copper);
        source.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.putInt("state", 0);
        block.put("pos", integers(0, 0, 0));
        blocks.add(block);
        source.put("blocks", blocks);
        source.put("entities", new ListTag());
        StructureTemplate template = new StructureTemplate();
        template.load(level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(), source);
        template.setAuthor("NingMeng123");
        return template;
    }

    private static ListTag integers(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    private record Generated(StructureStart start, BlockPos pot) {}

    private static final class PlacementFailure extends RuntimeException {}

    private static final class Placement {
        final ProtoChunk chunk;
        final PlacementRegion region;
        final BoundingBox bounds;

        Placement(ServerLevel level, ChunkPos coordinate, StructureStart start) {
            chunk = new ProtoChunk(coordinate, UpgradeData.EMPTY, level, level.registryAccess().registryOrThrow(Registries.BIOME), null);
            chunk.setPersistedStatus(ChunkStatus.CARVERS);
            bounds = new BoundingBox(coordinate.getMinBlockX(), level.getMinBuildHeight(), coordinate.getMinBlockZ(),
                    coordinate.getMaxBlockX(), level.getMaxBuildHeight() - 1, coordinate.getMaxBlockZ());
            // StructureTemplate.processBlockInfos runs processors over the complete intersecting
            // piece before placeInWorld checks its per-column bounding box. The actual trial
            // copper degradation RuleProcessor and ProtectedBlockProcessor both read each source
            // position. Derive their read dependency extent from every intersecting real piece,
            // including a one-block halo, instead of guessing a fixed neighboring chunk radius.
            int radius = 0;
            for (var piece : start.getPieces()) {
                BoundingBox box = piece.getBoundingBox();
                if (!box.intersects(bounds)) continue;
                int minX = Math.floorDiv(box.minX() - 1, 16);
                int maxX = Math.floorDiv(box.maxX() + 1, 16);
                int minZ = Math.floorDiv(box.minZ() - 1, 16);
                int maxZ = Math.floorDiv(box.maxZ() + 1, 16);
                radius = Math.max(radius, Math.max(Math.abs(coordinate.x - minX), Math.abs(coordinate.x - maxX)));
                radius = Math.max(radius, Math.max(Math.abs(coordinate.z - minZ), Math.abs(coordinate.z - maxZ)));
            }
            // Dependencies are only lightweight local ProtoChunks. The bounds still clip all
            // actual structure writes to the one selected column, without any FULL chunk loads.
            StaticCache2D<GenerationChunkHolder> cache = StaticCache2D.create(coordinate.x, coordinate.z, radius, (x, z) -> {
                ProtoChunk cached = x == coordinate.x && z == coordinate.z ? chunk
                        : new ProtoChunk(new ChunkPos(x, z), UpgradeData.EMPTY, level,
                                level.registryAccess().registryOrThrow(Registries.BIOME), null);
                cached.setPersistedStatus(ChunkStatus.CARVERS);
                return new GenerationChunkHolder(cached.getPos()) {
                    @Override public int getTicketLevel() { return 0; }
                    @Override public int getQueueLevel() { return 0; }
                    @Override public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus status) {
                        return cached.getPersistedStatus().isOrAfter(status) ? cached : null;
                    }
                };
            });
            region = new PlacementRegion(level, cache, chunk);
        }
    }

    private static final class PlacementRegion extends WorldGenRegion {
        boolean failNextWrite;

        PlacementRegion(ServerLevel level, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess center) {
            super(level, cache, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES), center);
        }

        @Override public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
            if (failNextWrite) {
                failNextWrite = false;
                throw new PlacementFailure();
            }
            return super.setBlock(pos, state, flags, recursion);
        }
    }
}
