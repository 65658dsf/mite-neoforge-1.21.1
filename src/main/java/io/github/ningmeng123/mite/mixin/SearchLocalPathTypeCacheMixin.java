package io.github.ningmeng123.mite.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.ningmeng123.mite.entity.SearchLocalPathTypeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import org.spongepowered.asm.mixin.Mixin;

/** Prevents native direct-map collisions from reclassifying one search's same terrain. @author NingMeng123 */
@Mixin(PathTypeCache.class)
public abstract class SearchLocalPathTypeCacheMixin {
    @WrapMethod(method = "getOrCompute")
    private PathType mite$reuseSearchClassification(BlockGetter level, BlockPos position, Operation<PathType> original) {
        if (!(level instanceof SearchLocalPathTypeCache cache)) return original.call(level, position);
        // PathfindingContext updates its mutable evaluation position before this method is entered.
        // Keep that native call intact so adjacent block/fluid hooks always see the current position.
        long key = position.asLong();
        PathType type = cache.cachedRawPathType(key);
        if (type == null) {
            type = original.call(level, position);
            cache.cacheRawPathType(key, type);
        }
        return type;
    }
}
