package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.LivestockGrass032;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Register on the method-owning SnowyDirtBlock and scope to grass; other snowy soils retain their state space. @author NingMeng123 */
@Mixin(SnowyDirtBlock.class)
public abstract class Livestock032GrassMixin {
    @Inject(method="createBlockStateDefinition",at=@At("TAIL"))
    private void mite$grassTramplingState(StateDefinition.Builder<Block,BlockState> builder,CallbackInfo ci) {
        if((Object)this instanceof GrassBlock) builder.add(LivestockGrass032.TRAMPLINGS);
    }
}
