package io.github.ningmeng123.mite.worldgen;

import com.mojang.serialization.MapCodec;
import io.github.ningmeng123.mite.registry.MiteAttachments;
import io.github.ningmeng123.mite.registry.MiteWorldBlocks;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/** Modern chunk lifecycle around the explicit height adaptation, with biome feature slots for ores. @author NingMeng123 */
public final class R196UnderworldGenerator extends ChunkGenerator {
    public static final MapCodec<R196UnderworldGenerator> CODEC = Biome.CODEC.fieldOf("biome")
            .xmap(R196UnderworldGenerator::new, generator -> generator.biome).stable();
    private final Holder<Biome> biome;
    private volatile ModernUnderworldTerrain terrain;

    public R196UnderworldGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures, RandomState state, long seed) {
        terrain = new ModernUnderworldTerrain(seed);
        // No vanilla structures implicitly enter this biome. Source dungeons await their R196 mobs/loot.
        return ChunkGeneratorStructureState.createForFlat(state, seed, biomeSource, Stream.empty());
    }

    private ModernUnderworldTerrain terrain() {
        ModernUnderworldTerrain current = terrain;
        if (current == null) throw new IllegalStateException("Underworld generator has not received the level seed");
        return current;
    }

    public static BlockState stateFor(byte block) {
        return switch (block) {
            case R196UnderworldTerrain.STONE -> Blocks.STONE.defaultBlockState();
            case R196UnderworldTerrain.BEDROCK -> Blocks.BEDROCK.defaultBlockState();
            case R196UnderworldTerrain.WATER -> Blocks.WATER.defaultBlockState();
            case R196UnderworldTerrain.COBWEB -> Blocks.COBWEB.defaultBlockState();
            case R196UnderworldTerrain.MANTLE -> MiteWorldBlocks.MANTLE.get().defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState state,
                                                       StructureManager structures, ChunkAccess chunk) {
        byte[] blocks = terrain().generate(chunk.getPos().x, chunk.getPos().z);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int min = Math.max(getMinY(), chunk.getMinBuildHeight());
        int max = Math.min(getMinY() + getGenDepth(), chunk.getMaxBuildHeight());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = min; y < max; y++) {
                    byte block = blocks[ModernUnderworldTerrain.index(x, y, z)];
                    if (block == R196UnderworldTerrain.AIR) continue;
                    BlockState blockState = stateFor(block);
                    chunk.setBlockState(position.set(x, y, z), blockState, false);
                    ocean.update(x, y, z, blockState);
                    surface.update(x, y, z, blockState);
                }
            }
        }
        chunk.setData(MiteAttachments.UNDERWORLD_LAYOUT_VERSION.get(), ModernUnderworldTerrain.CURRENT_LAYOUT_VERSION);
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        R196UnderworldVegetation.decorate(level, chunk.getPos().x, chunk.getPos().z, terrain().hashedSeed());
        super.applyBiomeDecoration(level, chunk, structures);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState state, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // R196 constructs a Nether cave generator but never invokes it for this dimension.
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState state, ChunkAccess chunk) {}
    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {}
    @Override
    public int getMinY() { return ModernUnderworldTerrain.MIN_Y; }
    @Override
    public int getGenDepth() { return ModernUnderworldTerrain.HEIGHT; }
    @Override
    public int getSeaLevel() { return ModernUnderworldTerrain.SEA_LEVEL; }
    @Override
    public int getSpawnHeight(LevelHeightAccessor level) { return ModernUnderworldTerrain.SPAWN_HEIGHT; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState state) {
        byte[] blocks = terrain().generate(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        BlockState[] column = new BlockState[level.getHeight()];
        for (int i = 0; i < column.length; i++) {
            int y = level.getMinBuildHeight() + i;
            column[i] = y >= getMinY() && y < getMinY() + getGenDepth()
                    ? stateFor(blocks[ModernUnderworldTerrain.index(Math.floorMod(x, 16), y, Math.floorMod(z, 16))])
                    : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), column);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor level, RandomState state) {
        NoiseColumn column = getBaseColumn(x, z, level, state);
        for (int y = Math.min(getMinY() + getGenDepth(), level.getMaxBuildHeight()) - 1;
             y >= Math.max(getMinY(), level.getMinBuildHeight()); y--) {
            if (heightmap.isOpaque().test(column.getBlock(y))) return y + 1;
        }
        return level.getMinBuildHeight();
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState state, BlockPos position) {
        lines.add("MITE underworld: min Y -64, height 384, mantle -64..-60, cavern 116..307");
    }
}
