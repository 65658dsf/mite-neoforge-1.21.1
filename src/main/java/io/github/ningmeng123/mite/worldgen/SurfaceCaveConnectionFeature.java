package io.github.ningmeng123.mite.worldgen;

import io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules;
import io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Cell;
import io.github.ningmeng123.mite.rules.SurfaceCaveConnectionRules.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Short, dry links between nearby shallow cave chambers. @author NingMeng123 */
public final class SurfaceCaveConnectionFeature extends Feature<NoneFeatureConfiguration> {
    public SurfaceCaveConnectionFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!context.level().getLevel().dimension().equals(Level.OVERWORLD)) return false;
        return !connect(context.level().getChunk(context.origin()), context.level().getSeed()).links().isEmpty();
    }

    /** Plans from a local snapshot without reading neighboring chunks at different generation stages. */
    public static Plan connect(ChunkAccess chunk, long seed) {
        Snapshot snapshot = new Snapshot(chunk);
        Plan plan = SurfaceCaveConnectionRules.plan(snapshot, seed, chunk.getPos().x, chunk.getPos().z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (var block : plan.carved()) {
            pos.set(chunk.getPos().getMinBlockX() + block.x(), block.y(), chunk.getPos().getMinBlockZ() + block.z());
            chunk.setBlockState(pos, Blocks.CAVE_AIR.defaultBlockState(), false);
        }
        return plan;
    }

    private static Cell classify(BlockState state) {
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity()) return Cell.UNSAFE;
        if (state.isAir()) return Cell.AIR;
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE) || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE) ? Cell.ROCK : Cell.UNSAFE;
    }

    private static final class Snapshot implements SurfaceCaveConnectionRules.Access {
        private final int minY, maxY;
        private final Cell[] cells;
        private final int[] surfaces = new int[256];
        private final boolean[] dry = new boolean[256];

        Snapshot(ChunkAccess chunk) {
            // Include a floor and roof margin around the planner's Y8..160 foot positions.
            minY = Math.max(chunk.getMinBuildHeight(), 0);
            maxY = Math.min(chunk.getMaxBuildHeight() - 1, 168);
            cells = new Cell[Math.max(0, maxY - minY + 1) * 256];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int worldX = chunk.getPos().getMinBlockX() + x;
                int worldZ = chunk.getPos().getMinBlockZ() + z;
                // ChunkAccess returns the highest occupied Y; Access requires the first air Y.
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) + 1;
                surfaces[z * 16 + x] = surface;
                pos.set(worldX, surface - 1, worldZ);
                dry[z * 16 + x] = chunk.getBlockState(pos).getFluidState().isEmpty();
                for (int y = minY; y <= maxY; y++) {
                    pos.setY(y);
                    cells[(y - minY) * 256 + z * 16 + x] = classify(chunk.getBlockState(pos));
                }
            }
        }

        @Override public int minY() { return minY; }
        @Override public int maxY() { return maxY; }
        @Override public int surfaceY(int x, int z) { return surfaces[z * 16 + x]; }
        @Override public boolean dryColumn(int x, int z) { return dry[z * 16 + x]; }
        @Override public Cell cell(int x, int y, int z) {
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < minY || y > maxY) return Cell.UNSAFE;
            return cells[(y - minY) * 256 + z * 16 + x];
        }
    }
}
