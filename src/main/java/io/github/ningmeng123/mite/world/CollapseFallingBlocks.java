package io.github.ningmeng123.mite.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source collision rules and requested impact damage for disturbed soil. @author NingMeng123 */
public final class CollapseFallingBlocks {
    public static final float IMPACT_DAMAGE_PER_BLOCK = 1F;
    public static final int MAX_IMPACT_DAMAGE = 20;
    private CollapseFallingBlocks() {}

    /** Native falling damage keeps its height accumulation, protections and damage events. */
    public static void configureImpact(FallingBlockEntity falling) {
        if (CollapsePhysics.managed(falling.getBlockState()))
            falling.setHurtsEntities(IMPACT_DAMAGE_PER_BLOCK, MAX_IMPACT_DAMAGE);
    }

    /**
     * Run after native movement, before the native landing decision. Native gravity, velocity,
     * world-height bounds, save data, trial provenance and native fall-damage dispatch remain intact.
     */
    public static void afterMove(FallingBlockEntity falling) {
        if (!(falling.level() instanceof ServerLevel level) || falling.isRemoved()
                || !CollapsePhysics.managed(falling.getBlockState())) return;
        BlockState carried = falling.getBlockState();
        BlockPos occupied = falling.blockPosition();
        dislodge(level, occupied, carried, falling);
        if (falling.onGround()) {
            BlockPos support = occupied.below();
            if (level.hasChunkAt(support) && CollapsePhysics.glass(level.getBlockState(support))
                    && destroy(level, support, true, falling)) {
                // Entity.move has already resolved this tick's vertical collision. Removing its
                // glass support must not let the following native landing branch settle here.
                falling.setOnGround(false);
            }
        }
    }

    private static void dislodge(ServerLevel level, BlockPos pos, BlockState carried, FallingBlockEntity falling) {
        if (!level.hasChunkAt(pos)) return;
        BlockState occupying = level.getBlockState(pos);
        if (occupying.isAir() || !occupying.getFluidState().isEmpty()
                || !CollapsePhysics.canDislodgeOrCrush(level, pos, carried)) return;
        // sr distinguishes a collision above open space from crushing against a solid floor.
        // A crushed block is removed without calling its ordinary loot table at all.
        boolean drops = CollapsePhysics.canFallInto(level, pos.below(), carried);
        destroy(level, pos, drops, falling);
    }

    private static boolean destroy(ServerLevel level, BlockPos pos, boolean drops, FallingBlockEntity falling) {
        BlockState obstacle = level.getBlockState(pos);
        if (obstacle.isAir() || !obstacle.canEntityDestroy(level, pos, falling)) return false;
        // The normal destruction transaction carries BlockDropsEvent and the trial chamber
        // no-drop context. A falling entity is not a LivingEntity: do not fabricate its event.
        return level.destroyBlock(pos, drops, falling);
    }

    public static boolean canLandReplace(FallingBlockEntity falling, BlockPos pos, BlockState occupying,
            boolean nativeReplaceable) {
        if (!(falling.level() instanceof ServerLevel level) || !CollapsePhysics.managed(falling.getBlockState())
                || occupying.isAir() || !occupying.getFluidState().isEmpty()) return nativeReplaceable;
        // A block veto cannot be bypassed by the subsequent native replaceable-state check.
        return nativeReplaceable && CollapsePhysics.canDislodgeOrCrush(level, pos, falling.getBlockState())
                && occupying.canEntityDestroy(level, pos, falling);
    }

    public static boolean canContinueThroughSupport(FallingBlockEntity falling, boolean nativeFree) {
        if (!(falling.level() instanceof ServerLevel level) || !CollapsePhysics.managed(falling.getBlockState()))
            return nativeFree;
        return CollapsePhysics.canFallInto(level, falling.blockPosition().below(), falling.getBlockState());
    }
}
