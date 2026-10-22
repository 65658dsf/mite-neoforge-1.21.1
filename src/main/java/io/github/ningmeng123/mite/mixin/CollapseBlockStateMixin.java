package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.CollapsePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep collapse scheduling independent of native block overrides and block-state properties. @author NingMeng123 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class CollapseBlockStateMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void mite$pendingCollapse(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockState state = (BlockState)(Object)this;
        if (!CollapsePhysics.managed(state) && !state.is(Blocks.GRASS_BLOCK)) return;
        // A custom collapse check must not run a displaced future bubble-column tick early.
        if (CollapsePhysics.tick(level, pos, state)) ci.cancel();
    }

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void mite$clearRemovedCollapse(Level level, BlockPos pos, BlockState replacement, boolean moved, CallbackInfo ci) {
        CollapsePhysics.removed(level, pos, (BlockState)(Object)this, replacement);
    }

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void mite$contactUnderminedBlock(Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel server && CollapsePhysics.managed((BlockState)(Object)this))
            CollapsePhysics.undermine(server, pos, null);
    }
}
