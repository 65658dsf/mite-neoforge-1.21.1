package io.github.ningmeng123.mite.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit R196 support-removal causes; ordinary world edits do not trigger undermining. @author NingMeng123 */
public final class CollapseSecondaryTriggers {
    private CollapseSecondaryTriggers() {}

    /** aqz.onBlockDestroyedByExplosion schedules only the directly overlying dirt or soul sand. */
    public static void exploded(Level level, BlockPos removed, BlockState previous, Explosion explosion) {
        if (!(level instanceof ServerLevel server)
                || (explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY
                && explosion.getBlockInteraction() != Explosion.BlockInteraction.DESTROY_WITH_DECAY)
                || previous.isAir() || server.getBlockState(removed).is(previous.getBlock())) return;
        CollapsePhysics.schedule(server, removed.above());
    }

    /** Leaves, endermen, released silverfish and digging monsters call tryToFall directly. */
    public static void removedSupport(Level level, BlockPos removed) {
        if (level instanceof ServerLevel server) CollapsePhysics.tryFall(server, removed.above());
    }
}
