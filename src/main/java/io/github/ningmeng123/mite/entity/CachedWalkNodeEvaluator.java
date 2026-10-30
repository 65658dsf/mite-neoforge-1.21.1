package io.github.ningmeng123.mite.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Reuses complete native block/neighbor classifications within one synchronous search. @author NingMeng123 */
class CachedWalkNodeEvaluator extends WalkNodeEvaluator {
    private final Long2ObjectOpenHashMap<PathType> blockTypes = new Long2ObjectOpenHashMap<>();

    @Override public void prepare(PathNavigationRegion region, Mob mob) {
        blockTypes.clear();
        super.prepare(region, mob);
    }

    @Override public void done() {
        try {
            super.done();
        } finally {
            blockTypes.clear();
        }
    }

    @Override public void setCanOpenDoors(boolean value) {
        if (value != canOpenDoors()) blockTypes.clear();
        super.setCanOpenDoors(value);
    }

    @Override public void setCanPassDoors(boolean value) {
        if (value != canPassDoors()) blockTypes.clear();
        super.setCanPassDoors(value);
    }

    @Override public void setCanFloat(boolean value) {
        if (value != canFloat()) blockTypes.clear();
        super.setCanFloat(value);
    }

    @Override public void setCanWalkOverFences(boolean value) {
        if (value != canWalkOverFences()) blockTypes.clear();
        super.setCanWalkOverFences(value);
    }

    @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        // Other callers can supply a different context; its terrain must never share this cache.
        if (context != currentContext) return super.getPathType(context, x, y, z);
        long position = BlockPos.asLong(x, y, z);
        PathType type = blockTypes.get(position);
        if (type == null) {
            // Keep the native and NeoForge block/fluid hooks, including adjacent hazards.
            // Mixin results can depend on navigation flags, so their setters invalidate this map.
            // Mob-volume aggregation and native door/rail handling still run in the caller.
            type = super.getPathType(context, x, y, z);
            blockTypes.put(position, type);
        }
        return type;
    }
}
