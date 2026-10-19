package io.github.ningmeng123.mite.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Source predicates and explicit initialization of naturally generated underworld dungeons. @author NingMeng123 */
public interface R196SpawnerAccess {
    boolean miteUsesSourceRules();
    void mite$configureUnderworldDungeon(ServerLevel level, BlockPos pos);
}
