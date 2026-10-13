package io.github.ningmeng123.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both native inspection and mining use the retained cobblestone furnace's source hardness. @author NingMeng123 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FurnaceHardnessMixin {
    @Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
    private void mite$hardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (((BlockState)(Object)this).is(Blocks.FURNACE)) cir.setReturnValue(2F);
    }
}
