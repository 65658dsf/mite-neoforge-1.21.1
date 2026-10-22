package io.github.ningmeng123.mite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SaplingBlock.class)
public abstract class SaplingBonemealMixin {
    @Inject(method = "isValidBonemealTarget", at = @At("HEAD"), cancellable = true)
    private void mite$noBonemealTarget(LevelReader level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }

    @Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
    private void mite$noBonemealGrowth(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, CallbackInfo callback) {
        callback.cancel();
    }
}
