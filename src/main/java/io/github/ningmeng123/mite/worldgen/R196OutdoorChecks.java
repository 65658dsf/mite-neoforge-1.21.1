package io.github.ningmeng123.mite.worldgen;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Source abw.isOutdoors follows short passable paths to an open column, including two head levels. @author NingMeng123 */
public final class R196OutdoorChecks {
    private static final int SURFACE_CAVE_PATH = 12;
    private static final int SURFACE_CAVE_VERTICAL_RANGE = 3;
    private static final Direction[] SURFACE_CAVE_DIRECTIONS = Direction.values();
    private R196OutdoorChecks() {}
    public static boolean openSky(ServerLevel level, BlockPos pos) {
        return openSkyColumn(level, pos);
    }
    private static boolean openSkyColumn(LevelReader level, BlockPos pos) {
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
    /**
     * Daylight also protects shallow cave mouths reached through a short air path.
     * This is independent of local light and absolute altitude; walls and long tunnels
     * keep underground habitats separate. All reads stay within already loaded chunks.
     */
    public static boolean daytimeSurfaceHabitat(ServerLevel level, BlockPos origin) {
        if (!level.dimensionType().hasSkyLight() || !level.hasChunkAt(origin)
                || level.isOutsideBuildHeight(origin)) return false;
        int lowestY = Math.max(level.getMinBuildHeight(), origin.getY() - SURFACE_CAVE_VERTICAL_RANGE);
        // Cache only this search: construction and excavation must take effect immediately.
        Map<Long, Integer> opaqueColumns = new HashMap<>();
        if (surfaceColumn(level, origin, lowestY, opaqueColumns)) return true;
        if (!cavePassable(level, origin)) return false;
        record Step(BlockPos pos, int distance) {}
        ArrayDeque<Step> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        pending.add(new Step(origin.immutable(), 0));
        visited.add(origin.asLong());
        while (!pending.isEmpty()) {
            Step step = pending.removeFirst();
            if (step.distance() >= SURFACE_CAVE_PATH) continue;
            for (Direction direction : SURFACE_CAVE_DIRECTIONS) {
                BlockPos next = step.pos().relative(direction);
                if (Math.abs(next.getY() - origin.getY()) > SURFACE_CAVE_VERTICAL_RANGE
                        || level.isOutsideBuildHeight(next) || !visited.add(next.asLong())
                        || !level.hasChunkAt(next) || !cavePassable(level, next)) continue;
                if (surfaceColumn(level, next, lowestY, opaqueColumns)) return true;
                pending.addLast(new Step(next, step.distance() + 1));
            }
        }
        return false;
    }
    private static boolean surfaceColumn(ServerLevel level, BlockPos pos, int lowestY, Map<Long, Integer> opaqueColumns) {
        long column = (long)pos.getX() << 32 | pos.getZ() & 0xffffffffL;
        int opaqueY = opaqueColumns.computeIfAbsent(column, ignored -> {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos(pos.getX(), top - 1, pos.getZ());
            for (; above.getY() >= lowestY; above.move(0, -1, 0)) {
                var state = level.getBlockState(above);
                if (!state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.liquid()
                        && state.getLightBlock(level, above) > 0) return above.getY();
            }
            return Integer.MIN_VALUE;
        });
        return pos.getY() > opaqueY;
    }
    private static boolean cavePassable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.blocksMotion();
    }
    public static boolean outdoors(ServerLevel level, BlockPos pos) {
        return outdoors(level, pos, null);
    }
    public static boolean outdoorsDuringGeneration(WorldGenLevel level, BlockPos pos) {
        return outdoors(level, pos, level instanceof WorldGenRegion region ? region.getCenter() : null);
    }
    private static boolean outdoors(LevelReader level, BlockPos pos, ChunkPos generationCenter) {
        if (!level.dimensionType().hasSkyLight()) return false;
        if (passable(level, pos.above())) {
            if (atHeight(level, pos.above(), generationCenter)) return true;
            if (passable(level, pos.above(2)) && atHeight(level, pos.above(2), generationCenter)) return true;
        }
        return atHeight(level, pos, generationCenter);
    }
    private static boolean atHeight(LevelReader level, BlockPos origin, ChunkPos generationCenter) {
        if (passable(level, origin) && openSkyColumn(level, origin)) return true;
        boolean[][] reached = new boolean[7][7]; reached[3][3] = true;
        for (int distance = 1; distance <= 4; distance++) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) + Math.abs(z) != distance || distance > 3 && (Math.abs(x) != 2 || Math.abs(z) != 2)) continue;
            boolean reachable = x != 0 && reached[3 + x - Integer.signum(x)][3 + z]
                    || z != 0 && reached[3 + x][3 + z - Integer.signum(z)];
            if (!reachable) continue;
            BlockPos point = origin.offset(x, 0, z);
            // At FEATURES only the center and its immediate neighbors are guaranteed to be carved.
            if (generationCenter != null && generationCenter.getChessboardDistance(
                    SectionPos.blockToSectionCoord(point.getX()), SectionPos.blockToSectionCoord(point.getZ())) > 1) return true;
            if (!passable(level, point)) continue;
            if (openSkyColumn(level, point)) return true;
            reached[x + 3][z + 3] = true;
        }
        return false;
    }
    private static boolean passable(LevelReader level, BlockPos pos) {
        return pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || level.getFluidState(pos).isEmpty() && !level.getBlockState(pos).blocksMotion();
    }
}
