package io.github.ningmeng123.mite.entity;

import javax.annotation.Nullable;
import net.minecraft.world.level.pathfinder.PathType;

/** Opt-in raw classifications owned by one synchronous path search's terrain view. @author NingMeng123 */
public interface SearchLocalPathTypeCache {
    @Nullable PathType cachedRawPathType(long position);

    void cacheRawPathType(long position, PathType type);
}
