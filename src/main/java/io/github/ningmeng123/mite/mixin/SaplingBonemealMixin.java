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
    /** Two sapling stages average 154 random ticks, close to seven crop stages at 1/21 (147). */
    @Inject(method = "advanceTree", at = @At("HEAD"), cancellable = true)
    private void mite$cropPacedTrees(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, CallbackInfo callback) {
        if (random.nextInt(11) != 0) callback.cancel();
    }

    @Inject(method = "isValidBonemealTarget", at = @At("HEAD"), cancellable = true)
    private void mite$noBonemealTarget(LevelReader level, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }

    @Inject(method = "performBonemeal", at = @At("HEAD"), cancellable = true)
    private void mite$noBonemealGrowth(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, CallbackInfo callback) {
        callback.cancel();
    }
}
