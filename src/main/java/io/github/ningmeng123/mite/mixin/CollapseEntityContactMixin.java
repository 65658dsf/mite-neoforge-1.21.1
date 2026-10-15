package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.world.CollapsePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** R196 walking calls onUnderminedByPlayer with no player restriction, including creative entities. @author NingMeng123 */
@Mixin(Block.class)
public abstract class CollapseEntityContactMixin {
    @Inject(method = "stepOn", at = @At("HEAD"))
    private void mite$walkOnUnderminedBlock(Level level, BlockPos pos, BlockState state, Entity entity, CallbackInfo ci) {
        if (level instanceof ServerLevel server && CollapsePhysics.managed(state))
            CollapsePhysics.undermine(server, pos, null);
    }
}
