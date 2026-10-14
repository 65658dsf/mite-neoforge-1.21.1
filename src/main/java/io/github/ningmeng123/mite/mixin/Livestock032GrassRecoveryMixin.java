package io.github.ningmeng123.mite.mixin;

import io.github.ningmeng123.mite.entity.animal.LivestockGrass032;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** aon pasture recovery hooks the modern method owner without replacing terrain ecology. @author NingMeng123 */
@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class Livestock032GrassRecoveryMixin {
    @Inject(method="randomTick",at=@At("TAIL"))
    private void mite$grassRecovery(BlockState state,ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci) {
        if((Object)this instanceof GrassBlock) LivestockGrass032.recover(level,pos,random);
    }
}
