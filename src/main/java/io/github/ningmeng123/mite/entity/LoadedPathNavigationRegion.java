package io.github.ningmeng123.mite.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import java.util.function.Predicate;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;

/** One native search's loaded-chunk and block-state snapshot. @author NingMeng123 */
final class LoadedPathNavigationRegion extends PathNavigationRegion implements SearchLocalPathTypeCache {
    private final Level sourceLevel;
    private final double borderMinX, borderMinZ, borderMaxX, borderMaxZ;
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<PathType> rawPathTypes = new Long2ObjectOpenHashMap<>();
    private final Long2BooleanOpenHashMap cautiousProbes = new Long2BooleanOpenHashMap();
    private Predicate<BlockPos> cautiousValidator;
    private WeakReference<PathfinderMob> obstacleOwner;
    private PursuitEntityObstacles.Snapshot entityObstacles;

    LoadedPathNavigationRegion(Level level, BlockPos minimum, BlockPos maximum) {
        // The native constructor populates chunks exclusively with getChunkNow; it never loads terrain.
        super(level, minimum, maximum);
        sourceLevel = level;
        var border = level.getWorldBorder();
        borderMinX = border.getMinX(); borderMinZ = border.getMinZ();
        borderMaxX = border.getMaxX(); borderMaxZ = border.getMaxZ();
    }

    @Override public BlockState getBlockState(BlockPos position) {
        if (isOutsideBuildHeight(position)) return Blocks.BEDROCK.defaultBlockState();
        int x = SectionPos.blockToSectionCoord(position.getX()) - centerX;
        int z = SectionPos.blockToSectionCoord(position.getZ()) - centerZ;
        if (x < 0 || x >= chunks.length || z < 0 || z >= chunks[x].length)
            return Blocks.BEDROCK.defaultBlockState();
        ChunkAccess chunk = chunks[x][z];
        if (chunk == null) return Blocks.BEDROCK.defaultBlockState();
        long key = position.asLong();
        BlockState state = states.get(key);
        if (state == null) {
            state = chunk.getBlockState(position);
            states.put(key, state);
        }
        return state;
    }

    @Override public FluidState getFluidState(BlockPos position) {
        return getBlockState(position).getFluidState();
    }

    @Override public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
        int x = chunkX - centerX, z = chunkZ - centerZ;
        // Native collision probes otherwise read ChunkAccess directly and bypass the queried
        // states needed to validate a delayed failure. Missing-terrain semantics stay native.
        return x >= 0 && x < chunks.length && z >= 0 && z < chunks[x].length && chunks[x][z] != null
                ? this : super.getChunkForCollisions(chunkX, chunkZ);
    }

    @Override public PathType cachedRawPathType(long position) {
        return rawPathTypes.get(position);
    }

    @Override public void cacheRawPathType(long position, PathType type) {
        rawPathTypes.put(position, type);
    }

    void cautiousValidator(Predicate<BlockPos> validator) { cautiousValidator = validator; }
    void recordCautious(BlockPos position, boolean fits) { cautiousProbes.put(position.asLong(), fits); }

    void entityObstacles(PathfinderMob mob, PursuitEntityObstacles.Snapshot snapshot) {
        obstacleOwner = new WeakReference<>(mob);
        entityObstacles = snapshot;
    }

    /** Delayed failures are reusable only while all queried terrain and captured chunks remain current. */
    boolean terrainUnchanged() {
        if (entityObstacles != null) {
            PathfinderMob owner = obstacleOwner.get();
            if (owner == null || !entityObstacles.unchanged(owner)) return false;
        }
        var border = sourceLevel.getWorldBorder();
        if (borderMinX != border.getMinX() || borderMinZ != border.getMinZ()
                || borderMaxX != border.getMaxX() || borderMaxZ != border.getMaxZ()) return false;
        for (int x = 0; x < chunks.length; ++x) for (int z = 0; z < chunks[x].length; ++z)
            if (sourceLevel.getChunkSource().getChunkNow(centerX + x, centerZ + z) != chunks[x][z]) return false;
        for (var entry : states.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            int x = SectionPos.blockToSectionCoord(pos.getX()) - centerX;
            int z = SectionPos.blockToSectionCoord(pos.getZ()) - centerZ;
            ChunkAccess chunk = chunks[x][z];
            // Block entities may change their collision behavior without replacing BlockState.
            // Such a delayed result takes the native synchronous compatibility path.
            if (entry.getValue().hasBlockEntity() || chunk == null || chunk.getBlockState(pos) != entry.getValue()) return false;
        }
        if (cautiousValidator != null) for (var entry : cautiousProbes.long2BooleanEntrySet())
            if (cautiousValidator.test(BlockPos.of(entry.getLongKey())) != entry.getBooleanValue()) return false;
        return true;
    }
}
