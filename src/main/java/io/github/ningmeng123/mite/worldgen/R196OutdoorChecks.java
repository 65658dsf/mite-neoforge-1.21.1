package io.github.ningmeng123.mite.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.levelgen.Heightmap;

/** Source abw.isOutdoors follows short passable paths to an open column, including two head levels. @author NingMeng123 */
public final class R196OutdoorChecks {
    private R196OutdoorChecks() {}
    public static boolean openSky(ServerLevel level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return false;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        for (BlockPos.MutableBlockPos above = pos.mutable(); above.getY() < top; above.move(0, 1, 0))
            if (level.getBlockState(above).getLightBlock(level, above) > 0) return false;
        return true;
    }
    /** Foliage, tree branches and water shade the surface without turning it into an underground habitat. */
    public static boolean surface(ServerLevel level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return false;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        for (BlockPos.MutableBlockPos above = pos.mutable(); above.getY() < top; above.move(0, 1, 0)) {
            var block = level.getBlockState(above);
            if (block.is(BlockTags.LEAVES) || block.is(BlockTags.LOGS) || block.liquid()) continue;
            if (block.getLightBlock(level, above) > 0) return false;
        }
        return true;
    }
    public static boolean outdoors(ServerLevel level, BlockPos pos) {
        if (!level.dimensionType().hasSkyLight()) return false;
        if (passable(level, pos.above())) {
            if (atHeight(level, pos.above())) return true;
            if (passable(level, pos.above(2)) && atHeight(level, pos.above(2))) return true;
        }
        return atHeight(level, pos);
    }
    private static boolean atHeight(ServerLevel level, BlockPos origin) {
        if (passable(level, origin) && openSky(level, origin)) return true;
        boolean[][] reached = new boolean[7][7]; reached[3][3] = true;
        for (int distance = 1; distance <= 4; distance++) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) + Math.abs(z) != distance || distance > 3 && (Math.abs(x) != 2 || Math.abs(z) != 2)) continue;
            boolean reachable = x != 0 && reached[3 + x - Integer.signum(x)][3 + z]
                    || z != 0 && reached[3 + x][3 + z - Integer.signum(z)];
            BlockPos point = origin.offset(x, 0, z);
            if (!reachable || !passable(level, point)) continue;
            if (openSky(level, point)) return true;
            reached[x + 3][z + 3] = true;
        }
        return false;
    }
    private static boolean passable(ServerLevel level, BlockPos pos) {
        return pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || level.getFluidState(pos).isEmpty() && !level.getBlockState(pos).blocksMotion();
    }
}
